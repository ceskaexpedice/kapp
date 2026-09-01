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

  it('allows every logged-in user to edit title labels regardless of library rights', () => {
    state.setUser({realm_access: {roles: ['viewer']}});

    expect(state.canEditTitleLabels()).toBeTrue();
    expect(state.canEditTitle({physical_locations: ['MZK01']})).toBeFalse();

    state.setUser(null);
    expect(state.canEditTitleLabels()).toBeFalse();
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

  it('combines label sets visible to the user', () => {
    state.setLabelSet('labels', [{name: 'global'}]);
    state.setLabelSet('labels_jane', [{name: 'private'}]);
    state.setLabelSet('labels_MZK', [{name: 'library'}]);

    expect(state.labels().map(label => label.name)).toEqual(['global', 'private', 'library']);
  });

  it('does not duplicate a label name shared by multiple scopes', () => {
    state.setLabelSet('labels', [{name: 'shared', color: '#111111'}]);
    state.setLabelSet('labels_MZK', [{name: 'shared', color: '#ffffff'}]);

    expect(state.labels()).toEqual([{name: 'shared', color: '#111111'}]);
  });

  it('keeps scoped labels with the same display name when their storage names differ', () => {
    state.setLabelSet('labels_jane', [{name: 'review', storageName: 'review_jane'}]);
    state.setLabelSet('labels_MZK', [{name: 'review', storageName: 'review_MZK'}]);

    expect(state.labels().map(label => label.storageName)).toEqual(['review_jane', 'review_MZK']);
  });

  it('serializes text and facets as readable query parameters', () => {
    state.q = 'uuid:470c08eb-6ed0-11f1-befe-be5744c0a6f6';
    state.usedFacets.set([
      {field: 'labels', value: 'review_jane'},
      {field: 'labels', value: 'new_imported'},
      {field: 'state', value: 'TO_CONTROL', negated: true}
    ]);

    expect(state.toQueryParams()).toEqual({
      q: 'uuid:470c08eb-6ed0-11f1-befe-be5744c0a6f6',
      labels: ['review_jane', 'new_imported'],
      state_not: 'TO_CONTROL'
    });
  });

  it('restores readable query parameters including repeated and negated facets', () => {
    state.decodeQueryParams({
      q: 'a title',
      labels: ['review_jane', 'new_imported'],
      state_not: 'TO_CONTROL'
    });

    expect(state.q).toBe('a title');
    expect(state.usedFacets()).toEqual([
      {field: 'labels', value: 'review_jane'},
      {field: 'labels', value: 'new_imported'},
      {field: 'state', value: 'TO_CONTROL', negated: true}
    ]);
  });

  it('keeps decoding legacy state links', () => {
    const legacyState = btoa(encodeURIComponent(JSON.stringify({
      q: 'legacy',
      f: [{field: 'labels', value: 'old_label'}]
    })));

    state.decodeQueryParams({s: legacyState});

    expect(state.q).toBe('legacy');
    expect(state.usedFacets()).toEqual([{field: 'labels', value: 'old_label'}]);
  });

  it('prefers readable parameters when both URL formats are present', () => {
    const legacyState = btoa(encodeURIComponent(JSON.stringify({q: 'legacy', f: []})));

    state.decodeQueryParams({s: legacyState, q: 'readable', state: 'TO_CONTROL'});

    expect(state.q).toBe('readable');
    expect(state.usedFacets()).toEqual([{field: 'state', value: 'TO_CONTROL'}]);
  });
});
