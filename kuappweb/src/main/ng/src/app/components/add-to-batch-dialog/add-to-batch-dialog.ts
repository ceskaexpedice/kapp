import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-add-to-batch-dialog',
  imports: [
    TranslateModule, MatDialogModule, FormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule
  ],
  templateUrl: './add-to-batch-dialog.html',
  styleUrl: './add-to-batch-dialog.scss'
})
export class AddToBatchDialog {

}
