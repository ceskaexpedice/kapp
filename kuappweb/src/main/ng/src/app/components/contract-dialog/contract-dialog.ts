import { DatePipe } from '@angular/common';
import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
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

@Component({
  selector: 'app-contract-dialog',
  imports: [
    TranslateModule, FormsModule, 
    MatDialogModule, MatMenuModule, 
    MatIconModule, MatButtonModule, MatCardModule,
    MatChipsModule, MatFormFieldModule, MatInputModule,
    MatProgressBarModule,
    MatListModule, MatTooltipModule
  ],
  templateUrl: './contract-dialog.html',
  styleUrl: './contract-dialog.scss'
})
export class ContractDialog {
  
  readonly service = inject(AppService);
  readonly state = inject(AppState);
  readonly dialog = inject(MatDialog);
  readonly data = inject<{doc: Contract}>(MAT_DIALOG_DATA);

  loading = signal<boolean>(false);
  
  name: string;
  overwrite = false;
  selectedFile: File;
  uploadProgress: number | null;
  progressMsg: string;

  contract = signal<Contract>(new Contract());

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.contract.set(this.data.doc);
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile(){
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
  this.service.saveContract(this.contract()).subscribe((resp: any) => {
        console.log(resp)
      })
    }
}
