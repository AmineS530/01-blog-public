import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { AuthService } from './core/services/auth.service';
import { of } from 'rxjs';

describe('App', () => {
  let mockAuthService: any;

  beforeEach(async () => {
    mockAuthService = {
      loggedIn$: of(false),
      profile$: of(null),
      checkConnection: () => of(true),
      isLoggedIn: () => false,
      getUsername: () => 'testuser',
      getRole: () => 'USER',
      getCachedProfile: () => null,
      loadCurrentUser: () => of(null),
      invalidateProfileCache: () => {}
    };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render main element', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('main')).toBeTruthy();
  });
});
