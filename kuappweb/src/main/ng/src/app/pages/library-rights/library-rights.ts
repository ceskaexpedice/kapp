import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { concatMap, map, of } from 'rxjs';

import { AppService } from '../../app.service';
import { AppState } from '../../app-state';
import { LibraryPhysicalLocations, PhysicalLocationFacet } from '../../shared/library-rights';
import { UIService } from '../../ui.service';

@Component({
  selector: 'app-library-rights',
  imports: [
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
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
  mappings: LibraryPhysicalLocations[] = [];
  physicalLocations: PhysicalLocationFacet[] = [];
  selectedLibrary: string | null = null;
  newLibrary = '';
  locationFilter = '';
  loadingLocations = false;
  saving = false;

  ngOnInit(): void {
    this.libraries = [...this.state.digitalni_knihovny()];
    const storedMappings = this.state.libraryPhysicalLocations();
    this.mappings = this.libraries.map(library => ({
      library,
      physicalLocations: [...(storedMappings.find(mapping => mapping.library === library)?.physicalLocations || [])]
    }));
    this.selectedLibrary = this.libraries[0] || null;
    this.loadPhysicalLocations();
  }

  get filteredPhysicalLocations(): PhysicalLocationFacet[] {
    const filter = this.locationFilter.trim().toLocaleLowerCase();
    return filter
      ? this.physicalLocations.filter(location => location.val.toLocaleLowerCase().includes(filter))
      : this.physicalLocations;
  }

  get selectedMapping(): LibraryPhysicalLocations | undefined {
    return this.mappings.find(mapping => mapping.library === this.selectedLibrary);
  }

  selectLibrary(library: string): void {
    this.selectedLibrary = library;
    this.locationFilter = '';
  }

  addLibrary(): void {
    const library = this.newLibrary.trim();
    if (library && !this.libraries.includes(library)) {
      this.libraries.push(library);
      this.mappings.push({library, physicalLocations: []});
      this.selectedLibrary = library;
      this.newLibrary = '';
    }
  }

  removeLibrary(index: number): void {
    const [removedLibrary] = this.libraries.splice(index, 1);
    this.mappings = this.mappings.filter(mapping => mapping.library !== removedLibrary);
    if (this.selectedLibrary === removedLibrary) {
      this.selectedLibrary = this.libraries[0] || null;
    }
  }

  isAssigned(location: string): boolean {
    return this.selectedMapping?.physicalLocations.includes(location) || false;
  }

  toggleLocation(location: string, checked: boolean): void {
    const mapping = this.selectedMapping;
    if (!mapping) {
      return;
    }
    mapping.physicalLocations = checked
      ? [...new Set([...mapping.physicalLocations, location])].sort()
      : mapping.physicalLocations.filter(value => value !== location);
  }

  save(): void {
    this.saving = true;
    this.service.saveDK(this.libraries).pipe(
      concatMap(libraries => {
        if (this.failed(libraries)) {
          return of({libraries, mappings: null});
        }
        return this.service.saveLibraryRights(this.mappings).pipe(
          map(mappings => ({libraries, mappings}))
        );
      })
    ).subscribe({
      next: (response: any) => {
        this.saving = false;
        if (this.failed(response.libraries) || this.failed(response.mappings)) {
          this.ui.showErrorSnackBar('snackbar.record_saved.error');
          return;
        }
        this.state.digitalni_knihovny.set([...this.libraries]);
        this.state.libraryPhysicalLocations.set(this.mappings.map(mapping => ({
          library: mapping.library,
          physicalLocations: [...mapping.physicalLocations]
        })));
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
      },
      error: () => {
        this.saving = false;
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      }
    });
  }

  private loadPhysicalLocations(): void {
    this.loadingLocations = true;
    this.service.getPhysicalLocations().subscribe((response: any) => {
      this.loadingLocations = false;
      this.physicalLocations = [...(response.buckets || [])]
        .sort((a: PhysicalLocationFacet, b: PhysicalLocationFacet) => a.val.localeCompare(b.val));
    });
  }

  private failed(response: any): boolean {
    return !!response?.error || response?.response?.status >= 400;
  }
}
