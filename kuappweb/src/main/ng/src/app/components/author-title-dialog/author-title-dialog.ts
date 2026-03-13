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
  readonly service = inject(AppService);
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
    if (!this.data) {
      // Dialog to add new author
      // Show buttons
      this.isNew.set(true);
    } else {
      const a = this.data.authors_kapp?.find(a => a.id === this.data.id);
      if (a) {
        this.author.set(a);
        this.useKramerius = false;
        this.hasKapp.set(true);
        this.old_id = a.old_id;
      } else {
        this.author.set(this.data.authors_kramerius.find(a => a.id === this.data.id));
        this.useKramerius = true;
        this.hasKapp.set(false);
      }

      this.getLinks();
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
        if (a['datafield_100d']) {
          const p = a['datafield_100d'].split('-');
          a['date_birth'] = p[0];
          if (p.length > 1) {
            a['date_death'] = p[1];
          }

        }
        this.author.set({
          full: a['datafield_100a'],
          date: a['datafield_100d'],
          family: a['family'],
          given: a['given'],
          year_death: a['year_death'],
          role: null,
          nameIdentifier: id,
          id: id,
          isLast: false
        });
        this.getLinks();
        if (resp.error) {
          this.ui.showErrorSnackBar('snackbar.record_changed.error');
        } else {
          this.ui.showInfoSnackBar('snackbar.record_changed.success');
        }
      });
    }
  }

  onSave() {

    if (true || this.hasKapp && this.useKramerius) {
      const data: SimpleDialogData = {
        title: this.translator.instant('alert.saving_author_back'),
        message: this.translator.instant('alert.saving_author_back_desc'),
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
    } else {
      this.save();
    }


  }

  save(): void {
    console.log(this.author());
    const authors = this.data.authors_kapp ? [...this.data.authors_kapp] : [];
    const ka = authors.find(a => a.id === this.author().id);
    if (ka) {
      ka.role = this.author().role
    } else {
      authors.push(this.author())
    }

    this.service.saveAuthorsInTitle(authors, this.data.pid).subscribe((res: any) => {
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
}
