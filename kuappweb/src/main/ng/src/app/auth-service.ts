import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { map, Observable, throwError } from 'rxjs';
import { User } from './shared/user';
import { AppConfiguration } from './app-configuration';
import { AppState } from './app-state';
import { AppService } from './app.service';
import { ExternalNavigationService } from './external-navigation.service';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  static AUTH_AUTHORIZED = 1;
  static AUTH_NOT_AUTHORIZED = 2;
  static AUTH_NOT_LOGGED_IN = 3;

  static GLOBAL_ACTIONS_LOADED = 0;
  static SPECIFIC_ACTIONS_LOADED = 0;

  static token: string;
  static tokenTime: Date;
  static tokenDeadline: Date;
  static tokenCounter: number;

  // global actions
  authorizedGlobalActions: string[];

  // specific actions
  authorizedSpecificActions: any = {};

  constructor(
    private http: HttpClient,
    private settings: AppConfiguration,
    private router: Router, 
    private service: AppService,
    private state: AppState,
    private navigation: ExternalNavigationService
  ) {
    AuthService.token = localStorage.getItem('account.token');
    AuthService.tokenTime = new Date(localStorage.getItem('account.token.time'));

    this.settings.interceptresponse.subscribe((event: any) => {
      if (event.type && event.type === "token_expired") {
        AuthService.token = null;
        AuthService.tokenDeadline = null;
        AuthService.tokenCounter = null;
        AuthService.tokenTime = null;
        this.logout();
      }
    });
  }



  baseUrl(): string {
    const deployPath = this.settings.deployPath || '/';
    const basePath = deployPath.endsWith('/') ? deployPath : `${deployPath}/`;
    return new URL(basePath, window.location.origin).toString();
  }

  login() {
    const currentUrl = this.router.url;
    const target = currentUrl.startsWith('/keycloak') ? '/' : currentUrl;
    const redirectUri = new URL('keycloak', this.baseUrl()).toString();
    const loginUrl = new URL(`${this.settings.keycloakUrl}/auth`);

    localStorage.setItem('loginUrl', target);
    localStorage.setItem('redirectUri', redirectUri);
    loginUrl.searchParams.set('client_id', this.settings.keycloak.clientId);
    loginUrl.searchParams.set('redirect_uri', redirectUri);
    loginUrl.searchParams.set('response_type', 'code');
    loginUrl.searchParams.set('scope', 'openid');

    this.navigation.assign(loginUrl.toString());
  }

  logout(suffix: string = "") {
    this.service.logout().subscribe({
      next: () => this.finishLogout(suffix),
      error: () => this.finishLogout(suffix)
    });
  }

  private finishLogout(suffix: string): void {
    AuthService.token = null;
    AuthService.tokenTime = null;
    AuthService.tokenDeadline = null;
    AuthService.tokenCounter = null;

    localStorage.removeItem('account.token');
    localStorage.removeItem('account.token.time');
    localStorage.removeItem('loginUrl');
    localStorage.removeItem('redirectUri');
    this.state.code = null;
    this.state.session_state = null;
    this.state.user.set(null);

    const postLogoutPath = suffix && suffix.startsWith('/') ? suffix.substring(1) : '';
    const postLogoutRedirectUri = new URL(postLogoutPath, this.baseUrl()).toString();
    const configuredLogoutUrl = this.settings.keycloak?.logoutUrl;
    const logoutUrl = new URL(configuredLogoutUrl || `${this.settings.keycloakUrl}/logout`);
    logoutUrl.searchParams.set('client_id', this.settings.keycloak.clientId);
    logoutUrl.searchParams.set('post_logout_redirect_uri', postLogoutRedirectUri);

    this.navigation.assign(logoutUrl.toString());
  }

  isLoggedIn() {
    return this.state.user && this.state.user().isLoggedIn();
  }

  isAuthorized() {
    if (this.state.user()) {
      return true;
    }
    return false;
  }


  getTextProfileImage(): string {
    if (!this.state.user) {
      return '?';
    }
    return (this.state.user.name || this.state.user().sid || '?')[0];
  }

  checkToken(callback: (arg0: number) => void) {
    if (!AuthService.token) {
      callback(AuthService.AUTH_NOT_LOGGED_IN);
      return;
    }

    this.validateToken().subscribe((user: User) => {
      this.state.user.set(user);
    },
      (error) => {
        callback(AuthService.AUTH_NOT_LOGGED_IN);
      });
  }

  keycloakAuth(code: string, callback: (arg0: number) => void = null) {
    this.getUser(code).subscribe(
      (user: any) => {
        if (!user || user.error) {
          callback?.(AuthService.AUTH_NOT_LOGGED_IN);
        } else {
          this.state.user.set(user);
          callback?.(AuthService.AUTH_AUTHORIZED);
        }
      },
      (error) => {
        callback?.(AuthService.AUTH_NOT_LOGGED_IN);
      }
    );
  }

  private validateToken(): Observable<User> {
    return this.http.get(`${this.settings.clientApiBaseUrl}/user?sessionAttributes=true`)
      .pipe(map(response => User.fromJson(response)));
  }


  private logoutKramerius(): Observable<any> {
    return this.http.get(`${this.settings.clientApiBaseUrl}/user/logout`);
  }

  private getUser(code: string): Observable<string> {
    const redirectUri = localStorage.getItem('redirectUri');
    if (!code || !redirectUri) {
      return throwError(() => new Error('Missing Keycloak authorization callback parameters'));
    }
    const params = new HttpParams()
      .set('code', code)
      .set('redirect_uri', redirectUri);
    return this.http.get('/api/user/token', {params}).pipe(map((response: any) =>
      response)
    );

  }
}
