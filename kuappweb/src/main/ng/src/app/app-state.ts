import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";
import { Batch } from "./shared/batch";

@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    public q: string = '';
    usedFacets = signal<{ field: string, value: string }[]>([]);

    public digitalni_knihovny = signal<string[]>([]);
    public licenses = signal<string[]>([]);
    public labels = signal<Label[]>([]);
    public batches = signal<Batch[]>([]);
    public titleColumns = signal<{field: string, selected: boolean, width: number}[]>([]);
    public contractColumns = signal<{field: string, selected: boolean, width: number}[]>([]);
    public contractTitleColumns = signal<{field: string, selected: boolean, width: number}[]>([]);

    // keycloak 
    code: string;
    session_state: string;
    redirect_uri: string;

    encodeState() {
        const obj = {q: this.q, f: this.usedFacets()};
        return btoa(JSON.stringify(obj));
    }

    decodeState(s: string) {
        if (s) {
            const obj = JSON.parse(atob(s));
            this.q = obj.q;
            this.usedFacets.set(obj.f);
        } else {
            
            this.q = '';
            this.usedFacets.set([]);
        }
    }

    
}