import { HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { AuthorDialog } from '../author-dialog/author-dialog';
import {MatChipEditedEvent, MatChipInput, MatChipInputEvent, MatChipsModule} from '@angular/material/chips';
import { AppState } from '../../app-state';
import { Label } from '../../shared/label';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';


@Component({
  selector: 'app-labels-dialog',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule,
    MatProgressBar, MatChipsModule, MatFormFieldModule, MatInputModule

  ],
  templateUrl: './labels-dialog.html',
  styleUrl: './labels-dialog.scss'
})
export class LabelsDialog {
  readonly service = inject(AppService);
  readonly dialogRef = inject(MatDialogRef<AuthorDialog>);
  readonly state = inject(AppState);
  loading = signal<boolean>(false);

  ngOnInit() {
    const p: any = {};
    
  }

  edit(l: Label) {
console.log('A')
  }

  editColor(e: any, l: Label) {
console.log('B')
e.stopPropagation();

  }

  save() {

  }
}
