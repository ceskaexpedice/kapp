import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { AppState } from '../../app-state';

interface RoleRights {
  role: string;
  physicalLocations: string[];
}

@Component({
  selector: 'app-user-rights',
  imports: [
    MatCardModule,
    MatChipsModule,
    MatIconModule,
    TranslateModule
  ],
  templateUrl: './user-rights.html',
  styleUrl: './user-rights.scss'
})
export class UserRights {
  readonly state = inject(AppState);

  get libraryRoles(): RoleRights[] {
    const configuredLibraries = new Set([
      ...this.state.digitalni_knihovny(),
      ...this.state.libraryPhysicalLocations().map(mapping => mapping.library)
    ]);

    return this.state.roles()
      .filter(role => configuredLibraries.has(role))
      .sort((left, right) => left.localeCompare(right))
      .map(role => ({
        role,
        physicalLocations: [
          ...(this.state.libraryPhysicalLocations()
            .find(mapping => mapping.library === role)?.physicalLocations || [])
        ].sort((left, right) => left.localeCompare(right))
      }));
  }

  get otherRoles(): string[] {
    const libraryRoles = new Set(this.libraryRoles.map(item => item.role));
    return this.state.roles()
      .filter(role => role !== 'kappAdmin' && !libraryRoles.has(role))
      .sort((left, right) => left.localeCompare(right));
  }
}
