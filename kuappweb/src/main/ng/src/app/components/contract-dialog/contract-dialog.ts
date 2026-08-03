import { DatePipe, LowerCasePipe, NgClass } from '@angular/common';
import { HttpClient, HttpEvent, HttpEventType, HttpParams } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule, MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Contract } from '../../shared/contract';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatTabsModule } from '@angular/material/tabs';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { CdkDrag, CdkDragHandle } from '@angular/cdk/drag-drop';
import { ResizecolDirective } from '../../shared/resizecol.directive';
import { TitleDialog } from '../title-dialog/title-dialog';

@Component({
  selector: 'app-contract-dialog',
  imports: [
    TranslateModule, FormsModule, LowerCasePipe,
    CdkDrag, CdkDragHandle, ResizecolDirective, NgClass,
    MatDialogModule, MatMenuModule,
    MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatAutocompleteModule,
    MatListModule, MatTooltipModule, MatTabsModule,
    MatCheckbox, NgxExtendedPdfViewerModule,
    MatSelectModule, MatTableModule
  ],
  templateUrl: './contract-dialog.html',
  styleUrl: './contract-dialog.scss'
})
export class ContractDialog {

  readonly service = inject(AppService);
  readonly ui = inject(UIService);
  readonly state = inject(AppState);
  readonly dialog = inject(MatDialog);
  readonly data = inject<{ doc: Contract }>(MAT_DIALOG_DATA);

  loading = signal<boolean>(false);

  name: string;
  overwrite = false;
  selectedFile: File;
  uploadProgress: number | null;
  progressMsg: string;

  contract = signal<Contract>(new Contract());

  kontaktni_osoby: string = '';

  dodatek: string;
  dodatky = signal<string[]>([]);

  hlavni_smlouva: string;
  hlavni_smlouvy = signal<string[]>([]);

  titles = signal<any[]>([]);
  titles_found = signal<any[]>([]);
  ccnb: string = '';
  issn: string = '';

  withState: boolean = true;

  displayedColumns = computed<string[]>(() => [...this.state.contractTitleColumns().filter(c => c.selected).map(f => f.field), 'action']);
  columnSizes: { [field: string]: number } = {};


  constructor(
    private http: HttpClient) { }

  ngOnInit() {
    this.contract.set(this.data.doc);
    if (this.contract().kontaktni_osoby) {
      this.kontaktni_osoby = this.contract().kontaktni_osoby.join("\n");
    }
    if (this.contract().ccnb) {
      this.ccnb = this.contract().ccnb.join("\n");
    }
    if (this.contract().issn) {
      this.issn = this.contract().issn.join("\n");
    }
    this.state.contractTitleColumns().forEach(c => {
      this.columnSizes[c.field] = c.width ? c.width : 100;
    });
    this.getTitles();
  }

  getTitles() {

    this.titles.set([]);
    const p: any = {};
    p.contract = this.data.doc.filename;
    p.rows = 1000;

    this.service.searchTitlesByContract(p as HttpParams).subscribe((resp: any) => {
      this.titles.set(resp.response.docs);
    });
  }

