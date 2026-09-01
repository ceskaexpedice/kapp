import { Component, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
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
import { UIService } from '../../ui.service';
import { SimpleDialogData } from '../simple-dialog/simple-dialog';
import { SimpleDialogComponent } from '../simple-dialog/simple-dialog.component';
import { MatDialog } from '@angular/material/dialog';

@Component({
  selector: 'app-author-item',
  imports: [RouterModule, TranslateModule, FormsModule,
    MatCardModule, MatButtonModule, MatMenuModule, MatFormFieldModule, MatInputModule,
    MatIconModule, MatChipsModule, MatProgressBarModule],
  templateUrl: './author-item.html',
  styleUrl: './author-item.scss'
})
export class AuthorItem {

  readonly translator = inject(TranslateService);
  readonly dialog = inject(MatDialog);
  inputAuthor = input<any>();
  //numTitles = input<number>(0);
   numTitles = signal<number>(0);
  author: Author = null;
  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);
  // loadingLinks = signal<boolean>(false);
  loading = signal(false);
  editing = signal<boolean>(false);

  states = ['controlled_public', 'controlled', 'new_imported']
  labels: Label[] = [];


  aut = signal<any[]>([]);

  authorChanged = output<boolean>();

  constructor(
    public router: Router,
    private ui: UIService,
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) {
    effect(() => {
      this.author = Object.assign({}, this.inputAuthor());
      if (this.author.nameIdentifier) {
        this.getLinks();
      }
      this.labels = [];
      if (this.author['labels']) {
        this.author['labels'].forEach((name: string) => {
          const l = this.state.labels().find(la => (la.storageName || la.name) === name);
          if (l) {
            this.labels.push({...l});
          } 

        });
      }
      this.searchTitles();
    })
  }

  

  toggleEdit() {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    this.editing.set(!this.editing());
  }

  getLinks() {
    const p: any = {};
        p.id = this.author.nameIdentifier;
        this.loading.set(true);
        this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
          this.loading.set(false);
          if (resp.links) {
            this.links.set(resp.links);
            this.linkTypes.set(Object.keys(resp.links));
          }
        });
  }

  addLabel(l: Label) {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    if (!this.labels.find(la => (la.storageName || la.name) === (l.storageName || l.name))) {
      this.labels.push(l);
      this.saveLabel(l, true);
    }
  }

  removeLabel(l: Label) {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    const index = this.labels.indexOf(l);
    if (index > -1) {
      this.labels.splice(index, 1);
      this.saveLabel(l, false);
    }
  }

  saveLabel(label: Label, selected: boolean) {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    this.service.saveAuthorLabel(this.author['id'], label.storageName || label.name, selected).subscribe((res: any) => {
      console.log(res)
    })

  }

  checkAutor() {
    this.loading.set(true);
    const p: any = {};
    p.rows = 10;
    p.author = this.author['full'];
    this.service.checkAuthor(p as HttpParams).subscribe((resp: any) => {
      this.aut.set(resp.response.docs);
      this.loading.set(false);
    });
  }

  setFromAut(a: any) {
    this.editing.set(true);
    const dates = a['datafield_100d'] ? a['datafield_100d'].split('-') : [];
    const auth: Author = {
      old_id: this.author.old_id ? this.author.old_id : this.author.id,
      id: a['controlfield_001'],
      nameIdentifier: a['controlfield_001'],
      full: a['datafield_100a'],
      family: a['family']?.[0],
      given: a['given']?.[0],
      date: a['datafield_100d'],
      year_birth: dates[0] ? parseInt(dates[0]) : null,
      year_death: dates.length > 1 ? parseInt(dates[1]) : null,
      // labels: [...this.author.labels, 'author_modified']
      labels: [...this.labels.map(l => l.name).filter(l => l !== 'author_modified'), 'author_modified']

    }
    this.author = auth;
    this.getLinks();
  }

  setFull() {
    this.author.full = this.author.family + ', ' + this.author.given;
    this.author.date = (this.author.year_birth ? this.author.year_birth : '') + '-' + (this.author.year_death ? this.author.year_death : '');

    if (this.author.nameIdentifier) {
      this.author.id = this.author.nameIdentifier;
    } else {
      this.author.id = (
        (this.author.family ? this.author.family : '') +
        (this.author.given ? this.author.given : '') +
        (this.author.year_birth ? this.author.year_birth : '') +
        (this.author.year_death ? this.author.year_death : '') +
        (this.author.note ? this.author.note : '')
      ).toLowerCase().normalize('NFKD').replace(/[^\w]/g, '');
    }
  }

  checkEditedAuthor() {
      const p: any = {};
      p.id = this.author.id;
      this.service.getAuthorById(p as HttpParams).subscribe((resp: any) => {
        const a = resp.response.numFound;
        if (resp.response.numFound === 0) {
          this.save();
        } else {
  
            const message = this.translator.instant('alert.saving_author_exists_desc');
            const data: SimpleDialogData = {
              title: this.translator.instant('alert.saving_author_exists'),
              message: message,
              alertClass: 'app-warn',
              btn1: {
                label: 'Ano',
                value: 'yes',
                color: 'warn'
              },
              btn2: {
                label: 'Ne',
                value: 'no',
                color: 'default'
              }
            };
            const dialogRef = this.dialog.open(SimpleDialogComponent, {
              data: data
            });
            dialogRef.afterClosed().subscribe(result => {
              if (result === 'yes') {
                this.save();
              }
            });
          
        }
      });
    }

  onSave() {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    if (this.author.id !== this.inputAuthor().id) {
      this.checkEditedAuthor();
    } else {
      this.save();
    }
  }

  save() {
    if (!this.state.hasLibraryRole()) {
      return;
    }
    this.loading.set(true);
    if (this.author.id !== this.inputAuthor().id) {
      this.author.old_id = this.inputAuthor().id;
    }
    this.author.labels = [...this.labels.map(l => l.name).filter(l => l !== 'author_modified'), 'author_modified'];
    this.service.saveAuthor(this.author).subscribe((res: any) => {
      this.loading.set(false);
      if (res.error) {
        this.ui.showErrorSnackBar(res.error)
      } else {
        this.authorChanged.emit(true);
      }
    });
  }

  searchTitles() {
    const p: any = {};
    p.rows = 0;
    p.author_id = this.author['id'];
    p.only_pid = false;
    this.service.searchTitlesByAuthor(p as HttpParams).subscribe((resp: any) => {
      this.numTitles.set(resp.response.numFound);
    });

  }

  gotoTitles() {
    this.state.usedFacets.set([{ field: "authors_ids", value: this.author['id'] }]);
    this.router.navigate(['/titles'], { queryParams: this.state.toQueryParams() });
  }
}
