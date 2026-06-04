import { HttpParams } from '@angular/common/http';
import { Component, effect, input, model, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { AppConfiguration } from '../../app-configuration';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';
import { MatIconModule } from '@angular/material/icon';
import { Author, AuthorTitle } from '../../shared/author';
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
import { AuthorTitleDialog, AuthorTitleDialogData } from '../author-title-dialog/author-title-dialog';
import { MatListModule } from "@angular/material/list";
import { isIndexSignatureDeclaration } from 'typescript';
import { MatCheckbox } from "@angular/material/checkbox";
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-result-item',
  imports: [RouterModule, TranslateModule, FormsModule, DatePipe,
    MatCardModule, MatButtonModule, MatMenuModule,
    MatIconModule, MatChipsModule, MatFormFieldModule, MatListModule, MatCheckbox, MatInputModule],
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
  //hasCollapsed = true;
  isCollapsed = signal<boolean>(true);

  editingNote = signal(false);
  editingDate = signal(false);
  editingDilo = signal(false);
  lastYearDeath: number;
  is_anonymous: boolean;
  is_collective: boolean;
  is_official: boolean;
  is_pseudonymous: boolean;
  note: string;
  noteRows = 3;
  noteWidth = 200;

  mergedAuthors: AuthorTitle[] = [];
  lastAuthor: string;

  constructor(
    private router: Router,
    private dialog: MatDialog,
    public state: AppState,
    private service: AppService,
    public config: AppConfiguration
  ) {
    effect(() => {

      //pridat vsechny pole z indexu

      this.labels = [];
      let doc = this.doc();
      this.is_anonymous = this.doc()['is_anonymous'];
      this.is_collective = this.doc()['is_collective'];
      this.is_official = this.doc()['is_official'];
      this.is_pseudonymous = this.doc()['is_pseudonymous'];
      if (this.doc()['note']) {
        this.note = this.doc()['note'].join("\n");
        const lines = this.note.split('\n');
        this.noteRows = lines.length > 2 ? lines.length : 3;
      }
      
      //this.hasCollapsed = this.doc()['is_hierarchical'];
      if (this.doc()['labels']) {
        this.doc()['labels'].forEach((name: string) => {
          const l = this.state.labels().find(la => la.name === name);
          if (l) {
            this.labels.push({ name: name, color: l.color, text_color: l.text_color });
          }

        });
      }

      this.mergedAuthors = [...this.setMergedAuthors(this.doc())];
      const hasLast = this.mergedAuthors.find(a => a.isLast)
      this.lastAuthor = hasLast ? hasLast.full : '';

      if (this.doc()['is_hierarchical']) {
        this.hasCollapsed = true;
        this.getRelated();
      } else if (this.expanded()) {
        //zobrazit expanded
        // this.hasCollapsed = true;
        // console.log(this.expanded())
        // this.related.set([...this.expanded().docs]);
        // console.log(this.related())
      }
    })
  }

  setMergedAuthors(d: any) {
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

  getRelated() {
    this.related.set([]);
    this.service.getRelated(this.doc()['root_pid']).subscribe((resp: any) => {
      if (resp.response?.docs?.length > 0) {
        //    p.author = usedFacets.filter(k => k.field === 'authors').map(k => k.value);
        // let ddcs = [];
        // resp.response.docs.forEach((d: any) => {
        //   if (d['pid'] != d['root.pid']) {
        //     ddcs.push(d);
        //   }
        // });  
        resp.response.docs.filter((d: any) => d['pid'] != d['root_pid']);
        let relatedDocs = resp.response.docs.filter((d: any) => d['pid'] != d['root_pid']);
        const sortedDocs = relatedDocs
          .filter((d: any) => d['pid'] !== d['root_pid'])
          .sort((a: any, b: any) => {
            const titleA = (a['root.title'] || '').toLowerCase();
            const titleB = (b['root.title'] || '').toLowerCase();
            return titleA.localeCompare(titleB, 'cs'); // 'cs' zajisti spravne ceske razeni (c vs c)
          });

        this.related.set(sortedDocs);

      }
    });
  }

  infoAuthor(a: AuthorTitle) {
    const data: AuthorTitleDialogData = {
      pid: this.doc().pid,
      authors_kapp: this.doc().authors_kapp,
      authors_kramerius: this.doc().authors_kramerius,
      id: a.id,
      ids: this.doc().authors_ids,
      nameIdentifiers: this.doc().nameIdentifiers
    }
    const dialogRef = this.dialog.open(AuthorTitleDialog, {
      data: data,
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
    const data: AuthorTitleDialogData = {
      pid: this.doc().pid,
      authors_kapp: this.doc().authors_kapp,
      authors_kramerius: this.doc().authors_kramerius,
      id: null,
      ids: this.doc().authors_ids,
      nameIdentifiers: this.doc().nameIdentifiers
    }
    const dialogRef = this.dialog.open(AuthorTitleDialog, {
      data: data,
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
      data: { isBatch: false, doc: this.doc() },
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
    if (this.doc()['note']) {
      this.note = this.doc()['note'].join("\n");
    }
    this.editingNote.set(true)
  }

  closeEditingNote() {
    this.editingNote.set(false);
    if (this.doc()['note']) {
      this.note = this.doc()['note'].join("\n");
    } else {
      this.note = '';
    }
  }

  saveNote() {

    this.service.saveTitleNote(this.doc().pid, this.note).subscribe((res: any) => {
      console.log(res)
    })
  }

  editLastYearDeath() {
    if (this.doc()['last_year_death']) {
      this.lastYearDeath = this.doc()['last_year_death'];
    }
    this.editingDate.set(true)
  }

  closeEditingDate() {
    this.editingDate.set(false)
  }

  saveLastYearDeath() {
    this.service.saveLastYearDeath(this.doc().pid, this.lastYearDeath, this.doc().authors_kapp).subscribe((res: any) => {
      console.log(res)
    })
  }



  editTypDila() {
    this.editingDilo.set(true)
  }

  closeEditingDilo() {
    this.editingDilo.set(false)
  }

  saveTypDila() {
    this.service.saveTypDila(this.doc().pid, this.is_anonymous, this.is_collective, this.is_official, this.is_pseudonymous).subscribe((res: any) => {
      console.log(res)
    })
  }

  gotoPid(pid: string) {
    this.state.q = pid;
    this.state.usedFacets.set([]);
    this.router.navigate(['/titles'], { queryParams: { s: this.state.encodeState() } });
    // const p: any = {};
    // p.q = pid;
    // p.only_pid = true;
    // this.service.searchTitlesByAuthor(p as HttpParams).subscribe((resp: any) => {
    //   this.numTitles.set(resp.response.numFound);
    // });
  }
}
