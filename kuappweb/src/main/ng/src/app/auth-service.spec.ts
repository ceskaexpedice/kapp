import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EventEmitter } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { AppConfiguration } from './app-configuration';
import { AppService } from './app.service';
import { AppState } from './app-state';
import { AuthService } from './auth-service';
import { ExternalNavigationService } from './external-navigation.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let state: AppState;
  let appService: jasmine.SpyObj<AppService>;
  let navigation: jasmine.SpyObj<ExternalNavigationService>;

  beforeEach(() => {
    localStorage.clear();
    appService = jasmine.createSpyObj<AppService>('AppService', ['logout']);
    appService.logout.and.returnValue(of({}));
    navigation = jasmine.createSpyObj<ExternalNavigationService>('ExternalNavigationService', ['assign']);

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AppState,
        provideHttpClient(),
        provideHttpClientTesting(),
        {provide: AppService, useValue: appService},
        {provide: ExternalNavigationService, useValue: navigation},
        {provide: Router, useValue: {url: '/titles'}},
        {
          provide: AppConfiguration,
          useValue: {
            deployPath: undefined,
            keycloakUrl: 'https://id.example/realms/kapp/protocol/openid-connect',
            keycloak: {clientId: 'kapp'},
            interceptresponse: new EventEmitter()
          }
        }
      ]
    });

    service = TestBed.inject(AuthService);
    state = TestBed.inject(AppState);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('ends a stale Keycloak session before starting login', () => {
    service.login();

    expect(appService.logout).toHaveBeenCalled();
    const logoutUrl = new URL(navigation.assign.calls.mostRecent().args[0]);
    expect(logoutUrl.pathname.endsWith('/logout')).toBeTrue();
    expect(logoutUrl.searchParams.get('client_id')).toBe('kapp');
    expect(logoutUrl.searchParams.get('post_logout_redirect_uri')).toBe(
      `${window.location.origin}/keycloak?reauth=1`
    );
    expect(localStorage.getItem('loginUrl')).toBe('/titles');
  });

  it('starts login after Keycloak logout with a single encoded callback', () => {
    localStorage.setItem('loginUrl', '/titles');

    service.continueLoginAfterLogout();

    const loginUrl = new URL(navigation.assign.calls.mostRecent().args[0]);
    expect(loginUrl.pathname.endsWith('/auth')).toBeTrue();
    expect(loginUrl.searchParams.get('client_id')).toBe('kapp');
    expect(loginUrl.searchParams.get('redirect_uri')).toBe(`${window.location.origin}/keycloak`);
    expect(localStorage.getItem('redirectUri')).toBe(`${window.location.origin}/keycloak`);
    expect(localStorage.getItem('loginUrl')).toBe('/titles');
  });

  it('invalidates the app session and then ends the Keycloak session', () => {
    state.setUser({name: 'Test'});

    service.logout();

    expect(appService.logout).toHaveBeenCalled();
    expect(state.user()).toBeNull();
    const logoutUrl = new URL(navigation.assign.calls.mostRecent().args[0]);
    expect(logoutUrl.pathname.endsWith('/logout')).toBeTrue();
    expect(logoutUrl.searchParams.get('client_id')).toBe('kapp');
    expect(logoutUrl.searchParams.get('post_logout_redirect_uri')).toBe(`${window.location.origin}/`);
  });

  it('exchanges the authorization code using the original callback URI', () => {
    localStorage.setItem('redirectUri', `${window.location.origin}/keycloak`);
    let status: number;

    service.keycloakAuth('code+value', result => status = result);

    const request = http.expectOne(req => req.url === '/api/user/token');
    expect(request.request.params.get('code')).toBe('code+value');
    expect(request.request.params.get('redirect_uri')).toBe(`${window.location.origin}/keycloak`);
    request.flush({name: 'Test'});
    expect(status).toBe(AuthService.AUTH_AUTHORIZED);
  });
});
