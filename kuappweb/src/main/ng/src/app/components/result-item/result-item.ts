import { HttpParams } from '@angular/common/http';
import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';

@Component({
  selector: 'app-result-item',
  imports: [RouterModule, TranslateModule, MatCardModule, MatButtonModule],
  templateUrl: './result-item.html',
  styleUrl: './result-item.scss'
})
export class ResultItem {
  doc = input<any>();
  
  constructor(
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) { }

  checkAutor() {
    const p: any = {};
    p.rows = 10;
    p.author = this.doc()['last_author'];
    this.service.checkAutor(p as HttpParams).subscribe((resp: any) => {
      console.log(resp)
    });
  }
}
