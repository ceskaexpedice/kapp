import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogContent, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { AuthorTitle } from '../../shared/author';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
import { HttpParams } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBar } from "@angular/material/progress-bar";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from "@angular/material/checkbox";
import { SimpleDialogData } from '../simple-dialog/simple-dialog';
import { SimpleDialogComponent } from '../simple-dialog/simple-dialog.component';
import { Router } from '@angular/router';
import { AppState } from '../../app-state';

@Component({
  selector: 'app-author-dialog',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatButtonToggleModule,
    MatProgressBar, MatFormFieldModule, MatInputModule,
    MatCheckboxModule
  ],
  templateUrl: './author-title-dialog.html',
  styleUrl: './author-title-dialog.scss'
})
export class AuthorTitleDialog {

  readonly translator = inject(TranslateService);
  readonly router = inject(Router);
  readonly service = inject(AppService);
  readonly state = inject(AppState);
  readonly ui = inject(UIService);
  readonly dialogRef = inject(MatDialogRef<AuthorTitleDialog>);
  readonly data = inject<{ pid: string, authors_kapp: AuthorTitle[], authors_kramerius: AuthorTitle[], id: string }>(MAT_DIALOG_DATA);

  readonly dialog = inject(MatDialog);

  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);
  loading = signal<boolean>(false);
  editing = signal<boolean>(false);
  isNew = signal<boolean>(false);

  author = signal<AuthorTitle>(null);
  useKramerius = false;
  hasKapp = signal<boolean>(false);
  old_id = '';

  hasChanges: boolean;

  ngOnInit() {
    if (!this.data.id) {
      // Dialog to add new author
      // Show buttons
      this.isNew.set(true);
      const a: AuthorTitle = {
        id: '',
        full: '',
        family: '',
        given: '',
        date: '',
        note: '',
        role: ['']
      }

      this.author.set(a);
    } else {
      const a = this.data.authors_kapp?.find(a => a.id === this.data.id);
      if (a) {
        this.author.set(JSON.parse(JSON.stringify(a)));
        this.useKramerius = false;
        this.hasKapp.set(true);
        this.old_id = a.old_id;
      } else {
        const k = this.data.authors_kramerius.find(a => a.id === this.data.id);
        this.author.set(JSON.parse(JSON.stringify(k)));
        this.useKramerius = true;
        this.hasKapp.set(false);
      }

      this.getLinks();
    }
  }

  setFull() {
    this.author().full = this.author().family + ', ' + this.author().given;
    this.author().date = this.author().year_birth + '-' + this.author().year_death;

    if (this.author().nameIdentifier) {
      this.author().id = this.author().nameIdentifier;
    } else {
      this.author().id = (this.author().family + this.author().given +
        (this.author().year_birth ? this.author().year_birth : '') +
        (this.author().year_death ? this.author().year_death : '') +
        (this.author().note ? this.author().note : '')
      ).toLowerCase().normalize('NFKD').replace(/[^\w]/g, '');
    }
  }

  setAutor() {
    if (this.useKramerius) {
      this.author.set(this.data.authors_kramerius.find(a => a.id === this.old_id));
    } else {
      this.author.set(this.data.authors_kapp.find(a => a.id === this.data.id));
    }
  }

  getLinks() {
    if (this.author().nameIdentifier) {
      const p: any = {};
      p.id = this.author().nameIdentifier;
      this.loading.set(true);
      this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
        this.loading.set(false);
        if (resp.links) {
          this.links.set(resp.links);
          this.linkTypes.set(Object.keys(resp.links));
        }
      });
    }
  }

  toggleEdit() {
    this.editing.set(!this.editing());
  }

  addById() {
    const id = prompt('ident?', '');
    if (id) {
      const p: any = {};
      p.rows = 10;
      p.id = id
      this.service.getAutById(p as HttpParams).subscribe((resp: any) => {
        const a = resp.response.docs[0];
        if (!a) {
          alert('Id not found')
          return;
        }


        const dates = a['datafield_100d'] ? a['datafield_100d'].split('-') : [];

        const auth: AuthorTitle = {

          id: a['controlfield_001'],
          nameIdentifier: a['controlfield_001'],
          full: a['datafield_100a'],
          family: a['family']?.[0],
          given: a['given']?.[0],
          date: a['datafield_100d'],
          year_birth: dates[0] ? parseInt(dates[0]) : null,
          year_death: dates.length > 1 ? parseInt(dates[1]) : null,
          // labels: [...this.author.labels, 'author_modified']
          role: null,
          labels: ['author_modified'],
          isLast: false

        }

        this.author.set(auth);
        if (resp.error) {
          this.ui.showErrorSnackBar('snackbar.record_changed.error');
        } else {
          this.getLinks();
          //this.ui.showInfoSnackBar('snackbar.record_changed.success');
        }
      });
    }
  }

  onSaveRole() {
    this.save(false);
  }

  onSave() {

    // check fields
    if (this.author().nameIdentifier) {
      const p: any = {};
      p.rows = 1;
      p.id = this.author().nameIdentifier;
      this.service.getAutById(p as HttpParams).subscribe((resp: any) => {
        const a = resp.response.docs[0];
        if (!a) {
          alert('nameIdentifier invalid')
          return;
        } else {
          this.checkEditedAuthor();
        }
      });
    } else {
      this.checkEditedAuthor();
    }


  }

  checkEditedAuthor() {
    const p: any = {};
    p.rows = 1;
    p.id = this.author().id;
    this.service.getAuthorById(p as HttpParams).subscribe((resp: any) => {
      const a = resp.response.docs[0];
      if (!a) {
        //this.save(true);
      } else {

        if (this.author().family !== a['family'] ||
          this.author().given !== a['given'] ||
          this.author().year_birth !== a['year_birth'] ||
          this.author().year_death !== a['year_death']
        ) {
          const message = this.translator.instant('alert.saving_author_differs_desc') +
                          `<div>${this.translator.instant('field.family')}: ${a['family']} -> ${this.author().family} </div>
                          <div>${this.translator.instant('field.given')}: ${a['given']} -> ${this.author().given} </div>
                          <div>${this.translator.instant('field.date_birth')}: ${a['year_birth']} -> ${this.author().year_birth} </div>
                          <div>${this.translator.instant('field.date_death')}: ${a['year_death']} -> ${this.author().year_death} </div>
                          `
          const data: SimpleDialogData = {
            title: this.translator.instant('alert.saving_author_differs'),
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
              this.save(true);
            }
          });
        } else {
          this.save(true);
        }
        
      }
    });
  }

  save(inAuthors: boolean): void {
    console.log(this.author());
    const authors = this.data.authors_kapp ? [...this.data.authors_kapp] : [];
    const ka = authors.find(a => a.id === this.author().id);
    if (ka) {
      ka.role = this.author().role
    } else {
      authors.push(this.author())
    }

    this.service.saveAuthorsInTitle(authors, this.data.pid, inAuthors ? this.author() : null).subscribe((res: any) => {
      if (res.error) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      } else {
        console.log(this.author());
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
        this.hasChanges = true;
      }
    })
  }

  setAsLast() {
    if (this.author().year_death) {
      this.service.saveDecisionDate(this.data.pid, this.author().year_death).subscribe((res: any) => {
        this.hasChanges = true;
        if (res.error) {
          this.ui.showErrorSnackBar('snackbar.record_changed.error');
        } else {
          console.log(res);
          this.ui.showInfoSnackBar('snackbar.record_changed.success');
        }
      })
    }
  }

  gotoAuthors() {
    this.state.usedFacets.set([]);
    this.state.q = this.author().full;
    this.router.navigate(['/authors'], { queryParams: { s: this.state.encodeState() } });
    this.dialogRef.close(false);
  }
}
