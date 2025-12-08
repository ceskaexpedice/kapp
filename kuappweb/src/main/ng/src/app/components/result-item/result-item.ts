import { HttpParams } from '@angular/common/http';
import { Component, effect, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { MatIconModule } from '@angular/material/icon';
import { Author } from '../../shared/author';
import { AuthorDialog } from '../author-dialog/author-dialog';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { MatChipsModule } from '@angular/material/chips';
import { LabelsDialog } from '../labels-dialog/labels-dialog';
import { Label } from '../../shared/label';
import { StateDialog } from '../state-dialog/state-dialog';
import { Batch } from '../../shared/batch';
import { MatFormFieldModule } from "@angular/material/form-field";
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-result-item',
  imports: [RouterModule, TranslateModule, FormsModule, DatePipe,
    MatCardModule, MatButtonModule, MatMenuModule,
    MatIconModule, MatChipsModule, MatFormFieldModule],
  templateUrl: './result-item.html',
  styleUrl: './result-item.scss'
})
export class ResultItem {
  doc = model<any>();
  expanded = input<any>();
  related = signal<any[]>([]);
  links = signal<{ [key: string]: { ident: string, url: string }[] }>({});
  linkTypes = signal<string[]>([]);

  states = ['controlled_public', 'controlled', 'new_imported'];
  labels: Label[] = [];

  hasCollapsed = false;
  isCollapsed = signal<boolean>(true);

  editingDate = signal(false);
  decisionDate: number;

  constructor(
    private dialog: MatDialog,
    public state: AppState,
    private service: AppService,
    public config: AppConfiguration
  ) {
    effect(() => {
      this.labels = [];
      if (this.doc()['labels']) {
        this.doc()['labels'].forEach((name: string) => {
          const l = this.state.labels().find(la => la.name === name);
          if (l) {
            this.labels.push({ name: name, color: l.color, text_color: l.text_color });
          }

        });
      }

      if (this.expanded()) {
        this.hasCollapsed = true;
        this.getRelated();
      }
    })
  }

  getRelated() {
    this.related.set([]);
    this.service.getRelated(this.doc()['root.pid']).subscribe((resp: any) => {
      if (resp.response?.docs?.length > 0) {
        this.related.set(resp.response.docs);
      }
    });
  }

  infoAuthor(a: Author) {
    const dialogRef = this.dialog.open(AuthorDialog, {
      data: {pid:this.doc().pid, author:a},
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.service.getDoc(this.doc().pid).subscribe((resp: any) => {
          if (resp.response?.docs?.length > 0) {
            this.doc.set(resp.response.docs[0]);
          }
        });
      }
    });

  }

  addAuthor() {
    const dialogRef = this.dialog.open(AuthorDialog, {
      data: {pid:this.doc().pid, author:null},
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.service.getDoc(this.doc().pid).subscribe((resp: any) => {
          if (resp.response?.docs?.length > 0) {
            this.doc.set(resp.response.docs[0]);
          }
        });
      }
    });

  }

  addLabel(l: Label) {
    if (!this.labels.find(la => la.name === l.name)) {
      this.labels.push(l);
      this.saveLabels();
    }
  }

  removeLabel(l: Label) {
    const index = this.labels.indexOf(l);
    if (index > -1) {
      this.labels.splice(index, 1);
      this.saveLabels();
    }

    // if (!this.labels.find(la => la.name === l.name)) {
    //   this.labels.push(l);
    //   this.saveLabels();
    // }
  }

  saveLabels() {
    this.service.saveDocLabels(this.doc()['pid'], this.labels.map(l => l.name)).subscribe((res: any) => {
      console.log(res)
    })

  }

  inBatch(s: string) {
    return this.doc()['batch']?.includes(s)
  }

  addToBatch(b: Batch) {
      this.saveDocBatch(b.name);
  }

  saveDocBatch(bs: string) {
    this.service.saveDocBatch(this.doc()['pid'], bs).subscribe((res: any) => {
      console.log(res)
    })

  }

  checkAutor() {
    const p: any = {};
    p.rows = 10;
    p.author = this.doc()['last_author'];
    this.service.checkAuthor(p as HttpParams).subscribe((resp: any) => {
      console.log(resp)
    });
  }

  setState() {

    const dialogRef = this.dialog.open(StateDialog, {
      data: {isBatch: false, doc: this.doc()},
      panelClass: "app-dialog-state"
    });

    dialogRef.afterClosed().subscribe(result => {
      console.log(result)
      if (result) {
        this.service.getDoc(this.doc().pid).subscribe((resp: any) => {
          if (resp.response?.docs?.length > 0) {
            this.doc.set(resp.response.docs[0]);
          }
        });
      }
    });
  }

  toggleCollapsed() {
    this.isCollapsed.set(!this.isCollapsed());
  }

  editNote() {

  }

  editDecisionDate() {
    if (this.doc()['decision_date']) {
      this.decisionDate = this.doc()['decision_date'];
    }
    this.editingDate.set(true)
  }

  closeEditingDate() {
    this.editingDate.set(false)
  }

  saveDecisionDate() {
    
    this.service.saveDecisionDate(this.doc().pid, this.decisionDate).subscribe((res: any) => {
      console.log(res)
    })
  }
}
