import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { ResultItem } from "../result-item/result-item";

@Component({
  selector: 'app-title-dialog',
  imports: [
    TranslateModule,
    MatDialogModule, MatIconModule, MatButtonModule,
    ResultItem
],
  templateUrl: './title-dialog.html',
  styleUrl: './title-dialog.scss'
})
export class TitleDialog {
  readonly doc = inject<any>(MAT_DIALOG_DATA);
}
