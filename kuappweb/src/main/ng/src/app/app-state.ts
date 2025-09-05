import { Injectable, signal } from "@angular/core";


@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    
}