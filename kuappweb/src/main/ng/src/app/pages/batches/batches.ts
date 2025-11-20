import { Component, inject, signal } from '@angular/core';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Batch } from '../../shared/batch';
import { AngularSplitModule } from "angular-split";
import { DatePipe } from '@angular/common';
import { ResultItem } from "../../components/result-item/result-item";

import { FileUploadModule, FileUploader, FileUploaderOptions } from 'ng2-file-upload';

@Component({
  selector: 'app-batches',
  imports: [
    TranslateModule, FormsModule, DatePipe, FileUploadModule,
    MatDialogModule, MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    AngularSplitModule,
    ResultItem
  ],
  templateUrl: './batches.html',
  styleUrl: './batches.scss'
})
export class Batches {

  readonly service = inject(AppService);
  readonly state = inject(AppState);
  loading = signal<boolean>(false);


  newBatch: string;
  selectedBatch = signal<Batch>(null);
  docs = signal<any[]>([]);

  public uploader: FileUploader = new FileUploader({ url: 'api/index/upload_batch' });
  selectedFile: string;
  progressMsg: string;

  onFileSelected(e: any) {
    console.log(e)
    const file: File = e.target.files[0];
    if (file) {
      this.selectedFile = file.name;
      if (!this.state.batches().find(b => b.name === this.newBatch)) {
        this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
        this.saveBatches();
        this.uploadFile();
      }
    }
  }

  uploadFile() {
    const opts: FileUploaderOptions = {
      url: 'api/index/upload_batch',
      additionalParameter: {
        name: this.newBatch ? this.newBatch : this.selectedFile
      }
    };
    this.progressMsg = 'uploading...'
    this.uploader.setOptions(opts);
    this.uploader.onSuccessItem = (item: any, response: any, status: any, headers: any) => this.fileUploaded(JSON.parse(response));
    this.uploader.uploadAll();
  }

  fileUploaded(response: any) {
    console.log(response, response['error'])
    if (response.error) {
      alert(response.error);
      this.progressMsg = 'Error uploading ';
    } else {
      this.progressMsg = response.msg;
    }
  }

  selectBatch(b: Batch) {
    this.selectedBatch.set(b);
    this.service.getDocsInBatch(b.name).subscribe(resp => {
      this.docs.set(resp.response.docs);
    });
  }

  addBatch() {

    if (!this.state.batches().find(b => b.name === this.newBatch)) {
      // this.batches.push({date_created: new Date(), name: this.newBatch, state: 'new'});
      this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
      this.saveBatches();
    }
  }

  importBatch() {

    if (!this.state.batches().find(b => b.name === this.newBatch)) {
      // this.batches.push({date_created: new Date(), name: this.newBatch, state: 'new'});
      this.state.batches.update(b => [...b, { date_created: new Date(), name: this.newBatch, state: 'new' }]);
      this.saveBatches();
    }
  }


  saveBatches() {
    this.service.saveBatches(this.state.batches()).subscribe(resp => {
      console.log(resp)
    });
  }

}
