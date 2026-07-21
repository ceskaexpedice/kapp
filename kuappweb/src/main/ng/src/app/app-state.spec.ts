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

  it('allows a title when any physical location matches any library role', () => {
    state.setUser({realm_access: {roles: ['MZK', 'NKP']}});
    state.libraryPhysicalLocations.set([
      {library: 'MZK', physicalLocations: ['MZK01']},
      {library: 'NKP', physicalLocations: ['ABA001']}
    ]);

    expect(state.canEditTitle({physical_locations: ['OTHER', 'ABA001']})).toBeTrue();
    expect(state.canEditTitle({physical_locations: ['MZK01']})).toBeTrue();
    expect(state.canEditTitle({physical_locations: ['OTHER']})).toBeFalse();
  });

  it('does not grant access from a mapping when the user lacks its role', () => {
    state.setUser({realm_access: {roles: ['MZK']}});
    state.libraryPhysicalLocations.set([
      {library: 'NKP', physicalLocations: ['ABA001']}
    ]);

    expect(state.canEditTitle({physical_locations: ['ABA001']})).toBeFalse();
  });

  it('allows kappAdmin to edit every title', () => {
    state.setUser({realm_access: {roles: ['kappAdmin']}});

    expect(state.canEditTitle({physical_locations: []})).toBeTrue();
  });

  it('requires access to every title in a bulk edit', () => {
    state.setUser({realm_access: {roles: ['MZK']}});
    state.libraryPhysicalLocations.set([
      {library: 'MZK', physicalLocations: ['MZK01']}
    ]);

    expect(state.canEditTitles([
      {physical_locations: ['MZK01']},
      {physical_locations: ['MZK01', 'OTHER']}
    ])).toBeTrue();
    expect(state.canEditTitles([
      {physical_locations: ['MZK01']},
      {physical_locations: ['ABA001']}
    ])).toBeFalse();
  });

  it('recognizes any configured library role for global author editing', () => {
    state.setUser({realm_access: {roles: ['MZK']}});
    state.libraryPhysicalLocations.set([
      {library: 'MZK', physicalLocations: []}
    ]);

    expect(state.hasLibraryRole()).toBeTrue();

    state.setUser({realm_access: {roles: ['viewer']}});
    expect(state.hasLibraryRole()).toBeFalse();
  });
});
