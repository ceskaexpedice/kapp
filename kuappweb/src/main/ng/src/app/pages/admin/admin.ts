import { Component, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
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
import { MatTooltip, MatTooltipModule } from "@angular/material/tooltip";

@Component({
  selector: 'app-admin',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    MatTooltipModule
],
  templateUrl: './admin.html',
  styleUrl: './admin.scss'
})
export class Admin {
  readonly service = inject(AppService);
  readonly ui = inject(UIService);
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
      if (resp.error) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      } else {
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
        console.log(resp);
      }
    });
  }

  removeLabel(idx: number) {

    this.labels.splice(idx, 1);
    this.ui.showInfoSnackBar('snackbar.record_removed.success');
    // error
    //this.ui.showErrorSnackBar('snackbar.record_removed.error');
  }

  addLicense() {
    if (!this.licenses.find(l => l === this.new_license)) {
      this.licenses.push(this.new_license);
      this.new_license = null;
      //this.saveLicenses();
    }
  }

  removeLicense() {
    // todo
    // success
    this.ui.showInfoSnackBar('snackbar.record_removed.success');
    // error
    //this.ui.showErrorSnackBar('snackbar.record_removed.error');
  }

  saveLicenses() {
    this.state.licenses.set(this.licenses);
    this.service.saveLicenses(this.state.licenses()).subscribe(resp => {
      if (resp.error) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      } else {
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
      }
    });
  }

  addDK() {
    if (!this.licenses.find(l => l === this.new_license)) {
      this.licenses.push(this.new_license);
      this.new_license = null;
      //this.saveDK();
    }
  }

  saveDK() {
    this.state.digitalni_knihovny.set(this.dk);
    this.service.saveDK(this.state.digitalni_knihovny()).subscribe(resp => {
      if (resp.error) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
        console.log(resp);
      } else {
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
        console.log(resp);
      }
    });
  }

  removeDK() {
    // todo
    // success
    this.ui.showInfoSnackBar('snackbar.record_removed.success');
    // error
    //this.ui.showErrorSnackBar('snackbar.record_removed.error');
  }
}
