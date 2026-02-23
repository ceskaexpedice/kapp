import { CdkDropList, CdkDrag, CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';

@Component({
  selector: 'app-columns-settings-dialog',
  imports: [TranslateModule, MatDialogModule, CdkDropList, CdkDrag,
    MatIconModule, MatButtonModule, MatTooltipModule, FormsModule, MatCheckboxModule],

  templateUrl: './columns-settings-dialog.html',
  styleUrl: './columns-settings-dialog.scss'
})
export class ColumnsSettingsDialog {

  public allColumns: { field: string, selected: boolean, width: number }[];

  constructor(
    public dialogRef: MatDialogRef<ColumnsSettingsDialog>,
    private service: AppService,
    public state: AppState) { }

    ngOnInit() {
      this.allColumns = [...this.state.titleColumns()];
    }

  save() {
    localStorage.setItem('titleColumns', JSON.stringify(this.allColumns));
    this.state.titleColumns.update(a => [...this.allColumns])
    // this.service.saveTitleColumns(this.allColumns).subscribe(resp => {
    //   console.log(resp);
    //   this.state.titleColumns.update(a => [...this.allColumns])
    // });
  }

  reset() {

  }

  drop(event: CdkDragDrop<string[]>) {
    moveItemInArray(this.allColumns, event.previousIndex, event.currentIndex);
  }

}
