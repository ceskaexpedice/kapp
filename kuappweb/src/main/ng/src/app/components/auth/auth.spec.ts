import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';

import { AppState } from '../../app-state';
import { AuthService } from '../../auth-service';
import { Auth } from './auth';

describe('Auth', () => {
  let component: Auth;
  let fixture: ComponentFixture<Auth>;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['keycloakAuth']);

    await TestBed.configureTestingModule({
      imports: [Auth],
      providers: [
        AppState,
        {provide: AuthService, useValue: authService},
        {provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigateByUrl'])},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {queryParamMap: convertToParamMap({error: 'login_required'})}}
        }
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Auth);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
