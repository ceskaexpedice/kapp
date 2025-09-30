import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";



@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    public q: string = '';

    public labels = signal<Label[]>([]);
    
}