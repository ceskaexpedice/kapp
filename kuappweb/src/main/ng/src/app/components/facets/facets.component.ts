import { Component, effect, inject, input, output } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { TranslateModule } from '@ngx-translate/core';
import { FacetFields, JSONFacet } from '../../shared/facet';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { AppState } from '../../app-state';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import {provideNativeDateAdapter} from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';

@Component({
  selector: 'app-facets',
  imports: [TranslateModule, FormsModule, MatFormFieldModule,
    MatExpansionModule, MatListModule, MatInputModule, MatButtonModule,
    MatCheckboxModule, MatTooltipModule, MatIconModule, MatDatepickerModule
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './facets.component.html',
  styleUrl: './facets.component.scss'
})
export class FacetsComponent {

  private router = inject(Router);

  facets = input<FacetFields>();
  fields = input<string[]>([]);
  hard_facets = input<string[]>([]);
  
  onFiltersChanged = output<{field: string, value: string}[]>();
  onMouserOver = output<{field: string, value: string}>();
  onMouseOut = output<{field: string, value: string}>();

  hasUsedFacets: boolean;
  usedFacets: {field: string, value: string}[] = [];

  yearFrom: number;
  yearTo: number;

  constructor(public state: AppState) {
    effect(() => {
      this.usedFacets = this.state.usedFacets();
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;

      if (this.facets()['date_issued_year']) {
        const diy: any = this.facets()['date_issued_year'];
        this.yearFrom = diy.date_issued_year_min;
        this.yearTo = diy.date_issued_year_max;
      }
    })
  }

  isHardUsed(field: string) {
    return this.usedFacets.findIndex(f => f.field === field) > -1;
  }

  filterHard(field: string, value: any) {
    if (value.checked) {
      this.usedFacets.push({field, value: 'true'});
    } else {
      this.usedFacets = this.usedFacets.filter(f => !(f.field === field));
    }
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.update(f => [...this.usedFacets]);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  isUsed(field: string, value: string) {
    return this.usedFacets.findIndex(f => f.field === field && f.value === value) > -1;
  }

  clearYear() {
    this.usedFacets = this.usedFacets.filter(f => !(f.field === 'date_issued_year'));
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.set(this.usedFacets);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  filterYear() {
    const uf = this.usedFacets.find(f => f.field === 'date_issued_year');
      const value = this.yearFrom + ',' + this.yearTo;
      this.usedFacets = this.usedFacets.filter(f => !(f.field === 'date_issued_year'));
      this.usedFacets.push({field: 'date_issued_year', value});
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
      this.state.usedFacets.update(f => [...this.usedFacets]);
      this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
      this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }
  
  filter(field: string, value: string) {
    const uf = this.usedFacets.find(f => f.field === field && f.value === value);
    if (uf) {
      this.usedFacets = this.usedFacets.filter(f => !(f.field === field && f.value === value));
    } else {
      this.usedFacets.push({field, value});
    }
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.update(f => [...this.usedFacets]);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  unfilter(field: string, value: string) {
    this.usedFacets = this.usedFacets.filter(f => !(f.field === field && f.value === value));
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.set(this.usedFacets);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  fireMouserOver(field: string, value: string) {
    this.onMouserOver.emit({field, value});
  }

  fireMouseOut(field: string, value: string) {
    this.onMouseOut.emit({field, value});
  }
}
