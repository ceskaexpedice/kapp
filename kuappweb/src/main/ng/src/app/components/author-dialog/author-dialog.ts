import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogContent, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { Author } from '../../shared/author';
import { TranslateModule } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { HttpParams } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBar } from "@angular/material/progress-bar";
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {MatButtonToggleModule} from '@angular/material/button-toggle';

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
  readonly dialogRef = inject(MatDialogRef<AuthorDialog>);
  readonly data = inject<Author>(MAT_DIALOG_DATA);

  
  links = signal<{[key: string]:{ident: string, url: string}[]}>({});
  linkTypes = signal<string[]>([]);
  loadingLinks = signal<boolean>(false);
  editing = signal<boolean>(false);

  ngOnInit() {
    if (this.data.nameIdentifier) {
        const p: any = {};
        p.id= this.data.nameIdentifier;
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

  save(): void {
    
  }

  setAsLast() {

  }
}
