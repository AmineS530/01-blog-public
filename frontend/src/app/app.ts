import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule, 
    RouterOutlet, 
    MatToolbarModule, 
    MatButtonModule, 
    MatIconModule, 
    MatTooltipModule
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  constructor(public authService: AuthService, private router: Router) {}

  goToFeed(): void {
    this.router.navigate(['/feed']);
  }

  goToMyProfile(): void {
    const username = this.authService.getUsername();
    if (username) {
      this.router.navigate(['/profile', username]);
    }
  }

  goToCreate(): void {
    this.router.navigate(['/posts/create']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
