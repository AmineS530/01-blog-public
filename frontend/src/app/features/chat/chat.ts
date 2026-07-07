import {
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  AfterViewChecked,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { ActivatedRoute, Router } from '@angular/router';
import {
  MessageService,
  MessageResponse,
  MessageRequest,
} from '../../core/services/message.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfileService } from '../../core/services/profile.service';
import { MediaService } from '../../core/services/media.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { RealtimeService } from '../../core/services/realtime.service';
import { ProfileResponse } from '../../shared/models/profile.models';
import { Subscription } from 'rxjs';

interface ChatPartner {
  publicId: string;
  username: string;
  displayName?: string;
  avatarUrl: string | null;
  lastMessageSnippet: string;
  lastMessageTime: string;
  unreadCount: number;
}

/**
 * Reactive Chat Interface Controller.
 * <p>
 * This component coordinates the real-time direct messaging UI. It integrates historical message threads,
 * active user inbox list parsing, recommended partner profiles, real-time message arrival via WebSockets,
 * physical media file attachments, and programmatic DOM view-scrolling synchronization.
 * </p>
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatInputModule,
    MatFormFieldModule,
  ],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewChecked {
  /** Reference to the chat scrollable area element in the DOM. */
  @ViewChild('scrollContainer') private scrollContainer?: ElementRef;

  /** The public ID of the logged-in user. */
  currentUserId = '';

  /** The username of the logged-in user. */
  currentUsername = '';

  /** Sorted list of conversation threads (inbox partners). */
  inbox: ChatPartner[] = [];

  /** List of suggested profiles to start new conversations. */
  recommended: ProfileResponse[] = [];

  /** The currently selected partner thread. */
  activePartner: ChatPartner | null = null;

  /** Historical message list for the active conversation thread. */
  activeMessages: MessageResponse[] = [];

  loadingInbox = true;
  loadingThread = false;
  loadingRecommended = false;

  /** Set of currently online user public IDs. */
  onlineUsers = new Set<string>();

  /** Search/Filter query string to filter inbox and recommendations. */
  searchQuery = '';
  searchResults: ProfileResponse[] = [];
  isSearching = false;

  /** Outbound raw text message content. */
  messageContent = '';

  /** Uploaded file media URL attachment. */
  attachedMediaUrl: string | null = null;

  uploadingMedia = false;
  sendingMessage = false;

  /** Unsubscribe reference tracking WebSocket real-time messages to prevent leaks. */
  private messageSubscription?: Subscription;

  /** Unsubscribe reference tracking WebSocket online status updates. */
  private onlineSubscription?: Subscription;

  /** Internal dirty flag triggered to request programmatic scrolling on next view update. */
  private shouldScrollToBottom = false;

  constructor(
    private messageService: MessageService,
    private authService: AuthService,
    private profileService: ProfileService,
    private mediaService: MediaService,
    private realtimeService: RealtimeService,
    private feedback: FeedbackService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  /**
   * Initializes component state:
   * 1. Resolves logged-in user credentials and redirects to login if unauthenticated.
   * 2. Triggers parallel REST API retrievals for the inbox and recommendation lists.
   * 3. Attaches real-time message subscription pipelines.
   * 4. Parses route parameters to support starting a chat directly from a profile detail page link.
   */
  ngOnInit(): void {
    this.currentUserId = this.authService.getPublicId() ?? '';
    this.currentUsername = this.authService.getUsername() ?? '';

    if (!this.currentUserId) {
      this.router.navigate(['/login']);
      return;
    }

    this.loadInbox();
    this.loadRecommended();
    this.loadOnlineUsers();
    this.setupRealtime();

    // Support starting a chat directly via query params (e.g. /chat?user=publicId&username=username)
    this.route.queryParams.subscribe((params) => {
      const targetUser = params['user'];
      const targetUsername = params['username'];
      const targetAvatar = params['avatar'] || null;

      if (targetUser && targetUsername) {
        this.startConversationWith({
          publicId: targetUser,
          username: targetUsername,
          avatarUrl: targetAvatar,
          lastMessageSnippet: '',
          lastMessageTime: new Date().toISOString(),
          unreadCount: 0,
        });
      }
    });
  }

  /**
   * Core Angular lifecycle check hook.
   * <p>
   * Triggered after every change detection cycle. Programmatic scroll alignments are defer-executed here
   * because only at this stage has Angular fully mapped newly appended messages into the DOM, giving us
   * accurate scrollHeight values to snap the scrollbar to the absolute bottom.
   * </p>
   */
  ngAfterViewChecked(): void {
    if (this.shouldScrollToBottom) {
      this.scrollToBottom();
      this.shouldScrollToBottom = false;
    }
  }

  /**
   * Fetches the user's active direct message history inbox via the REST API.
   */
  loadInbox(): void {
    this.loadingInbox = true;
    this.messageService.getInbox().subscribe({
      next: (messages) => {
        this.processInbox(messages);
        this.loadingInbox = false;
      },
      error: () => {
        this.feedback.showToast('Failed to load inbox.', 'error');
        this.loadingInbox = false;
      },
    });
  }

  /**
   * Fetches the initial list of online user public IDs.
   */
  loadOnlineUsers(): void {
    this.messageService.getOnlineUsers().subscribe({
      next: (users) => {
        this.onlineUsers = new Set(users);
      },
      error: () => {},
    });
  }

  /**
   * Fetches a list of recommended user profiles to encourage starting new message interactions.
   * Filters out the current user's profile to prevent self-chat suggestions.
   */
  loadRecommended(): void {
    this.loadingRecommended = true;
    this.profileService.getRecommendedProfiles().subscribe({
      next: (profiles) => {
        this.recommended = profiles.filter((p) => p.username !== this.currentUsername);
        this.loadingRecommended = false;
      },
      error: () => {
        this.loadingRecommended = false;
      },
    });
  }

  /**
   * Establishes a reactive listener binding to the real-time WebSocket incoming messages stream.
   * <p>
   * This is a core real-time sync handler. Upon receiving a message event:
   * 1. It determines who the partner is (the other party in the conversation).
   * 2. If the message belongs to the currently active opened thread, it appends it to the activeMessages list
   *    and triggers a scroll request. It also triggers an immediate backpressure REST call to mark the thread as read.
   * 3. It dynamically updates the inbox list, updating snippets and unread badges, and shifting the partner
   *    to the top of the conversation list to simulate modern direct messaging flows.
   * </p>
   */
  setupRealtime(): void {
    this.messageSubscription = this.realtimeService.messages$.subscribe({
      next: (msg: MessageResponse) => {
        // Determine the partner in this message
        const messagePartnerId =
          msg.senderPublicId === this.currentUserId ? msg.recipientPublicId : msg.senderPublicId;
        const messagePartnerUsername =
          msg.senderPublicId === this.currentUserId ? msg.recipientUsername : msg.senderUsername;
        const messagePartnerDisplayName =
          msg.senderPublicId === this.currentUserId
            ? msg.recipientDisplayName || msg.recipientUsername
            : msg.senderDisplayName || msg.senderUsername;
        const messagePartnerAvatar =
          msg.senderPublicId === this.currentUserId ? msg.recipientAvatarUrl : msg.senderAvatarUrl;

        // If it belongs to our active conversation
        if (this.activePartner && this.activePartner.publicId === messagePartnerId) {
          if (!this.activeMessages.some((m) => m.id === msg.id)) {
            this.activeMessages.push(msg);
            this.shouldScrollToBottom = true;
          }

          // If we are recipient, mark it as read on the backend
          if (msg.recipientPublicId === this.currentUserId) {
            this.messageService.markConversationAsRead(messagePartnerId).subscribe();
          }
        }

        // Update the conversation list (inbox)
        const existingIdx = this.inbox.findIndex((c) => c.publicId === messagePartnerId);
        const snippet = msg.mediaUrl ? '📷 Image/Video' : msg.content;

        if (existingIdx !== -1) {
          const partner = this.inbox[existingIdx];
          partner.lastMessageSnippet = snippet;
          partner.lastMessageTime = msg.createdAt;
          partner.displayName = messagePartnerDisplayName || partner.displayName;
          if (
            msg.recipientPublicId === this.currentUserId &&
            (!this.activePartner || this.activePartner.publicId !== messagePartnerId)
          ) {
            partner.unreadCount++;
          }
          // Move to top of inbox
          this.inbox.splice(existingIdx, 1);
          this.inbox.unshift(partner);
        } else {
          // New conversation
          const partner: ChatPartner = {
            publicId: messagePartnerId,
            username: messagePartnerUsername,
            displayName: messagePartnerDisplayName || messagePartnerUsername,
            avatarUrl: messagePartnerAvatar || null,
            lastMessageSnippet: snippet,
            lastMessageTime: msg.createdAt,
            unreadCount: msg.recipientPublicId === this.currentUserId ? 1 : 0,
          };
          this.inbox.unshift(partner);
        }
      },
    });

    this.onlineSubscription = this.realtimeService.onlineStatus$.subscribe({
      next: (status) => {
        if (status.online) {
          this.onlineUsers.add(status.publicId);
        } else {
          this.onlineUsers.delete(status.publicId);
        }
      },
    });
  }

  processInbox(messages: MessageResponse[]): void {
    const partnersMap = new Map<string, ChatPartner>();

    for (const m of messages) {
      const partnerId =
        m.senderPublicId === this.currentUserId ? m.recipientPublicId : m.senderPublicId;
      const partnerUsername =
        m.senderPublicId === this.currentUserId ? m.recipientUsername : m.senderUsername;
      const partnerDisplayName =
        m.senderPublicId === this.currentUserId
          ? m.recipientDisplayName || m.recipientUsername
          : m.senderDisplayName || m.senderUsername;
      const partnerAvatar =
        m.senderPublicId === this.currentUserId ? m.recipientAvatarUrl : m.senderAvatarUrl;

      const snippet = m.mediaUrl ? '📷 Image/Video' : m.content;

      if (!partnersMap.has(partnerId)) {
        partnersMap.set(partnerId, {
          publicId: partnerId,
          username: partnerUsername,
          displayName: partnerDisplayName || partnerUsername,
          avatarUrl: partnerAvatar || null,
          lastMessageSnippet: snippet,
          lastMessageTime: m.createdAt,
          unreadCount: 0, // Calculated separately or incremented
        });
      }
    }

    // Sort inbox partners based on last message time
    this.inbox = Array.from(partnersMap.values()).sort(
      (a, b) => new Date(b.lastMessageTime).getTime() - new Date(a.lastMessageTime).getTime(),
    );

    // Load unread counts or verify unread statuses
    this.fetchUnreadCounts();
  }

  fetchUnreadCounts(): void {
    // Unread count is already managed or can be fetched per partner. Let's do simple matching.
    // For local resilience, we can query details or rely on server push unread status.
  }

  selectPartner(partner: ChatPartner): void {
    this.startConversationWith(partner);
  }

  startConversationWith(partner: ChatPartner): void {
    this.activePartner = partner;
    this.loadingThread = true;
    this.activeMessages = [];
    this.searchQuery = '';
    this.searchResults = [];

    // Reset unread count locally
    partner.unreadCount = 0;

    this.messageService.getConversation(partner.publicId).subscribe({
      next: (messages) => {
        // Sort ascending by creation time
        this.activeMessages = messages.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
        );
        this.loadingThread = false;
        this.shouldScrollToBottom = true;

        // Mark as read
        this.messageService.markConversationAsRead(partner.publicId).subscribe();
      },
      error: () => {
        this.feedback.showToast('Failed to load conversation history.', 'error');
        this.loadingThread = false;
      },
    });
  }

  onFileSelected(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;

    this.uploadingMedia = true;
    this.mediaService.upload(file).subscribe({
      next: (res) => {
        this.attachedMediaUrl = res.url;
        this.uploadingMedia = false;
        this.feedback.showToast('Attachment uploaded successfully!', 'success');
      },
      error: () => {
        this.feedback.showToast('Failed to upload attachment.', 'error');
        this.uploadingMedia = false;
      },
    });
  }

  removeAttachment(): void {
    this.attachedMediaUrl = null;
    this.feedback.showToast('Attachment removed.', 'info');
  }

  sendMessage(): void {
    if (
      (!this.messageContent.trim() && !this.attachedMediaUrl) ||
      !this.activePartner ||
      this.sendingMessage
    ) {
      return;
    }

    this.sendingMessage = true;
    const request: MessageRequest = {
      recipientPublicId: this.activePartner.publicId,
      content: this.messageContent.trim(),
      mediaUrl: this.attachedMediaUrl,
    };

    this.messageService.sendMessage(request).subscribe({
      next: (msg) => {
        // Locally add and clear
        this.messageContent = '';
        this.attachedMediaUrl = null;
        this.sendingMessage = false;

        // Check if message is already in thread (might be pushed via websocket already)
        if (!this.activeMessages.some((m) => m.id === msg.id)) {
          this.activeMessages.push(msg);
          this.shouldScrollToBottom = true;
        }

        // Update inbox snippet
        const snippet = msg.mediaUrl ? '📷 Image/Video' : msg.content;
        const partner = this.inbox.find((p) => p.publicId === this.activePartner!.publicId);
        if (partner) {
          partner.lastMessageSnippet = snippet;
          partner.lastMessageTime = msg.createdAt;
          // Move to top of inbox
          this.inbox = [partner, ...this.inbox.filter((p) => p.publicId !== partner.publicId)];
        }
      },
      error: (err) => {
        const errorMsg = err.error?.message || 'Failed to send message.';
        this.feedback.showToast(errorMsg, 'error');
        this.sendingMessage = false;
      },
    });
  }

  get filteredInbox(): ChatPartner[] {
    if (!this.searchQuery.trim()) {
      return this.inbox;
    }
    const q = this.searchQuery.toLowerCase();
    return this.inbox.filter(
      (c) =>
        c.username.toLowerCase().includes(q) ||
        (c.displayName && c.displayName.toLowerCase().includes(q)),
    );
  }

  get filteredRecommended(): ProfileResponse[] {
    if (!this.searchQuery.trim()) {
      return this.recommended;
    }
    const q = this.searchQuery.toLowerCase();
    return this.recommended.filter(
      (p) =>
        p.username.toLowerCase().includes(q) ||
        (p.displayName && p.displayName.toLowerCase().includes(q)),
    );
  }

  onSearchChange(): void {
    const query = this.searchQuery.trim();
    if (!query) {
      this.searchResults = [];
      this.isSearching = false;
      return;
    }

    this.isSearching = true;
    this.profileService.searchProfiles(query).subscribe({
      next: (results) => {
        this.searchResults = results.filter((p) => p.username !== this.currentUsername);
        this.isSearching = false;
      },
      error: () => {
        this.isSearching = false;
      },
    });
  }

  private scrollToBottom(): void {
    if (this.scrollContainer) {
      try {
        const el = this.scrollContainer.nativeElement;
        el.scrollTop = el.scrollHeight;
      } catch (err) {
        // Fail silently
      }
    }
  }

  isImage(url: string | null | undefined): boolean {
    if (!url) return false;
    const lowercase = url.toLowerCase();
    return lowercase.includes('.jpg') || 
           lowercase.includes('.jpeg') || 
           lowercase.includes('.png') || 
           lowercase.includes('.gif') || 
           lowercase.includes('.webp') ||
           lowercase.startsWith('data:image/');
  }

  isVideo(url: string | null | undefined): boolean {
    if (!url) return false;
    const lowercase = url.toLowerCase();
    return lowercase.includes('.mp4') || 
           lowercase.includes('.webm') || 
           lowercase.includes('.ogg') ||
           lowercase.startsWith('data:video/');
  }

  goToProfile(username: string): void {
    this.feedback.showMiniProfile(username, this.profileService);
  }

  ngOnDestroy(): void {
    this.messageSubscription?.unsubscribe();
    this.onlineSubscription?.unsubscribe();
  }
}
