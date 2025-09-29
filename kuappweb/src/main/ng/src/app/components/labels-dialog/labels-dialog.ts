import { HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import { AppService } from '../../app.service';
import { AuthorDialog } from '../author-dialog/author-dialog';

@Component({
  selector: 'app-labels-dialog',
  imports: [
    TranslateModule,
    MatDialogModule, MatIconModule, MatButtonModule,
    MatProgressBar
  ],
  templateUrl: './labels-dialog.html',
  styleUrl: './labels-dialog.scss'
})
export class LabelsDialog {
  readonly service = inject(AppService);
  readonly dialogRef = inject(MatDialogRef<AuthorDialog>);
  labels = signal<{ name: string, color: string, text_color: string }[]>([]);
  loading = signal<boolean>(true);

  ngOnInit() {
    const p: any = {};
    this.service.getLabels().subscribe((resp: any) => {
      this.loading.set(false);
      const labelsC: { name: string, color: string, text_color: string }[] = resp.value;
      labelsC.forEach(l => {
        // const r = parseInt(l.color.substring(1,2), 16);
        // const g = parseInt(l.color.substring(3,4), 16);
        // const b = parseInt(l.color.substring(5,6), 16);
        const c = parseInt(l.color.substring(1), 16);
        l.text_color = c > 128 ? '#000' : '#fff';
      });
      this.labels.set(labelsC);
    });
  }

  save() {

  }
}