  findTitles() {

    this.titles_found.set([]);
    const p: any = {};
    p.contract = this.data.doc.filename;
    // p.ccnb = this.data.doc.ccnb;
    // p.issn = this.data.doc.issn;
    p.rows = 1000;
    this.service.findTitlesByContract(p as HttpParams).subscribe((resp: any) => {
      const tf: any = [...resp.ccnb, ...resp.issn];
      tf.forEach((t: any) => {
        if (this.titles().find(ot => ot.pid === t.pid)) {
          t.selected = true;
        }
      })
      this.titles_found.set([...tf]);
    });

  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile() {
    if (this.selectedFile) {
      this.loading.set(true);
      const formData = new FormData();
      formData.append('file', this.selectedFile);

      formData.append('name', this.name ? this.name : this.selectedFile.name),
        formData.append('overwrite', this.overwrite.toString());

      this.http.post("api/index/upload_contract", formData, {
        reportProgress: true,
        observe: 'events'
      }).subscribe((event: HttpEvent<any>) => {
        switch (event.type) {
          case HttpEventType.UploadProgress:
            this.progressMsg = 'uploading...';
            if (event.total) {
              this.uploadProgress = Math.round(100 * (event.loaded / event.total));
            }
            if (this.uploadProgress > 99) {
              this.progressMsg = 'processing...';
            }
            // console.log('Uploaded ' + event.loaded + ' out of ' + event.total + ' bytes');
            break;
          case HttpEventType.Response:
            this.progressMsg = 'Finished uploading!';
            this.loading.set(false);
            const gemini = JSON.parse(event.body.candidates[0].content.parts[0].text);
            gemini.filename = this.selectedFile.name;
            gemini.name = this.name;
            console.log(gemini)
            this.contract.set(gemini);
            break;
        }
      });
    }
  }

  save() {
    this.contract().kontaktni_osoby = this.kontaktni_osoby.split('\n');
    this.contract().issn = this.issn.split('\n');
    this.contract().ccnb = this.ccnb.split('\n');
    this.service.saveContract(this.contract()).subscribe((resp: any) => {
      if (resp.error  ) {
        this.ui.showErrorSnackBar('snackbar.record_saved.error');
      } else {
        this.ui.showInfoSnackBar('snackbar.record_saved.success');
        // console.log(resp)
      }
    })
  }

  addTitleToContract(doc: any, add: boolean) {
    if (!this.state.canEditTitle(doc)) {
      return;
    }
    this.service.saveDocContract([doc.pid], this.contract().filename, this.withState, add, this.contract().license).subscribe((res: any) => {
      console.log(res);
      this.getTitles();
    })

  }

  analyze() {
    this.loading.set(true);
    this.service.analyzeContract(this.contract().filename).subscribe((resp: any) => {
      const gemini = resp;
      gemini.filename = this.contract().filename;
      this.contract.set(gemini);
      this.loading.set(false);

    })
  }

  checkDodatky(e: any) {
    const val = e.target ? e.target.value : e;

    const p: any = {};
    p.dodatek = val;
    p.rows = 10;

    this.service.searchContracts(p as HttpParams).subscribe((resp: any) => {
      this.dodatky.set(resp.response.docs.map((d: Contract) => d.filename));
    });

  }

  checkHlavni(e: any) {
    const val = e.target ? e.target.value : e;

    const p: any = {};
    p.hlavni_smlouva = val;
    p.rows = 10;

    this.service.searchContracts(p as HttpParams).subscribe((resp: any) => {
      this.hlavni_smlouvy.set(resp.response.docs.map((d: Contract) => d.filename));
    });

  }

  setDodatek() {
    if (!this.contract().dodatky) {
      this.contract().dodatky = [];
    }
    this.contract().dodatky.push(this.dodatek);
    // console.log(this.contract().dodatky)
    this.dodatek = null;
  }

  setHlavniSmlouva() {
    //this.contract().hlavni_smlouva = this.hlavni_smlouva
    //this.hlavni_smlouva = null;
  }

  removeDodatek(d: string) {
    const index = this.contract().dodatky.indexOf(d);
    if (index > -1) {
      this.contract().dodatky.splice(index, 1);
    }
  }

  viewDoc(doc: any) {

    const dialogRef = this.dialog.open(TitleDialog, {
      data: doc,
      width: '980px'
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
      }
    });
  }

  saveColumnsSizes(e: any, field?: string) {
    const el = this.state.contractTitleColumns().find((c: any) => c.field === field);
    if (el) {
      el.width = e;
    } else {
      console.log("nemelo by")
    }
    localStorage.setItem('contractTitleColumns', JSON.stringify(this.state.contractTitleColumns()));
  }

}
