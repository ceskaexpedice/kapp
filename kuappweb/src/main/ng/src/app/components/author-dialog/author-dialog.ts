import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogContent, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { Author } from '../../shared/author';
import { TranslateModule } from '@ngx-translate/core';
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

@Component({
  selector: 'app-author-dialog',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatButtonToggleModule,
    MatProgressBar, MatFormFieldModule, MatInputModule
  ],
  templateUrl: './author-dialog.html',
  styleUrl: './author-dialog.scss'
})
export class AuthorDialog {

  readonly service = inject(AppService);
  readonly ui = inject(UIService);
  readonly dialogRef = inject(MatDialogRef<AuthorDialog>);
  readonly data = inject<{pid: string, author: Author}>(MAT_DIALOG_DATA);


  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);
  loadingLinks = signal<boolean>(false);
  editing = signal<boolean>(false);
  isNew = signal<boolean>(false);

  author = signal<Author>(null);

  hasChanges: boolean;

  ngOnInit() {
    if (!this.data) {
      // Dialog to add new author
      // Show buttons
      this.isNew.set(true);
    } else {
      this.author.set(this.data.author);
      this.getLinks();
    }
  }

  getLinks() {
    if (this.author().nameIdentifier) {
      const p: any = {};
      p.id = this.author().nameIdentifier;
      this.loadingLinks.set(true);
      this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
        this.loadingLinks.set(false);
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
      this.service.getAuthorById(p as HttpParams).subscribe((resp: any) => {
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
          'date_range_end.year': a['date_range_end.year'],
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

  save(): void {
        console.log(this.author());
      this.service.saveAuthor(this.author()).subscribe((res: any) => {
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
    if (this.author()['date_range_end.year']) {
      this.service.saveDecisionDate(this.data.pid, this.author()['date_range_end.year']).subscribe((res: any) => {
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
