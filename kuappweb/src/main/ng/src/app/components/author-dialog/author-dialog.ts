import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogContent, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import { Author } from '../../shared/author';
import { TranslateModule } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { HttpParams } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-author-dialog',
  imports: [
    TranslateModule,
    MatDialogModule, MatIconModule, MatButtonModule
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

  ngOnInit() {
    if (this.data.nameIdentifier) {
        const p: any = {};
        p.id= this.data.nameIdentifier;
        this.service.getLinks(p as HttpParams).subscribe((resp: any) => {
          console.log(resp)
          if (resp.links) {
            this.links.set(resp.links);
            this.linkTypes.set(Object.keys(resp.links));
          }
        });
      }
  }

  save(): void {
    
  }

  setAsLast() {

  }
}
