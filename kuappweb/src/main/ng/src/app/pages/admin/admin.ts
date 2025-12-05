import { Component, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { MatInputModule } from "@angular/material/input";
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule } from '@ngx-translate/core';
import { MatCardModule } from '@angular/material/card';
import { Label } from '../../shared/label';

@Component({
  selector: 'app-admin',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule],
  templateUrl: './admin.html',
  styleUrl: './admin.scss'
})
export class Admin {
  readonly service = inject(AppService);
  readonly state = inject(AppState);
  loading = signal<boolean>(false);
  labels: Label[];
  nl: Label = {name: ''};

  ngOnInit() {
    const p: any = {};
    this.labels = this.state.labels();
  }

  addLabel(l: Label) {
    if (!this.labels.find(la => la.name === l.name)) {
      this.labels.push({name: l.name, color: l.color});
      this.nl = {name: ''};
      this.saveLabels();
    }
  }


  saveLabels() {
    this.state.labels.set(this.labels);
    this.service.saveLabels(this.state.labels()).subscribe(resp => {
      console.log(resp)
    });
  }
}
