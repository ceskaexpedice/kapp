import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-footer',
  imports: [TranslateModule],
  templateUrl: './footer.html',
  styleUrl: './footer.scss'
})
export class Footer {
  currentYear: number=new Date().getFullYear();

  constructor(
    private router: Router,
    public translator: TranslateService
  ) {}

  ngOnInit(): void {
    this.onLanguageChanged('cs');
  }

  onLanguageChanged(lang: string) {
    this.translator.use(lang);
  }

  get isHome(): boolean {
    return this.router.url === '/' || this.router.url === '/home';
  }
}
