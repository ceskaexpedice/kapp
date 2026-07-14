import { DatePipe, LowerCasePipe, NgClass } from '@angular/common';
import { Component, computed, DOCUMENT, effect, inject, Inject, signal } from '@angular/core';
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
import { ActivatedRoute, Router } from '@angular/router';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
import { HttpParams } from '@angular/common/http';
import { ResultItem } from "../../components/result-item/result-item";
import { MatInputModule } from '@angular/material/input';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { AuthorItem } from "../../components/author-item/author-item";
import { Observable, switchMap } from 'rxjs';
import { TitleDialog } from '../../components/title-dialog/title-dialog';
import { MatDialog } from '@angular/material/dialog';
import { ResizecolDirective } from '../../shared/resizecol.directive';

import { MatSortModule, Sort } from '@angular/material/sort';
import { ColumnsSettingsDialog } from '../../components/columns-settings-dialog/columns-settings-dialog';
import { MatMenuModule } from "@angular/material/menu";

import { Batch } from '../../shared/batch';
import { StateDialog } from '../../components/state-dialog/state-dialog';
import { SimpleDialogComponent } from '../../components/simple-dialog/simple-dialog.component';
import { SimpleDialogData } from '../../components/simple-dialog/simple-dialog';
import { Contract } from '../../shared/contract';
import { TranslateService } from '@ngx-translate/core';
import { AuthorTitle } from '../../shared/author';
import { UsedFacet } from '../../shared/facet';
import { ReleaseYearDisplayPipe } from '../../shared/release-year-display.pipe';

@Component({
  selector: 'app-results',
  imports: [
    TranslateModule, FormsModule, DatePipe,
    AngularSplitModule, ResizecolDirective,
    MatProgressBarModule, MatSelectModule, MatSortModule,
    MatButtonModule, MatTableModule, MatPaginatorModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatButtonToggleModule,
    MatListModule, MatCheckboxModule, MatRadioModule, MatTooltipModule, FacetsComponent,
    ResultItem, AuthorItem, MatMenuModule, LowerCasePipe,
    NgClass, ReleaseYearDisplayPipe
  ],
  templateUrl: './results.html',
  styleUrl: './results.scss'
})
export class Results {
  loading = signal(false);
  solrResponse: any;
  docs = signal<any[]>([]);
  numTitles: { [pid: string]: number } = {};
  contracts = signal<any[]>([]);
  hasFacets = signal<boolean>(false);
  view = signal<string>('titles');

  rows = 25;
  numFound: number = 0;
  pageIndex = 0;
  pageSizeOptions = [10, 25, 100];

  readonly route = inject(ActivatedRoute);
  private router = inject(Router);
  readonly dialog = inject(MatDialog);

  viewType = signal<string>('table_view');
  displayedColumns = computed<string[]>(() => 
    this.state.user() ?
    ['selection', ...this.state.titleColumns().filter(c => c.selected).map(f => f.field), 'action'] :
    [...this.state.titleColumns().filter(c => c.selected).map(f => f.field), 'action']
);
  columnSizes: { [field: string]: number } = {};

  columnsToDisplayWithExpand = computed<string[]>(() => [...this.displayedColumns()]);
  expandedElement: any | null = null;

  //['pid', 'title', 'state', 'date_issued', 'sigla', 'indextime', 'actions']; //Datum vydání; Producent/Umístění (duplicitně k fasetě); Datum importu(?)

  sort: Sort = { active: 'indextime', direction: 'desc' };

  constructor(
    @Inject(DOCUMENT) private document: Document,

    public state: AppState,
    public ui: UIService,
    private translator: TranslateService,
    private service: AppService,
    private config: AppConfiguration
  ) {
    effect(() => {
      this.state.titleColumns().forEach(c => {
        this.columnSizes[c.field] = c.width ? c.width : 100;
      })
      //this.getData();
    })
  }

