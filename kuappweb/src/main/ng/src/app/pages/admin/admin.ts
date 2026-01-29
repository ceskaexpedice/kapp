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
import { AppConfiguration } from '../../app-configuration';

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

  licenses:string[] = [];
  new_license: string;

  dk:string[] = [];
  new_dk: string;

  ngOnInit() {
    const p: any = {};
    this.labels = this.state.labels();
    this.licenses = [...this.state.licenses()];
    this.dk = [...this.state.digitalni_knihovny()];
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

  addLicense() {
    if (!this.licenses.find(l => l === this.new_license)) {
      this.licenses.push(this.new_license);
      this.new_license = null;
      //this.saveLicenses();
    }
  }

  saveLicenses() {
    this.state.licenses.set(this.licenses);
    this.service.saveLicenses(this.state.licenses()).subscribe(resp => {
        console.log(resp)
      });
  }

  addDK() {
    if (!this.licenses.find(l => l === this.new_license)) {
      this.licenses.push(this.new_license);
      this.new_license = null;
      //this.saveLicenses();
    }
  }

  saveDK() {
    this.state.digitalni_knihovny.set(this.dk);
    this.service.saveDK(this.state.digitalni_knihovny()).subscribe(resp => {
        console.log(resp)
      });
  }
}
