import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { map, Observable } from 'rxjs';
import { User } from './shared/user';
import { AppConfiguration } from './app-configuration';
import { AppState } from './app-state';
import { AppService } from './app.service';

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
    private state: AppState
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
    return location.origin + this.settings.deployPath;
  }

  login() {
    const redircetUri = `${this.baseUrl()}/keycloak`;
    let url = `${this.settings.clientApiBaseUrl}/user/auth/login?&redirect_uri=${redircetUri}`;
    if (this.settings.keycloak && this.settings.keycloak['loginType']) {
      url = url + "&type=" + this.settings.keycloak['loginType'];
    }
    window.open(url, '_top');
  }

  logout(suffix: string = "") {

    this.service.logout().subscribe(() => {
      AuthService.token = null;
      AuthService.tokenTime = null;
      AuthService.tokenDeadline = null;
      AuthService.tokenCounter = null;

      localStorage.removeItem('account.token');
      localStorage.removeItem('account.token.time');
      this.state.user.set(null);
    })

    // const redircetUri = `${this.baseUrl()}${suffix}`;
    // //let url = `${this.settings.clientApiBaseUrl}/user/auth/logout?&redirect_uri=${redircetUri}`;
    // let url = `${this.settings.clientApiBaseUrl}/user/auth/logout?&redirect_uri=${redircetUri}`;
    // if (this.settings.keycloak.logoutUrl) {
    //   url = this.settings.keycloak.logoutUrl;
    // }
    // window.open(url, '_top');
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
        if (!user) {
          callback(AuthService.AUTH_NOT_LOGGED_IN);
        } else {
          this.state.user.set(user);
          callback(AuthService.AUTH_AUTHORIZED);
        }
      },
      (error) => {
        callback(AuthService.AUTH_NOT_LOGGED_IN);
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
    // const redircetUri = `${this.baseUrl()}/keycloak`;
    // const keycloakUrl = this.settings.keycloakUrl + '/token';
    // let url = `${this.settings.clientApiBaseUrl}/user/auth/login?&redirect_uri=${redircetUri}`;
    const redirect_uri = localStorage.getItem('redirectUri');
    let url = `/api/user/token?code=${code}&redirect_uri=${redirect_uri}`;
    return this.http.get(url).pipe(map((response: any) =>
      response)
    );

  }
}
