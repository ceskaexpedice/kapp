import { Component, effect, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { Label } from '../../shared/label';
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { HttpParams } from '@angular/common/http';

@Component({
  selector: 'app-author-item',
  imports: [RouterModule, TranslateModule,
    MatCardModule, MatButtonModule, MatMenuModule,
    MatIconModule, MatChipsModule, MatProgressBarModule],
  templateUrl: './author-item.html',
  styleUrl: './author-item.scss'
})
export class AuthorItem {

  doc = input<any>();
  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);
  loadingLinks = signal<boolean>(false);
  loading = signal(false);

  states = ['controlled_public', 'controlled', 'new_imported']
  labels: Label[] = [];

  
  aut = signal<any[]>([]);
  numTitles = signal<number>(0);

  constructor(
    public router: Router,
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) {
    effect(() => {
      if (this.doc().nameIdentifier) {
        const p: any = {};
        p.id= this.doc().nameIdentifier;
        this.loadingLinks.set(true);
        this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
        this.loadingLinks.set(false);
          if (resp.links) {
            this.links.set(resp.links);
            this.linkTypes.set(Object.keys(resp.links));
          }
        });
      }
      this.labels = [];
      if (this.doc()['labels']) {
        this.doc()['labels'].forEach((name: string) => {
          const l = this.state.labels().find(la => la.name === name);
          if (l) {
            this.labels.push({ name: name, color: l.color, text_color: l.text_color });
          }
          
        });
      }
      this.searchTitles();
    })
  }

  

  addLabel(l: Label) {
    if (!this.labels.find(la => la.name === l.name)) {
      this.labels.push(l);
      this.saveLabels();
    }
  }

  removeLabel(l: Label) {
    const index = this.labels.indexOf(l);
    if (index > -1) {
      this.labels.splice(index, 1);
      this.saveLabels();
    }
  }

  saveLabels() {
    this.service.saveDocLabels(this.doc()['pid'], this.labels.map(l => l.name)).subscribe((res: any) => {
      console.log(res)
    })

  }

  editNote() {

  }

  checkAutor() {
    this.loadingLinks.set(true);
    const p: any = {};
    p.rows = 10;
    p.author = this.doc()['full'];
    this.service.checkAuthor(p as HttpParams).subscribe((resp: any) => {
      this.aut.set(resp.response.docs);
      this.loadingLinks.set(false);
    });
  }

  searchTitles() {
    const p: any = {};
    p.rows = 0;
    p.author_id = this.doc()['id'];
    p.only_pid = true;
    this.service.searchTitlesByAuthor(p as HttpParams).subscribe((resp: any) => {
       this.numTitles.set(resp.response.numFound);
    });

  }

  gotoTitles() {
    this.state.usedFacets.set([{field: "authors_ids", value: this.doc()['id']}]);
    this.router.navigate(['/titles'], {queryParams: {s: this.state.encodeState()}});
  }
}
