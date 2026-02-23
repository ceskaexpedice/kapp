import { DatePipe } from '@angular/common';
import { HttpClient, HttpEvent, HttpEventType, HttpParams } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
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
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Contract } from '../../shared/contract';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatTabsModule } from '@angular/material/tabs';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { MatSelectModule } from '@angular/material/select';

@Component({
  selector: 'app-contract-dialog',
  imports: [
    TranslateModule, FormsModule,
    MatDialogModule, MatMenuModule,
    MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule, MatAutocompleteModule,
    MatListModule, MatTooltipModule, MatTabsModule,
    MatCheckbox, NgxExtendedPdfViewerModule,
    MatSelectModule
  ],
  templateUrl: './contract-dialog.html',
  styleUrl: './contract-dialog.scss'
})
export class ContractDialog {

  readonly service = inject(AppService);
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
  ccnb: string = '';
  issn: string = '';


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

    this.titles.set([]);
    const p: any = {};
    p.contract = this.data.doc.filename;
    p.ccnb = this.data.doc.ccnb;
    p.issn = this.data.doc.issn;
    p.contract = this.data.doc.filename;
    p.rows = 1000;
    this.service.findTitlesByContract(p as HttpParams).subscribe((resp: any) => {
      this.titles.set(resp.response.docs);
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
      // console.log(resp)
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

}
