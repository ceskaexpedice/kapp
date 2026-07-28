import { Component, inject, signal } from '@angular/core';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
import { MatInputModule } from "@angular/material/input";
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { MatCardModule } from '@angular/material/card';
import { Label } from '../../shared/label';
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatSelectModule } from '@angular/material/select';

interface LabelScopeOption {
  id: string;
  visibility: 'everyone' | 'private' | 'library';
  library?: string;
  owner?: string;
}

@Component({
  selector: 'app-admin',
  imports: [
    TranslateModule, FormsModule,
    MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    MatTooltipModule, MatSelectModule
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
  labelScope = 'labels';
  labelScopes: LabelScopeOption[] = [];

  licenses:string[] = [];
  new_license: string;

  ngOnInit() {
    const p: any = {};
    this.labelScopes = [{id: 'labels', visibility: 'everyone'}];
    const username = this.state.user()?.preferred_username;
    if (username) {
      this.labelScopes.push({id: `labels_${username}`, visibility: 'private', owner: username});
    }
    const roles = new Set(this.state.roles());
    this.state.digitalni_knihovny()
      .filter(library => this.state.hasRole('kappAdmin') || roles.has(library))
      .forEach(library => this.labelScopes.push({
        id: `labels_${library}`,
        visibility: 'library',
        library,
        owner: library
      }));
    this.selectLabelScope();
    this.licenses = [...this.state.licenses()];
  }

  addLabel(l: Label) {
    if (!this.labels.find(la => la.name === l.name)) {
      this.labels.push({name: l.name, color: l.color});
      this.nl = {name: ''};
      this.saveLabels();
    }
  }

  saveLabels() {
    const scope = this.labelScopes.find(option => option.id === this.labelScope);
    if (!scope) {
      return;
    }
    this.service.saveLabels(this.labels, scope.visibility, scope.library).subscribe(resp => {
      if (resp.error) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      } else {
        this.state.setLabelSet(scope.id, this.labels.map(label => ({
          ...label,
          storageName: scope.visibility === 'everyone' ? label.name : `${label.name}_${scope.owner}`,
          visibility: scope.visibility,
          owner: scope.owner
        })));
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
        console.log(resp);
      }
    });
  }

  selectLabelScope() {
    this.labels = (this.state.labelSets()[this.labelScope] || []).map(label => ({...label}));
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

}
