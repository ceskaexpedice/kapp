import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";
import { Batch } from "./shared/batch";
import { User } from "./shared/user";
import { UsedFacet } from "./shared/facet";

@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    public q: string = '';
    usedFacets = signal<UsedFacet[]>([]);

    public digitalni_knihovny = signal<string[]>([]);
    public licenses = signal<string[]>([]);
    public labels = signal<Label[]>([]);
    public batches = signal<Batch[]>([]);
    public titleColumns = signal<{ field: string, selected: boolean, width: number }[]>([]);
    public contractColumns = signal<{ field: string, selected: boolean, width: number }[]>([]);
    public contractTitleColumns = signal<{ field: string, selected: boolean, width: number }[]>([]);

    // keycloak 
    code: string;
    session_state: string;
    redirect_uri: string;
    user = signal<User>(null);



    setUser(user: any) {
        return this.user.set(user);
    }

    hasRole(role: string): boolean {
        const user: any = this.user();
        const roles = [
            ...(user?.roles || []),
            ...(user?.realm_access?.roles || []),
            ...Object.values(user?.resource_access || {})
                .flatMap((resource: any) => resource?.roles || [])
        ];
        return roles.includes(role);
    }

    encodeState() {
        const obj = { q: this.q, f: this.usedFacets() };
        return btoa(encodeURIComponent(JSON.stringify(obj)));
    }

    decodeState(s: string) {
        if (s) {
            const obj = JSON.parse(decodeURIComponent(atob(s)));
            this.q = obj.q;
            this.usedFacets.set(obj.f);
        } else {

            this.q = '';
            this.usedFacets.set([]);
        }
    }
}
