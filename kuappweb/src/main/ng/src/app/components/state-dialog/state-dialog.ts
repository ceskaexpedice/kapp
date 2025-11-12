import { Component, inject, signal } from '@angular/core';
import { AppConfiguration } from '../../app-configuration';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import {MatSelectModule} from '@angular/material/select';

@Component({
  selector: 'app-state-dialog',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatSelectModule,
    MatProgressBar, MatFormFieldModule, MatInputModule

  ],
  templateUrl: './state-dialog.html',
  styleUrl: './state-dialog.scss'
})
export class StateDialog {

  readonly service = inject(AppService);
  readonly dialogRef = inject(MatDialogRef<StateDialog>);
  readonly state = inject(AppState);
  readonly config = inject(AppConfiguration);
  loading = signal<boolean>(false);

  save() {

  }
}
