import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatMenuModule } from '@angular/material/menu';
import { AppState } from '../../app-state';
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
    public translator: TranslateService,
    public state: AppState,
    public auth: AuthService
  ) { }

  ngOnInit(): void {
    this.auth.resumeLoginAfterLogout();
    this.onLanguageChanged('cs');
  }

  viewUser() {
    console.log(this.state.user())
  }

  logout() {
    this.auth.logout('');
  }

  onLanguageChanged(lang: string) {
    this.translator.use(lang);
  }
  goToKeycloakLogin() {
    this.auth.login();
  }
}
