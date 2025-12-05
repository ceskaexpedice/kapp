import { Component } from '@angular/core';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatMenuModule } from '@angular/material/menu';
import { AppState } from '../../app-state';
import { AppConfiguration } from '../../app-configuration';
import { AuthService } from '../../auth-service';

@Component({
  selector: 'app-navbar',
  imports: [TranslateModule, RouterModule,
    MatButtonModule, MatToolbarModule, MatMenuModule
  ],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss'
})
export class Navbar {

  constructor(
    private router: Router,
    public translator: TranslateService,
    public state: AppState,
    private config: AppConfiguration,
    public auth: AuthService
  ) { }

  ngOnInit(): void {
    this.onLanguageChanged('cs');
  }

  viewUser() {
    
    console.log(this.auth.user())
  }

  onLanguageChanged(lang: string) {
    this.translator.use(lang);
  }
  goToKeycloakLogin() {
    const keycloakUrl = this.config.keycloakUrl + '/auth';
      // 'https://eduid.inovatika.dev/realms/kapp/protocol/openid-connect/token';
      //'https://eduid.inovatika.dev/realms/kapp/protocol/openid-connect/auth';

    const clientId = 'kapp';
    localStorage.setItem('loginUrl', this.router.url);
    const redirectUri = encodeURIComponent(window.location.origin + '/keycloak');
    localStorage.setItem('redirectUri', redirectUri);
    const responseType = 'code';
    const scope = 'openid';

    const url =
      `${keycloakUrl}?client_id=${clientId}` +
      `&redirect_uri=${redirectUri}` +
      `&response_type=${responseType}` +
      `&scope=${scope}`;

    window.location.href = url;
  }
}
