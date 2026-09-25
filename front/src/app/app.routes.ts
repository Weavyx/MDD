import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { guestGuard } from './core/auth/guest.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Accueil | MDD',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/home/home').then((m) => m.Home),
  },
  {
    path: 'login',
    title: 'Connexion | MDD',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Inscription | MDD',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'feed',
    title: 'Articles | MDD',
    canActivate: [authGuard],
    loadComponent: () => import('./features/posts/feed/feed').then((m) => m.Feed),
  },
  // Declared before `posts/:id` so that "new" is not read as an id.
  {
    path: 'posts/new',
    title: 'Nouvel article | MDD',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/posts/post-create/post-create').then((m) => m.PostCreate),
  },
  {
    path: 'posts/:id',
    title: 'Article | MDD',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/posts/post-detail/post-detail').then((m) => m.PostDetail),
  },
  {
    path: 'topics',
    title: 'Thèmes | MDD',
    canActivate: [authGuard],
    loadComponent: () => import('./features/topics/topic-list/topic-list').then((m) => m.TopicList),
  },
  {
    path: 'profile',
    title: 'Profil | MDD',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile/profile').then((m) => m.Profile),
  },
  { path: '**', redirectTo: '' },
];
