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
import { Author } from '../../shared/author';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-author-item',
  imports: [RouterModule, TranslateModule, FormsModule,
    MatCardModule, MatButtonModule, MatMenuModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatChipsModule, MatProgressBarModule],
  templateUrl: './author-item.html',
  styleUrl: './author-item.scss'
})
export class AuthorItem {

  doc = input<any>();
  author: Author = null;
  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);
  loadingLinks = signal<boolean>(false);
  loading = signal(false);
  editing = signal<boolean>(false);

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
      this.author = this.doc();
      if (this.author.nameIdentifier) {
        this.getLinks();
      }
      this.labels = [];
      if (this.author['labels']) {
        this.author['labels'].forEach((name: string) => {
          const l = this.state.labels().find(la => la.name === name);
          if (l) {
            this.labels.push({ name: name, color: l.color, text_color: l.text_color });
          }

        });
      }
      this.searchTitles();
    })
  }

  

  toggleEdit() {
    this.editing.set(!this.editing());
  }

  getLinks() {
    const p: any = {};
        p.id = this.author.nameIdentifier;
        this.loadingLinks.set(true);
        this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
          this.loadingLinks.set(false);
          if (resp.links) {
            this.links.set(resp.links);
            this.linkTypes.set(Object.keys(resp.links));
          }
        });
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
    this.service.saveAuthorLabels(this.author['id'], this.labels.map(l => l.name)).subscribe((res: any) => {
      console.log(res)
    })

  }

  checkAutor() {
    this.loadingLinks.set(true);
    const p: any = {};
    p.rows = 10;
    p.author = this.author['full'];
    this.service.checkAuthor(p as HttpParams).subscribe((resp: any) => {
      this.aut.set(resp.response.docs);
      this.loadingLinks.set(false);
    });
  }

  setFromAut(a: any) {

    const dates = a['datafield_100d'].split('-');
    const auth: Author = {
      id: a['controlfield_001'],
      nameIdentifier: a['controlfield_001'],
      full: a['datafield_100a'],
      family: a['family']?.[0],
      given: a['given']?.[0],

      date: a['datafield_100d'],

      'date_range_start.year': dates[0],
      'date_range_end.year': dates.length > 1 ? dates[1] : '',
      labels: this.author.labels

    }
    this.author = auth;
    this.getLinks();
  }

  save() {

    this.service.saveAuthor(this.author).subscribe((res: any) => {
      console.log(res)
    })
  }

  searchTitles() {
    const p: any = {};
    p.rows = 0;
    p.author_id = this.author['id'];
    p.only_pid = true;
    this.service.searchTitlesByAuthor(p as HttpParams).subscribe((resp: any) => {
      this.numTitles.set(resp.response.numFound);
    });

  }

  gotoTitles() {
    this.state.usedFacets.set([{ field: "authors_ids", value: this.author['id'] }]);
    this.router.navigate(['/titles'], { queryParams: { s: this.state.encodeState() } });
  }
}
