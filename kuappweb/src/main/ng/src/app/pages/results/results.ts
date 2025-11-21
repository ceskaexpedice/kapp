import { DatePipe } from '@angular/common';
import { Component, DOCUMENT, inject, Inject, signal } from '@angular/core';
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
import { ActivatedRoute, ActivatedRouteSnapshot, Router, RouterModule } from '@angular/router';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { JSONFacet } from '../../shared/facet';
import { HttpParams } from '@angular/common/http';
import { ResultItem } from "../../components/result-item/result-item";
import { MatInputModule } from '@angular/material/input';
import {MatButtonToggleModule} from '@angular/material/button-toggle';
import { AuthorItem } from "../../components/author-item/author-item";
import { Observable } from 'rxjs';
import { TitleDialog } from '../../components/title-dialog/title-dialog';
import { MatDialog } from '@angular/material/dialog';

@Component({
  selector: 'app-results',
  imports: [
    TranslateModule, FormsModule,
    AngularSplitModule,
    MatProgressBarModule, MatSelectModule,
    MatButtonModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatButtonToggleModule,
    MatListModule, MatCheckboxModule, MatRadioModule, MatTooltipModule, FacetsComponent,
    ResultItem,
    AuthorItem
  ],
  templateUrl: './results.html',
  styleUrl: './results.scss'
})
export class Results {

  loading: boolean;
  solrResponse: any;
  docs = signal<any[]>([]);
  hasFacets = signal<boolean>(false);
  view = signal<string>('titles');

  rows = 25;
  numFound: number = 0;
  pageIndex = 0;
  pageSizeOptions = [10, 25, 100];

  private router = inject(Router);
    readonly dialog = inject(MatDialog);

  viewType = signal<string>('table_view');
  displayedColumns = ['pid', 'title', 'state'];

  constructor(
    @Inject(DOCUMENT) private document: Document,
    
    public state: AppState,
    private service: AppService,
    private config: AppConfiguration
  ) {

  }

  ngOnInit(): void {
    this.view.set(this.router.isActive('authors', { paths: 'exact', queryParams: 'exact', fragment: 'ignored', matrixParams: 'ignored' }) ? 'authors' : 'titles');
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

  clearQ() {
    this.state.q = '';
    this.getData();
  }

  doSearch() {
    const params = {
      q: this.state.q
    }
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge' });
    this.getData();
  }

  getData() {
    this.docs.set([]);
    const p: any = {};
    p.rows = this.rows;
    p.offset = this.pageIndex * p.rows;
    if (this.state.q) {
      p.q = this.state.q;
    }
    this.addFilters(p, this.usedFacets);
    let s: Observable<any>;
    if (this.view() === 'titles') {
      s = this.service.searchTitles(p as HttpParams);
    } else {
      s = this.service.searchAuthors(p as HttpParams);
    }
    s.subscribe((resp: any) => {
      this.solrResponse = resp;
      this.numFound = this.solrResponse.response.numFound;
      this.docs.set(this.solrResponse.response.docs);
    });
  }

  expanded(pid: string) {
    // console.log(this.solrResponse.expanded[pid])
    return this.solrResponse.expanded[pid];
  }

  viewDoc(doc: any) {
  
      const dialogRef = this.dialog.open(TitleDialog, {
        data: doc,
      });
  
      dialogRef.afterClosed().subscribe(result => {
        if (result) {
        }
      });
    }

}
