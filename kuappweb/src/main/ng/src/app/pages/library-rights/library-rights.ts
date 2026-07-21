import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

import { AppService } from '../../app.service';
import { AppState } from '../../app-state';
import { UIService } from '../../ui.service';

@Component({
  selector: 'app-library-rights',
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './library-rights.html',
  styleUrl: './library-rights.scss'
})
export class LibraryRights {
  readonly service = inject(AppService);
  readonly state = inject(AppState);
  readonly ui = inject(UIService);

  libraries: string[] = [];
  newLibrary = '';

  ngOnInit(): void {
    this.libraries = [...this.state.digitalni_knihovny()];
  }

  addLibrary(): void {
    const library = this.newLibrary.trim();
    if (library && !this.libraries.includes(library)) {
      this.libraries.push(library);
      this.newLibrary = '';
    }
  }

  removeLibrary(index: number): void {
    this.libraries.splice(index, 1);
  }

  save(): void {
    this.service.saveDK(this.libraries).subscribe((response: any) => {
      if (response.error || response.response?.status === 403) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
        return;
      }
      this.state.digitalni_knihovny.set([...this.libraries]);
      this.ui.showInfoSnackBar('snackbar.record_saved.success');
    });
  }
}
