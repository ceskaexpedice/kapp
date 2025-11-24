import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";
import { Batch } from "./shared/batch";



@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    public q: string = '';
    usedFacets = signal<{ field: string, value: string }[]>([]);

    public labels = signal<Label[]>([]);
    public batches = signal<Batch[]>([]);
    
}