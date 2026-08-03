import { Injectable } from '@angular/core';
import { CanActivate, Router, UrlTree } from '@angular/router';

import { AppState } from './app-state';

@Injectable({ providedIn: 'root' })
export class KappAdminGuard implements CanActivate {
  constructor(private state: AppState, private router: Router) {}

  canActivate(): boolean | UrlTree {
    return this.state.hasRole('kappAdmin')
      ? true
      : this.router.createUrlTree(['/']);
  }
}
