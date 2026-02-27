import { Component, inject, signal } from '@angular/core';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { UIService } from '../../ui.service';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { TranslateService } from '@ngx-translate/core';
import { Batch } from '../../shared/batch';
import { AngularSplitModule } from "angular-split";
import { DatePipe, LowerCasePipe, NgClass } from '@angular/common';
import { ResultItem } from "../../components/result-item/result-item";

import { FileUploadModule, FileUploader, FileUploaderOptions } from 'ng2-file-upload';
import { StateDialog } from '../../components/state-dialog/state-dialog';
import { MatTableModule } from '@angular/material/table';
import { TitleDialog } from '../../components/title-dialog/title-dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatListItem, MatListModule } from "@angular/material/list";
import { MatTooltipModule } from '@angular/material/tooltip';
import { AddToBatchDialog } from '../../components/add-to-batch-dialog/add-to-batch-dialog';

@Component({
  selector: 'app-batches',
  imports: [
    TranslateModule, FormsModule, DatePipe, FileUploadModule,
    MatDialogModule, MatMenuModule,
    MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    AngularSplitModule, MatTableModule,
    MatListItem, MatListModule, MatTooltipModule, LowerCasePipe, NgClass
],
  templateUrl: './batches.html',
  styleUrl: './batches.scss'
})
export class Batches {

  readonly service = inject(AppService);
  readonly state = inject(AppState);
  readonly dialog = inject(MatDialog);
  readonly translator = inject (TranslateService);

  loading = signal<boolean>(false);


  newBatch: string;
  selectedBatch = signal<Batch>(null);
  docs = signal<any[]>([]);
  states = signal<string[]>([]);

  public uploader: FileUploader = new FileUploader({ url: 'api/index/upload_batch' });
  selectedFile: string;
  progressMsg: string;

  displayedColumns = ['pid', 'title', 'state', 'action']

  
  counts = signal<{[batch: string]: number}>({});

  constructor(
    public ui: UIService
  ) {}

  ngOnInit() {
    this.selectBatch(this.state.batches()[0]);
    this.getUsedBatches();
  }

  getUsedBatches() {
    
    this.service.getUsedBatches().subscribe(res => {
      const c: {[batch: string]: number} = {};
      res.buckets.forEach((b: { val: string; count: number; }) => {
        c[b.val] = b.count;
      });
      this.counts.set(c);
    });
  }

  onFileSelected(e: any) {
    const file: File = e.target.files[0];
    if (file) {
      this.selectedFile = file.name;
      //if (!this.state.batches().find(b => b.name === this.newBatch)) {
      // this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
      // this.saveBatches();
      this.uploadFile();
      //}
    }
  }

  uploadFile() {
    const opts: FileUploaderOptions = {
      url: 'api/index/upload_batch',
      additionalParameter: {
        name: this.selectedBatch().name
      }
    };
    this.progressMsg = 'uploading...'
    this.uploader.setOptions(opts);
    this.uploader.onSuccessItem = (item: any, response: any, status: any, headers: any) => this.fileUploaded(JSON.parse(response));
    this.uploader.uploadAll();
  }

  fileUploaded(response: any) {
    if (response.error) {
      alert(response.error);
      this.progressMsg = 'Error uploading ';
    } else {
      this.progressMsg = response.msg;
    }
    this.loadBatch();
  }

  selectBatch(b: Batch) {
    this.selectedBatch.set(b);
    this.loadBatch();
  }

  loadBatch() {
    this.service.getDocsInBatch(this.selectedBatch().name).subscribe(resp => {
      this.docs.set(resp.response.docs);
      if (resp.facets.state.buckets) {
      this.states.set(resp.facets.state.buckets.map((b: any) => b.val));

      }
    });

  }

  addBatch() {
    if (!this.newBatch) {
      return;
    }
    if (!this.state.batches().find(b => b.name === this.newBatch)) {
      // this.batches.push({date_created: new Date(), name: this.newBatch, state: 'new'});
      this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
      this.newBatch = null;
      this.saveBatches(this.newBatch, true);
      // this.batchExist = false; 
    } else {
      // this.batchExist = true;
    }
  }

  batchExist(): boolean {
    return this.state.batches().findIndex(b => b.name === this.newBatch) > -1
  }

  

  removeBatch(b: Batch) {
    const idx = this.state.batches().findIndex(b => b.name === this.newBatch);
    if (idx > -1) {
      const b = this.state.batches()
      b.splice(idx, 1);
      this.state.batches.set([...b]);
      this.saveBatches(null, false);
    }
  }

  addDoc() {
    //const pid = window.prompt("PID?");

    const dialogRef = this.dialog.open(AddToBatchDialog, {
      data: { pid: String },
      panelClass: "app-dialog-add-to-batch"
    });

    dialogRef.afterClosed().subscribe((pid: any) => {
      if (pid) {
      this.service.saveDocBatch(pid, this.selectedBatch().name).subscribe((res: any) => {
        if (res.error) {
          this.ui.showErrorSnackBar('snackbar.record_added.error');
        } else {
          this.ui.showInfoSnackBar('snackbar.record_added.success');
        }
        this.loadBatch();
      });
      }
    });


    /* if (pid) {
      this.service.saveDocBatch(pid, this.selectedBatch().name).subscribe((res: any) => {
        this.loadBatch();
      })
    } */
  }

  importBatch() {

    if (!this.state.batches().find(b => b.name === this.newBatch)) {
      // this.batches.push({date_created: new Date(), name: this.newBatch, state: 'new'});
      this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
      this.saveBatches(this.newBatch, true);
    }
  }


  saveBatches(batch: string, adding: boolean) {
    this.service.saveBatches(this.state.batches()).subscribe(resp => {
      if (batch) {
        this.selectBatch(this.state.batches().find(b => b.name === batch));
      }
      if (resp.error) {
        this.ui.showErrorSnackBar(adding ? 'snackbar.record_added.error' : 'snackbar.record_remove.error');
      } else {
        this.ui.showInfoSnackBar(adding ? 'snackbar.record_added.success' : 'snackbar.record_remove.success');
      }
    });
  }

  setState() {

    const dialogRef = this.dialog.open(StateDialog, {
      data: { isBatch: true, batch: this.selectedBatch().name, states: this.states() },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadBatch();
      }
    });
  }

  removeFromBatch(pid: string) {
    this.service.saveDocBatch(pid, '').subscribe((res: any) => {
      this.loadBatch();
      if (res.error) {
        this.ui.showErrorSnackBar('snackbar.record_removed.error');
      } else {
        this.ui.showInfoSnackBar('snackbar.record_removed.success');
        this.getUsedBatches();
      }
    })
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

  createBatchFromLabel(label: string) {
      this.service.createBatchFromLabel(label).subscribe((res: any) => {
        this.state.batches.update(b => [...b, { date_created: new Date(), name: res.batch, state: 'new' }]);
        this.saveBatches(res.batch, true);
      })
  }

}
