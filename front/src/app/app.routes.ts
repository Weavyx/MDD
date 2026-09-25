import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'feed',
    canActivate: [authGuard],
    loadComponent: () => import('./features/posts/feed/feed').then((m) => m.Feed),
  },
  // Declared before `posts/:id` so that "new" is not read as an id.
  {
    path: 'posts/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/posts/post-create/post-create').then((m) => m.PostCreate),
  },
  {
    path: 'posts/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/posts/post-detail/post-detail').then((m) => m.PostDetail),
  },
  {
    path: 'topics',
    canActivate: [authGuard],
    loadComponent: () => import('./features/topics/topic-list/topic-list').then((m) => m.TopicList),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile/profile').then((m) => m.Profile),
  },
  { path: '**', redirectTo: '' },
];
