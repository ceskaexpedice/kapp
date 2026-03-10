import { HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { MatChipEditedEvent, MatChipInput, MatChipInputEvent, MatChipsModule } from '@angular/material/chips';
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
  readonly dialogRef = inject(MatDialogRef<LabelsDialog>);
  readonly state = inject(AppState);
  loading = signal<boolean>(false);
  labels: Label[];

  ngOnInit() {
    const p: any = {};
    this.labels = this.state.labels();
  }

  save() {
    this.state.labels.set(this.labels);
    this.service.saveLabels(this.state.labels()).subscribe(resp => {
      console.log(resp)
    });
  }
}
