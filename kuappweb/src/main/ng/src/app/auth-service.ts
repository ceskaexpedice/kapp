import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { map, Observable } from 'rxjs';
import { User } from './shared/user';
import { AppConfiguration } from './app-configuration';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  static AUTH_AUTHORIZED = 1;
  static AUTH_NOT_AUTHORIZED = 2;
  static AUTH_NOT_LOGGED_IN = 3;

  static GLOBAL_ACTIONS_LOADED = 0;
  static SPECIFIC_ACTIONS_LOADED = 0;

  user: User;
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
    private router: Router
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
    AuthService.token = null;
    AuthService.tokenTime = null;
    AuthService.tokenDeadline = null;
    AuthService.tokenCounter = null;

    localStorage.removeItem('account.token');
    localStorage.removeItem('account.token.time');
    this.user = null;

    const redircetUri = `${this.baseUrl()}${suffix}`;
    //let url = `${this.settings.keycloak.baseUrl}/realms/kramerius/protocol/openid-connect/logout`;
    let url = `${this.settings.clientApiBaseUrl}/user/auth/logout`;
    if (this.settings.keycloak.logoutUrl) {
      url = this.settings.keycloak.logoutUrl;
    }
    window.open(url, '_top');
  }

  isLoggedIn() {
    return this.user && this.user.isLoggedIn();
  }

  isAuthorized() {
    let flag = this.user && this.authorizedGlobalActions && this.authorizedGlobalActions.indexOf('a_admin_read') >= 0;
    if (flag) {
      return this.authorizedGlobalActions.indexOf("a_admin_read") >= 0;
    }
    //return true;  
    return false;
  }


  getTextProfileImage(): string {
    if (!this.user) {
      return '?';
    }
    return (this.user.name || this.user.uid || '?')[0];
  }

  checkToken(callback: (arg0: number) => void) {
    if (!AuthService.token) {
      callback(AuthService.AUTH_NOT_LOGGED_IN);
      return;
    }

    this.validateToken().subscribe((user: User) => {
      this.user = user;
    },
      (error) => {
        callback(AuthService.AUTH_NOT_LOGGED_IN);
      });
  }

  keycloakAuth(code: string, callback: (arg0: number) => void = null) {
    this.getToken(code).subscribe(
      (token: string) => {
        if (!token) {
          callback(AuthService.AUTH_NOT_LOGGED_IN);
        } else {
          AuthService.token = token;
          AuthService.tokenTime = new Date();
          localStorage.setItem('account.token', token);
          localStorage.setItem('account.token.time', new Date().toISOString());
          this.checkToken(callback);
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

  private getToken(code: string): Observable<string> {
    const redircetUri = `${this.baseUrl()}/keycloak`;
    // let url = `${this.settings.clientApiBaseUrl}/user/auth/login?&redirect_uri=${redircetUri}`;
    let url = `${this.settings.clientApiBaseUrl}/user/auth/token?code=${code}&redirect_uri=${redircetUri}`;
    return this.http.get(url).pipe(map((response: any) =>
      response['access_token'])
    );

  }
}
