import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Core frontend client for real-time WebSocket communication.
 * <p>
 * This service manages the state of the active connection to the Spring Boot backend,
 * coordinates auto-reconnection with an exponential backoff strategy, and distributes
 * deserialized server frames to specific feature areas (posts, comments, likes, messages)
 * through high-performance reactive RxJS Observables.
 * </p>
 */
@Injectable({
  providedIn: 'root',
})
export class RealtimeService implements OnDestroy {
  /** The underlying browser WebSocket instance. */
  private socket: WebSocket | null = null;

  /** Current reconnection timeout in milliseconds. Starts at 3000ms and scales progressively. */
  private reconnectInterval = 3000;

  /** Toggle switch to enable or disable automatic retry loops upon connection loss. */
  private shouldReconnect = true;

  /** Reactive state flag representing connection status. */
  private isConnected = false;

  // RxJS Subjects acting as event gateways for internal app components.
  private postsSubject = new Subject<any>();
  private commentsSubject = new Subject<any>();
  private likesSubject = new Subject<any>();
  private messagesSubject = new Subject<any>();
  private onlineStatusSubject = new Subject<{ publicId: string; online: boolean }>();

  /** Observable stream emitting new posts published across the ecosystem. */
  public posts$: Observable<any> = this.postsSubject.asObservable();

  /** Observable stream emitting new comments added to any existing post. */
  public comments$: Observable<any> = this.commentsSubject.asObservable();

  /** Observable stream emitting post and comment like count updates. */
  public likes$: Observable<any> = this.likesSubject.asObservable();

  /** Observable stream emitting direct chat messages targeted to the current logged-in user. */
  public messages$: Observable<any> = this.messagesSubject.asObservable();

  /** Observable stream emitting user online status changes (USER_ONLINE / USER_OFFLINE). */
  public onlineStatus$: Observable<{ publicId: string; online: boolean }> =
    this.onlineStatusSubject.asObservable();

  constructor(private authService: AuthService) {
    this.authService.loggedIn$.subscribe((isLoggedIn) => {
      if (isLoggedIn) {
        this.connect();
        this.registerUser();
      } else {
        this.disconnect();
      }
    });
  }

  /**
   * Establishes a raw WebSocket connection with the Spring Boot server.
   * <p>
   * If a connection is already active or connecting, this method safely short-circuits.
   * Attaches callback hooks to handle the connection lifecycle:
   * 1. {@code onopen}: Resets the reconnect timer backoff and sends a user registration signal.
   * 2. {@code onmessage}: Decodes text payloads and routes them to their corresponding Subjects.
   * 3. {@code onclose}: Initiates an exponential backoff reconnect attempt if permitted.
   * 4. {@code onerror}: Logs failures for diagnostic trace capabilities.
   * </p>
   */
  public connect(): void {
    if (
      this.socket &&
      (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)
    ) {
      return;
    }

    this.shouldReconnect = true;
    const wsUrl = 'ws://localhost:8080/ws';

    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.isConnected = true;
        this.reconnectInterval = 3000; // Reset exponential backoff back to default on success
        this.registerUser();
      };

      this.socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const type = payload.type;
          const data = payload.data;

          // Route received payloads to their respective reactive subjects based on the event type header
          switch (type) {
            case 'NEW_POST':
              this.postsSubject.next(data);
              break;
            case 'NEW_COMMENT':
              this.commentsSubject.next(data);
              break;
            case 'POST_LIKE':
            case 'COMMENT_LIKE':
              this.likesSubject.next({ type, ...data });
              break;
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
              console.log('Unhandled WebSocket event:', type, data);
          }
        } catch (e) {
          console.error('Error parsing WebSocket message:', e);
        }
      };

      this.socket.onclose = () => {
        this.isConnected = false;

        // Execute progressive retry using an exponential backoff formula, capped at 30 seconds
        if (this.shouldReconnect) {
          setTimeout(() => {
            this.reconnectInterval = Math.min(this.reconnectInterval * 1.5, 30000);
            this.connect();
          }, this.reconnectInterval);
        }
      };

      this.socket.onerror = (error) => {
        console.error('WebSocket error:', error);
      };
    } catch (err) {
      console.error('Failed to establish WebSocket:', err);
    }
  }

  /**
   * Registers the currently logged-in user's public ID to the server-side socket pool.
   * <p>
   * Transmits a {@code REGISTER} payload frame enclosing the user's public ID. This registers the session
   * on the Spring backend in a thread-safe map, enabling low-latency targeted unicasts like direct messages.
   * </p>
   */
  public registerUser(): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return;
    }

    const publicId = this.authService.getPublicId();
    if (publicId) {
      this.socket.send(
        JSON.stringify({
          type: 'REGISTER',
          publicId: publicId,
        }),
      );
    }
  }

  /**
   * Gracefully tears down the active WebSocket connection.
   * Disables reconnection hooks and triggers socket closure to prevent backend ghost sessions.
   */
  public disconnect(): void {
    this.shouldReconnect = false;
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  /**
   * Angular component destruction lifecycle hook.
   * Cleans up resources, active sockets, and prevents memory leaks upon module destruction.
   */
  ngOnDestroy(): void {
    this.disconnect();
  }
}
