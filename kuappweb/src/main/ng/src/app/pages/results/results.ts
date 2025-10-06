import { DatePipe } from '@angular/common';
import { Component, DOCUMENT, Inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AngularSplitModule } from 'angular-split';
import { FacetsComponent } from '../../components/facets/facets.component';
import { Router } from '@angular/router';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { JSONFacet } from '../../shared/facet';
import { HttpParams } from '@angular/common/http';
import { ResultItem } from "../../components/result-item/result-item";
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-results',
  imports: [
    TranslateModule, FormsModule,
    AngularSplitModule,
    MatProgressBarModule, MatSelectModule,
    MatButtonModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatIconModule, MatInputModule,
    MatListModule, MatCheckboxModule, MatRadioModule, MatTooltipModule, FacetsComponent,
    ResultItem
  ],
  templateUrl: './results.html',
  styleUrl: './results.scss'
})
export class Results {

  loading: boolean;
  solrResponse: any;
  docs = signal<any[]>([]);
  hasFacets = signal<boolean>(false);

  rows = 100;
  numFound: number = 0;
  pageIndex = 0;
  pageSizeOptions = [10, 25, 100];

  constructor(
    @Inject(DOCUMENT) private document: Document,
    private router: Router,
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) {

  }

  ngOnInit(): void {
    this.getData();
  }

  usedFacets: { field: string, value: string }[] = [];
  onFiltersChanged(usedFacets: { field: string, value: string }[]) {
    this.usedFacets = usedFacets;
    this.getData();
  }

  handlePageEvent(e: PageEvent) {
    this.rows = e.pageSize;
    this.pageIndex = e.pageIndex;
    this.getData();
  }

  addFilters(p: any, usedFacets: { field: string, value: string }[]) {
    p.author = usedFacets.filter(k => k.field === 'authors').map(k => k.value);
    p.state = usedFacets.filter(k => k.field === 'state').map(k => k.value);
    p.labels = usedFacets.filter(k => k.field === 'labels').map(k => k.value);
    p.physical_locations = usedFacets.filter(k => k.field === 'physical_locations').map(k => k.value);
  }


  doSearch() {
    this.getData();
  }

  getData() {
    const p: any = {};
    p.rows = this.rows;
    p.offset = this.pageIndex * p.rows;
    if (this.state.q) {
      p.q = this.state.q;
    }
    this.addFilters(p, this.usedFacets);
    this.service.search(p as HttpParams).subscribe((resp: any) => {
      this.solrResponse = resp;
      this.numFound = this.solrResponse.response.numFound;
      this.docs.set(this.solrResponse.response.docs);
    });
  }

}
