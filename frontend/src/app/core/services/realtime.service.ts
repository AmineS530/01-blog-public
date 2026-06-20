import { Injectable, OnDestroy, NgZone } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Core frontend client for real-time WebSocket communication.
 * <p>
 * This service manages the state of two active connections to the Spring Boot backend:
 * 1. General socket: For public feed updates and likes (/ws).
 * 2. Chat socket: For private messages and user online status changes (/ws/chat).
 * </p>
 */
@Injectable({
  providedIn: 'root',
})
export class RealtimeService implements OnDestroy {
  private socket: WebSocket | null = null;
  private chatSocket: WebSocket | null = null;

  private reconnectInterval = 3000;
  private chatReconnectInterval = 3000;

  private shouldReconnect = true;
  private isConnected = false;
  private isChatConnected = false;

  // RxJS Subjects acting as event gateways for internal app components.
  private postsSubject = new Subject<any>();
  private commentsSubject = new Subject<any>();
  private likesSubject = new Subject<any>();
  private messagesSubject = new Subject<any>();
  private notificationsSubject = new Subject<any>();
  private onlineStatusSubject = new Subject<{ publicId: string; online: boolean }>();

  public posts$: Observable<any> = this.postsSubject.asObservable();
  public comments$: Observable<any> = this.commentsSubject.asObservable(); // Maintained for backward compatibility (no-op now)
  public likes$: Observable<any> = this.likesSubject.asObservable();
  public messages$: Observable<any> = this.messagesSubject.asObservable();
  public notifications$: Observable<any> = this.notificationsSubject.asObservable();
  public onlineStatus$: Observable<{ publicId: string; online: boolean }> =
    this.onlineStatusSubject.asObservable();

  constructor(
    private authService: AuthService,
    private zone: NgZone,
  ) {
    this.authService.loggedIn$.subscribe((isLoggedIn) => {
      if (isLoggedIn) {
        this.connect();
        this.connectChat();
      } else {
        this.disconnect();
        this.disconnectChat();
      }
    });
  }

  /**
   * Establishes the general public WebSocket connection (/ws).
   */
  public connect(): void {
    if (
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    this.shouldReconnect = true;
    const token = this.authService.getToken();
    if (!token) return;
    const wsUrl = `ws://localhost:8080/ws?token=${token}`;

    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.isConnected = true;
        this.reconnectInterval = 3000;
      };

      this.socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const type = payload.type;
          const data = payload.data;

          this.zone.run(() => {
            switch (type) {
              case 'NEW_POST':
                this.postsSubject.next(data);
                break;
              case 'POST_LIKE':
              case 'COMMENT_LIKE':
                this.likesSubject.next({ type, ...data });
                break;
              case 'NOTIFICATION':
                this.notificationsSubject.next(data);
                break;
              default:
                break;
            }
          });
        } catch (e) {
          console.error('Error parsing general WebSocket message:', e);
        }
      };

      this.socket.onclose = () => {
        this.isConnected = false;

        if (this.shouldReconnect) {
          setTimeout(() => {
            this.reconnectInterval = Math.min(this.reconnectInterval * 1.5, 30000);
            this.connect();
          }, this.reconnectInterval);
        }
      };

      this.socket.onerror = (error) => {
        console.error('General WebSocket error:', error);
      };
    } catch (err) {
      console.error('Failed to establish general WebSocket:', err);
    }
  }

  /**
   * Establishes the chat-specific authenticated WebSocket connection (/ws/chat).
   */
  public connectChat(): void {
    if (
      this.chatSocket &&
      (this.chatSocket.readyState === WebSocket.OPEN ||
        this.chatSocket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) return;
    const wsUrl = `ws://localhost:8080/ws/chat?token=${token}`;

    try {
      this.chatSocket = new WebSocket(wsUrl);

      this.chatSocket.onopen = () => {
        this.isChatConnected = true;
        this.chatReconnectInterval = 3000;
        this.registerChatUser();
      };

      this.chatSocket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const type = payload.type;
          const data = payload.data;

          this.zone.run(() => {
            switch (type) {
              case 'NEW_MESSAGE':
                this.messagesSubject.next(data);
                break;
              case 'USER_ONLINE':
                this.onlineStatusSubject.next({ publicId: data.publicId, online: true });
                break;
              case 'USER_OFFLINE':
                this.onlineStatusSubject.next({ publicId: data.publicId, online: false });
                break;
              default:
                break;
            }
          });
        } catch (e) {
          console.error('Error parsing chat WebSocket message:', e);
        }
      };

      this.chatSocket.onclose = () => {
        this.isChatConnected = false;

        if (this.shouldReconnect && this.authService.isLoggedIn()) {
          setTimeout(() => {
            this.chatReconnectInterval = Math.min(this.chatReconnectInterval * 1.5, 30000);
            this.connectChat();
          }, this.chatReconnectInterval);
        }
      };

      this.chatSocket.onerror = (error) => {
        console.error('Chat WebSocket error:', error);
      };
    } catch (err) {
      console.error('Failed to establish chat WebSocket:', err);
    }
  }

  /**
   * Registers the active user to the chat session channel pool.
   */
  public registerChatUser(): void {
    if (!this.chatSocket || this.chatSocket.readyState !== WebSocket.OPEN) {
      return;
    }

    const publicId = this.authService.getPublicId();
    if (publicId) {
      this.chatSocket.send(
        JSON.stringify({
          type: 'REGISTER',
          publicId: publicId,
        }),
      );
    }
  }

  public disconnect(): void {
    this.shouldReconnect = false;
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  public disconnectChat(): void {
    if (this.chatSocket) {
      this.chatSocket.close();
      this.chatSocket = null;
    }
  }

  ngOnDestroy(): void {
    this.disconnect();
    this.disconnectChat();
  }
}