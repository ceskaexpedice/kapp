import { Injectable } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

@Injectable()
export class UIService {

  constructor(
    private clipboard: Clipboard,
    private snackBar: MatSnackBar,
    private translator: TranslateService
  ) { }


  showInfoSnackBar(message: string, duration: number = 4000) {
    this.snackBar.open(this.translator.instant(message), 'OK', { duration: duration, panelClass: 'app-snackbar-success', verticalPosition: 'top' });
  }

  showErrorSnackBar(message: string, duration: number = 4000) {
    this.snackBar.open(this.translator.instant(message), 'Error', { duration: duration, panelClass: 'app-snackbar-error', verticalPosition: 'top' });
  }

  copyTextToClipboard(val: string) {
    this.clipboard.copy(val);
    this.showInfoSnackBar('snackbar.copyTextToClipboard.success');
  } 
}
