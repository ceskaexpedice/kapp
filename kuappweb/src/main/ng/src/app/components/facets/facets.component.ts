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

@Component({
  selector: 'app-facets',
  imports: [TranslateModule, MatExpansionModule, MatListModule,
    MatCheckboxModule, MatTooltipModule, MatIconModule
  ],
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

  constructor(public state: AppState) {
    effect(() => {
      this.usedFacets = this.state.usedFacets();
      this.hasUsedFacets = this.usedFacets.filter(item => !this.hard_facets().includes(item.field)).length > 0;
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
