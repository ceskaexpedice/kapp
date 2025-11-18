import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatMenuModule } from '@angular/material/menu';
import { AppState } from '../../app-state';

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
    public state: AppState
  ) { }

  ngOnInit(): void {
    this.onLanguageChanged('cs');
  }

  onLanguageChanged(lang: string) {
    this.translator.use(lang);
  }
  goToKeycloakLogin() {
    const keycloakUrl =
      'https://eduid.inovatika.dev/realms/master/protocol/openid-connect/auth';

    const clientId = 'kuratorska-aplikace-web';
    const redirectUri = encodeURIComponent(window.location.origin + '/callback');
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
