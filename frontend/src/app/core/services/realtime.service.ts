import { Injectable, OnDestroy } from '@angular/core';
import { Subject, Observable } from 'rxjs';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root',
})
export class RealtimeService implements OnDestroy {
  private socket: WebSocket | null = null;
  private reconnectInterval = 3000;
  private shouldReconnect = true;
  private isConnected = false;

  private postsSubject = new Subject<any>();
  private commentsSubject = new Subject<any>();
  private likesSubject = new Subject<any>();
  private messagesSubject = new Subject<any>();

  public posts$: Observable<any> = this.postsSubject.asObservable();
  public comments$: Observable<any> = this.commentsSubject.asObservable();
  public likes$: Observable<any> = this.likesSubject.asObservable();
  public messages$: Observable<any> = this.messagesSubject.asObservable();

  constructor(private authService: AuthService) {
    this.connect();
    // Re-register if user login status changes
    // We can also monitor token updates or trigger manually
  }

  public connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.shouldReconnect = true;
    const wsUrl = 'ws://localhost:8080/ws';
    
    try {
      this.socket = new WebSocket(wsUrl);

      this.socket.onopen = () => {
        this.isConnected = true;
        this.reconnectInterval = 3000; // Reset backoff
        this.registerUser();
      };

      this.socket.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const type = payload.type;
          const data = payload.data;

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
            default:
              console.log('Unhandled WebSocket event:', type, data);
          }
        } catch (e) {
          console.error('Error parsing WebSocket message:', e);
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
        console.error('WebSocket error:', error);
      };
    } catch (err) {
      console.error('Failed to establish WebSocket:', err);
    }
  }

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
        })
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

  ngOnDestroy(): void {
    this.disconnect();
  }
}
