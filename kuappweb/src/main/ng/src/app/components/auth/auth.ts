import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../auth-service';
import { AppState } from '../../app-state';

@Component({
  selector: 'app-auth',
  imports: [],
  templateUrl: './auth.html',
  styleUrl: './auth.scss'
})
export class Auth {

  constructor(private auth: AuthService,
              private route: ActivatedRoute,
              public state: AppState,
              public router: Router) { }

  ngOnInit() {
    const params = this.route.snapshot.queryParamMap;

    const code = params.get('code');
    const storedTarget = localStorage.getItem('loginUrl') || '/';
    const target = storedTarget.startsWith('/') && !storedTarget.startsWith('/keycloak')
      ? storedTarget
      : '/';

    this.state.session_state = params.get('session_state');
    this.state.code = code;
    localStorage.removeItem('loginUrl');

    if (!code || params.has('error')) {
      localStorage.removeItem('redirectUri');
      this.router.navigateByUrl('/login?failure=2', {replaceUrl: true});
      return;
    }

    this.auth.keycloakAuth(code, (status: number) => {
      localStorage.removeItem('redirectUri');
      this.state.code = null;
      if (status == AuthService.AUTH_AUTHORIZED) {
        this.router.navigateByUrl(target, {replaceUrl: true});
      } else {
        this.router.navigateByUrl('/login?failure=2', {replaceUrl: true});
      }
    });
  }
}
