import { Component, effect, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterModule } from '@angular/router';
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

  states = ['controlled_public', 'controlled', 'new_imported']
  labels: Label[] = [];

  
  aut = signal<any[]>([]);

  constructor(
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
    const p: any = {};
    p.rows = 10;
    p.author = this.doc()['full'];
    this.service.checkAuthor(p as HttpParams).subscribe((resp: any) => {
      this.aut.set(resp.response.docs);
    });
  }

  searchTitles() {
    
    // const uf = this.usedFacets.find(f => f.field === field && f.value === value);
    // if (uf) {
    //   this.usedFacets = this.usedFacets.filter(f => !(f.field === field && f.value === value));
    // } else {
    //   this.usedFacets.push({field, value});
    // }
    // this.hasUsedFacets = this.usedFacets.length > 0;
    // this.state.usedFacets.update(f => [...this.usedFacets]);
  }
}
