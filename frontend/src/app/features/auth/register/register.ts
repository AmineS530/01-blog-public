import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatInputModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule
  ],
  templateUrl: './register.html'
})
export class RegisterComponent {
  form: FormGroup;
  error = '';
  loading = false;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';

    this.auth.register(this.form.value).subscribe({
      next: () => this.router.navigate(['/feed']),
      error: err => {
        this.error = err.error?.message || 'Registration failed';
        this.loading = false;
      }
    });
  }
}