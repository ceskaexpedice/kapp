import { TestBed } from '@angular/core/testing';

import { AppState } from './app-state';

describe('AppState roles', () => {
  let state: AppState;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    state = TestBed.inject(AppState);
  });

  it('reads a realm role from the Keycloak token', () => {
    state.setUser({realm_access: {roles: ['kappAdmin']}});

    expect(state.hasRole('kappAdmin')).toBeTrue();
  });

  it('reads a client role from the Keycloak token', () => {
    state.setUser({resource_access: {kapp: {roles: ['kappAdmin']}}});

    expect(state.hasRole('kappAdmin')).toBeTrue();
  });

  it('rejects users without the role', () => {
    state.setUser({realm_access: {roles: ['viewer']}});

    expect(state.hasRole('kappAdmin')).toBeFalse();
  });
});