  ngOnInit(): void {
    this.view.set(this.router.isActive('authors', { paths: 'exact', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored' }) ? 'authors' : 'titles');
    const s = this.route.queryParams.subscribe((p: any) => {
      this.view.set(this.router.isActive('authors', { paths: 'exact', queryParams: 'ignored', fragment: 'ignored', matrixParams: 'ignored' }) ? 'authors' : 'titles');
      this.getData();
    });
    this.getContracts();
  }

  handlePageEvent(e: PageEvent) {
    this.rows = e.pageSize;
    this.pageIndex = e.pageIndex;
    this.getData();
  }

  addFilters(p: any, usedFacets: UsedFacet[]) {
    usedFacets.forEach(facet => {
      const field = facet.field === 'authors' ? 'author' : facet.field;
      const parameter = facet.negated ? `${field}_not` : field;
      p[parameter] ??= [];
      p[parameter].push(facet.value);
    });
  }

  clearQ() {
    this.state.q = '';
    this.getData();
  }

  doSearch() {
    // const params = {
    //   q: this.state.q
    // }
    // this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge' });
    this.router.navigate([], { queryParams: { s: this.state.encodeState() } });
    this.getData(false);
  }

  export() {
    this.getData(true);
  }

  getContracts() {
    this.contracts.set([]);
    const p: any = {};
    p.rows = 1000;
    p.sort = 'filename asc';

    this.service.searchContracts(p as HttpParams).subscribe((resp: any) => {
      this.contracts.set(resp.response.docs);
    });
  }

  getData(csv: boolean = false) {

    this.loading.set(true);
    const p: any = {};
    p.rows = this.rows;
    p.offset = this.pageIndex * p.rows;
    if (this.state.q) {
      p.q = this.state.q;
    }
    p.sort = this.sort.active + " " + this.sort.direction;
    if (csv) {
      p.export = true;
      p.fl = this.displayedColumns();
    } else {
      this.docs.set([]);
    }
    this.addFilters(p, this.state.usedFacets());
    let s: Observable<any>;
    if (this.view() === 'titles') {
      s = this.service.searchTitles(p as HttpParams, csv);
    } else {
      s = this.service.searchAuthors(p as HttpParams);
    }
    s.subscribe((resp: any) => {

      this.loading.set(false);
      if (csv) {
        this.downLoadFile(resp)
        return;
      }
      this.solrResponse = resp;
      this.numFound = this.solrResponse.response.numFound;
      this.docs.set(this.solrResponse.response.docs);

    });
  }

  mergedAuthors(d: any) {
    let mergedAuthors: AuthorTitle[] = [];
    if (d['authors_kapp']) {
      mergedAuthors = [...d['authors_kapp']];
    }
    if (d['authors_kramerius']) {
      d['authors_kramerius'].forEach((ak: AuthorTitle) => {
        if (!mergedAuthors.find((ma: any) => ak.id && ak.id === ma.old_id) &&
          !mergedAuthors.find((ma: any) => ak.id && ak.id === ma.id) &&
          !mergedAuthors.find((ma: any) => ak.nameIdentifier && ak.nameIdentifier === ma.nameIdentifier) &&
          !mergedAuthors.find((ma: any) => ak.nameIdentifier && ak.nameIdentifier === ma.old_id)) {
          mergedAuthors.push(ak);
        }

      });
    }



    return mergedAuthors;
  }

  // searchTitlesByAuthors() {
  //   const p: any = {};
  //   p.rows = 0;
  //   p.author_id = this.docs().map(d => d.id);
  //   p.only_pid = true;
  //   this.service.searchTitlesByAuthor(p as HttpParams).subscribe((resp: any) => {

  //     this.numTitles.set(resp.response.numFound);
  //   });

  // }

  downLoadFile(data: any) {
    let blob = new Blob([data], { type: 'text/csv;charset=utf-8' });
    let url = window.URL.createObjectURL(blob);
    let pwa = window.open(url);
    if (!pwa || pwa.closed || typeof pwa.closed == 'undefined') {
      alert('Please disable your Pop-up blocker and try again.');
    }
  }

  expanded(pid: string) {
    return this.solrResponse.expanded[pid];
  }

  viewDoc(doc: any) {

    const dialogRef = this.dialog.open(TitleDialog, {
      data: doc,
      panelClass: "app-dialog-title"
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
      }
    });
  }

  sortTable(e: Sort) {
    this.sort = e;
    this.getData(false);
  }

  setColumns() {
    const dialogRef = this.dialog.open(ColumnsSettingsDialog, {
      width: '600px'
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        console.log(result);
        // this.displayedColumns = this.state.titleColumns().filter(c => c.selected).map(f => f.field)
      }
    });
  }


  // addAuthor(doc: any) {

  //   const data: AuthorTitleDialogData = {
  //     pid: doc.pid,
  //     authors_kapp: doc.authors_kapp,
  //     authors_kramerius: doc.authors_kramerius,
  //     id: null,
  //     ids: doc.authors_ids,
  //     nameIdentifiers: doc.nameIdentifiers
  //   }
  //   const dialogRef = this.dialog.open(AuthorTitleDialog, {
  //     data: data,
  //   });

  //   dialogRef.afterClosed().subscribe(result => {
  //     if (result) {
  //       this.service.getDoc(doc.pid).subscribe((resp: any) => {
  //         if (resp.response?.docs?.length > 0) {
  //           //this.doc.set(resp.response.docs[0]);
  //         }
  //       });
  //     }
  //   });
  // }

  setState(doc: any) {

    if (doc.is_hierarchical || !this.state.user()) {
      return;
    }

    const dialogRef = this.dialog.open(StateDialog, {
      data: { isBatch: false, doc: doc },
      panelClass: "app-dialog-state"
    });

    dialogRef.afterClosed().subscribe(result => {
      console.log(result)
      if (result) {
        this.service.getDoc(doc.pid).subscribe((resp: any) => {
          if (resp.response?.docs?.length > 0) {
            doc = resp.response.docs[0];
          }
        });
      }
    });
  }

  inBatch(s: string, doc: any) {
    return doc['batch']?.includes(s)
  }

  inContract(filename: string, doc: any) {
    return doc['contracts']?.includes(filename)
  }

  addSelectionToBatch(b: Batch) {
    this.saveDocBatch(b.name, this.docs().filter(d => d.selected));
  }

  addToBatch(b: Batch, doc: any) {
    this.saveDocBatch(b.name, [doc]);
  }

  saveDocBatch(bs: string, docs: any[]) {
    this.service.saveDocBatch(docs.map(d => d.pid), bs).subscribe((res: any) => {
      if (res.error) {
        this.ui.showErrorSnackBar('snackbar.record_added.error');
      } else {
        this.ui.showInfoSnackBar('snackbar.record_added.success');
        console.log(res);
      }
    })
  }

  addSelectionToContract(contract: Contract) {
    this.addToContract(contract, this.docs().filter(d => d.selected));
  }

  addToContract(contract: Contract, docs: any[]) {
    const checkboxes = [{
      label: String(this.translator.instant('change_licenses_at_same_time')),
      checked: true
    }];

    const data: SimpleDialogData = {
      title: this.translator.instant('assigning_work_to_contract'),
      message: this.translator.instant('alert.warn_contract'),
      alertClass: 'app-warn',
      btn1: {
        label: 'Ano',
        value: 'yes',
        color: 'warn'
      },
      btn2: {
        label: 'Ne',
        value: 'no',
        color: 'default'
      }
    };
    data.checkboxes = checkboxes;
    const dialogRef = this.dialog.open(SimpleDialogComponent, {
      data: data
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result === 'yes') {
        this.service.saveDocContract(docs.map(d => d.pid), contract.filename, checkboxes[0].checked, !this.inContract(contract.filename, docs), contract.license).subscribe((res: any) => {
          if (res.error) {
            this.ui.showErrorSnackBar('snackbar.record_added.error');
          } else {
            this.ui.showInfoSnackBar('snackbar.record_added.success');
            console.log(res);
          }
        })
      }
    });

  }

  // -- expanded table row
  isExpanded(element: any) {
    return this.expandedElement === element;
  }
  toggle(element: any) {
    this.expandedElement = this.isExpanded(element) ? null : element;
  }

  saveColumnsSizes(e: any, field?: string) {
    const el = this.state.titleColumns().find((c: any) => c.field === field);
    if (el) {
      el.width = e;
    } else {
      console.log("nemelo by")
    }
    localStorage.setItem('titleColumns', JSON.stringify(this.state.titleColumns()));
  }

  authorChanged() {
    this.getData();
  }

  selectAll(e: any) {
    this.docs().forEach((d: any) => d['selected']=e.target.checked);
  }

  numSelected() {
    return this.docs().filter(d => d.selected).length;
  }

}
