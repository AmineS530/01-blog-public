import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { noAuthGuard } from './core/guards/no-auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'register',
    canActivate: [noAuthGuard],
    loadComponent: () =>
      import('./features/auth/register/register').then((m) => m.RegisterComponent),
  },
  {
    path: 'login',
    canActivate: [noAuthGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'feed',
    canActivate: [authGuard],
    loadComponent: () => import('./features/feed/feed').then((m) => m.FeedComponent),
  },
  {
    path: 'posts/create',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/post/create-post/create-post').then((m) => m.CreatePostComponent),
  },
  {
    path: 'posts/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/post/single-post/single-post').then((m) => m.SinglePostComponent),
  },
  {
    path: 'posts/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/post/edit-post/edit-post').then((m) => m.EditPostComponent),
  },
  {
    path: 'profile/:username',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/profile/profile').then(m => m.ProfileComponent)
  },
  { path: '**', redirectTo: 'login' }
  ];
