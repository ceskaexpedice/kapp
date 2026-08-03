import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatListModule } from '@angular/material/list';
import { TranslateModule } from '@ngx-translate/core';
import { FacetFields, JSONFacet, UsedFacet } from '../../shared/facet';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { AppState } from '../../app-state';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import {DateAdapter, MAT_DATE_LOCALE, provideNativeDateAdapter} from '@angular/material/core';
import { MatDatepickerIntl, MatDatepickerModule } from '@angular/material/datepicker';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-facets',
  imports: [TranslateModule, FormsModule, MatFormFieldModule,
    MatExpansionModule, MatListModule, MatInputModule, MatButtonModule,
    MatCheckboxModule, MatTooltipModule, MatIconModule, MatDatepickerModule
  ],
  providers: [
    provideNativeDateAdapter(),
    {provide: MAT_DATE_LOCALE, useValue: 'cs-CZ '}
  ],
  templateUrl: './facets.component.html',
  styleUrl: './facets.component.scss'
})
export class FacetsComponent {

  private router = inject(Router);
  private datePipe = inject(DatePipe);
  private readonly _adapter = inject<DateAdapter<unknown, unknown>>(DateAdapter);
  private readonly _intl = inject(MatDatepickerIntl);
  private readonly _locale = signal(inject<unknown>(MAT_DATE_LOCALE));

  facets = input<FacetFields>();
  fields = input<string[]>([]);
  hard_facets = input<string[]>([]);
  
  onFiltersChanged = output<UsedFacet[]>();
  onMouserOver = output<{field: string, value: string}>();
  onMouseOut = output<{field: string, value: string}>();

  hasUsedFacets: boolean;
  usedFacets: UsedFacet[] = [];

  yearFrom: number;
  yearTo: number;

  dateChangedFrom: Date;
  dateChangedTo: Date;

  modifiedChangedFrom: Date;
  modifiedChangedTo: Date;

  labelsNote = computed(() => {
    const ret: {[val: string]: string} = {};
    this.state.labels().forEach(l => {
      ret[l.name] = l.note;
    });
    return ret;
  });

  labelsByStorageName = computed(() => new Map(
    this.state.labels().map(label => [label.storageName || label.name, label])
  ));


  constructor(public state: AppState) {
    this._locale.set('cs');
    this._adapter.setLocale(this._locale());
    effect(() => {
      this.usedFacets = this.state.usedFacets();
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;

      if (this.facets()['date_issued_year']) {
        const diy: any = this.facets()['date_issued_year'];
        // this.yearFrom = diy.date_issued_year_min;
        // this.yearTo = diy.date_issued_year_max;
      }
    })
  }

  isHardUsed(field: string) {
    return this.usedFacets.some(f => f.field === field && !f.negated);
  }

  isHardNegated(field: string) {
    return this.usedFacets.some(f => f.field === field && f.negated);
  }

  filterHard(field: string) {
    const facet = this.usedFacets.find(f => f.field === field);
    if (!facet) {
      this.usedFacets.push({field, value: 'true'});
    } else if (!facet.negated) {
      facet.negated = true;
    } else {
      this.usedFacets = this.usedFacets.filter(f => !(f.field === field));
    }
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.update(f => [...this.usedFacets]);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  isUsed(field: string, value: string) {
    return this.usedFacets.some(f => f.field === field && f.value === value && !f.negated);
  }

  isNegated(field: string, value: string) {
    return this.usedFacets.some(f => f.field === field && f.value === value && f.negated);
  }

  facetBuckets(field: string): JSONFacet[] {
    let buckets = this.facets()[field]?.buckets ?? [];
    if (field === 'labels') {
      const visibleLabels = new Set(this.state.labels().map(label => label.storageName || label.name));
      buckets = buckets.filter(bucket => visibleLabels.has(bucket.val));
    }
    const missingNegated = this.usedFacets
      .filter(f => f.field === field && f.negated && !buckets.some(b => b.val === f.value))
      .map(f => ({val: f.value, count: 0}));
    return [...missingNegated, ...buckets];
  }

  clearYear() {
    this.usedFacets = this.usedFacets.filter(f => !(f.field === 'date_issued_year'));
    this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
    this.state.usedFacets.set(this.usedFacets);
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  filterStateDate() {
    //const uf = this.usedFacets.find(f => f.field === 'state_date');
      const value = this.datePipe.transform(this.dateChangedFrom, 'yyyy-MM-dd') + ',' + this.datePipe.transform(this.dateChangedTo, 'yyyy-MM-dd') ;
      this.usedFacets = this.usedFacets.filter(f => !(f.field === 'state_date'));
      this.usedFacets.push({field: 'state_date', value});
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
      this.state.usedFacets.update(f => [...this.usedFacets]);
      //console.log(this.usedFacets)
      this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
  }

  filterModifiedDate() {
      const value = this.datePipe.transform(this.modifiedChangedFrom, 'yyyy-MM-dd') + ',' + this.datePipe.transform(this.modifiedChangedTo, 'yyyy-MM-dd') ;
      this.usedFacets = this.usedFacets.filter(f => !(f.field === 'last_modified_date'));
      this.usedFacets.push({field: 'last_modified_date', value});
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
      this.state.usedFacets.update(f => [...this.usedFacets]);
      //console.log(this.usedFacets)
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
  }
  
  filter(field: string, value: string) {
    const uf = this.usedFacets.find(f => f.field === field && f.value === value);
    if (!uf) {
      this.usedFacets.push({field, value});
    } else if (!uf.negated) {
      uf.negated = true;
    } else {
      this.usedFacets = this.usedFacets.filter(f => !(f.field === field && f.value === value));
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
