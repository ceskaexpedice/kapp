import { HttpParams } from '@angular/common/http';
import { Component, effect, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { MatIconModule } from '@angular/material/icon';
import { Author } from '../../shared/author';
import { AuthorDialog } from '../author-dialog/author-dialog';
import { MatDialog } from '@angular/material/dialog';

@Component({
  selector: 'app-result-item',
  imports: [RouterModule, TranslateModule, MatCardModule, MatButtonModule,
    MatIconModule
  ],
  templateUrl: './result-item.html',
  styleUrl: './result-item.scss'
})
export class ResultItem {
  doc = input<any>();
  links = signal<{[key: string]:{ident: string, url: string}[]}>({});
  linkTypes = signal<string[]>([]);
  
  constructor(
    private dialog: MatDialog,
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) { 
    effect(() => {
      this.links.set({});
      this.linkTypes.set([]);
      if (this.doc()['last_author_nameIdentifier']) {
        const p: any = {};
        p.id= this.doc()['last_author_nameIdentifier'];
        this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
          console.log(resp)
          if (resp.links) {
            this.links.set(resp.links);
            this.linkTypes.set(Object.keys(resp.links));
          }
        });
      }
    })
  }

  infoAuthor(a: Author) {
    const dialogRef = this.dialog.open(AuthorDialog, {
      data: a,
    });

    dialogRef.afterClosed().subscribe(result => {
      console.log('The dialog was closed');
      if (result !== undefined) {
        
      }
    });

  }

  checkAutor() {
    const p: any = {};
    p.rows = 10;
    p.author = this.doc()['last_author'];
    this.service.checkAutor(p as HttpParams).subscribe((resp: any) => {
      console.log(resp)
    });
  }
}
