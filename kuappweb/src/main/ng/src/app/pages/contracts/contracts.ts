import { DatePipe } from '@angular/common';
import { HttpClient, HttpEvent, HttpEventType, HttpParams } from '@angular/common/http';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AngularSplitModule } from 'angular-split';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { ContractDialog } from '../../components/contract-dialog/contract-dialog';
import { Contract } from '../../shared/contract';
import { Router } from '@angular/router';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCheckbox } from "@angular/material/checkbox";
import { ResizecolDirective } from '../../shared/resizecol.directive';

@Component({
  selector: 'app-contracts',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatMenuModule, MatProgressBarModule,
    MatIconModule, MatButtonModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatSortModule,
    AngularSplitModule, MatTableModule,
    MatTooltipModule, ResizecolDirective,
    MatCheckbox
  ],
  templateUrl: './contracts.html',
  styleUrl: './contracts.scss'
})
export class Contracts {


  private http = inject(HttpClient);
  readonly service = inject(AppService);
  readonly state = inject(AppState);
  readonly dialog = inject(MatDialog);
  private router = inject(Router);

  loading = signal<boolean>(false);

  solrResponse: any;
  docs = signal<any[]>([]);
  hasFacets = signal<boolean>(false);
  doc = signal<Contract>(null);

  rows = 1000;
  numFound: number = 0;
  pageIndex = 0;
  pageSizeOptions = [10, 25, 100];
  sort: Sort = { active: 'indextime', direction: 'desc' };

  //displayedColumns = ['id', 'filename', 'cislo_smlouvy', 'nazev_poskytovatele', 'ico', 'datum_uzavreni', 'datum_platnosti', 'kontaktni_osoby'];
  displayedColumns = ['filename', 'title', 'cislo_smlouvy', 'nazev_poskytovatele', 'ico', 'datum_uzavreni'];


  name: string;
  overwrite = false;
  analyze = true;
  selectedFile: File;
  uploadProgress: number | null;
  progressMsg: string;
  selctedFileExists = signal<boolean>(false);

  constructor() {
    effect(() => {
      const usedFacets = this.state.usedFacets();
      this.getData();

    })
  }

  addFilters(p: any, usedFacets: { field: string, value: string }[]) {
    // p.author = usedFacets.filter(k => k.field === 'authors').map(k => k.value);
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
    p.sort = this.sort.active + " " + this.sort.direction;
    this.addFilters(p, this.state.usedFacets());

    this.service.searchContracts(p as HttpParams).subscribe((resp: any) => {
      this.solrResponse = resp;
      this.numFound = this.solrResponse.response.numFound;
      this.docs.set(this.solrResponse.response.docs);
    });
  }

  sortTable(e: Sort) {
    this.sort = e;
    this.getData();
  }

  showContract(doc: Contract) {
    const dialogRef = this.dialog.open(ContractDialog, {
      data: { doc },
      panelClass: "app-dialog-contract"
    });

    dialogRef.afterClosed().subscribe(result => {
      this.getData();
    });
  }

  viewDoc(doc: any) {
    this.service.getContract(doc.filename).subscribe((resp: any) => {
      if (resp.response?.docs?.length > 0) {
        this.doc.set(resp.response.docs[0]);
        this.showContract(doc)
      }
    });

  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
    this.checkUpload();
  }



  checkUpload() {
    if (this.overwrite) {
      this.uploadFile();
      return;
    }
    this.selctedFileExists.set(false);
    this.service.checkContractExists(this.selectedFile.name).subscribe((resp: any) => {
      if (resp.exists) {
        // alert('File already exists. Check option to overwrite');
        this.selctedFileExists.set(true);
      } else if (resp.error) {
        alert('Error: ' + resp.error);
      } else {
        this.uploadFile()
      }
    })
  }

  uploadFile() {
    if (this.selectedFile) {
      this.loading.set(true);
      const formData = new FormData();
      formData.append('file', this.selectedFile);

      formData.append('name', this.name ? this.name : this.selectedFile.name),
        formData.append('overwrite', this.overwrite.toString());
      formData.append('analyze', this.analyze.toString());

      this.http.post("/api/index/upload_contract", formData, {
        reportProgress: true,
        observe: 'events'
      }).subscribe((event: HttpEvent<any>) => {
        switch (event.type) {
          case HttpEventType.UploadProgress:
            this.progressMsg = 'uploading';
            if (event.total) {
              this.uploadProgress = Math.round(100 * (event.loaded / event.total));
            }
            if (this.uploadProgress > 99) {
              this.progressMsg = 'processing';
            }
            // console.log('Uploaded ' + event.loaded + ' out of ' + event.total + ' bytes');
            break;
          case HttpEventType.Response:
            this.progressMsg = 'Finished uploading!';
            this.loading.set(false);
            if (event.body.error) {
              console.log(event.body.error);
            } else {
              const gemini = event.body;
              gemini.filename = this.selectedFile.name;
              gemini.name = this.name;
              this.showContract(gemini);
            }
            break;
        }
      });
    }
  }

}
