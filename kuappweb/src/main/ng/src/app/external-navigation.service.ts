import { Injectable } from '@angular/core';

@Injectable({providedIn: 'root'})
export class ExternalNavigationService {
  assign(url: string): void {
    window.location.assign(url);
  }
}
