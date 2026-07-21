import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";
import { Batch } from "./shared/batch";
import { User } from "./shared/user";
import { UsedFacet } from "./shared/facet";
import { LibraryPhysicalLocations } from "./shared/library-rights";

@Injectable({
    providedIn: 'root'
}) export class AppState {

    public showInfo = signal<boolean>(false);
    public q: string = '';
    usedFacets = signal<UsedFacet[]>([]);

    public digitalni_knihovny = signal<string[]>([]);
    public libraryPhysicalLocations = signal<LibraryPhysicalLocations[]>([]);
    public licenses = signal<string[]>([]);
    public labels = signal<Label[]>([]);
    public labelSets = signal<Record<string, Label[]>>({});
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
        return this.roles().includes(role);
    }

    roles(): string[] {
        const user: any = this.user();
        return [...new Set<string>([
            ...(user?.roles || []),
            ...(user?.realm_access?.roles || []),
            ...Object.values(user?.resource_access || {})
                .flatMap((resource: any) => resource?.roles || [])
        ])];
    }

    canEditTitle(title: any): boolean {
        if (!this.user()) {
            return false;
        }
        if (this.hasRole('kappAdmin')) {
            return true;
        }

        const roles = new Set(this.roles());
        const titleLocations = new Set<string>(this.asArray(title?.physical_locations));
        return this.libraryPhysicalLocations().some(mapping =>
            roles.has(mapping.library)
            && this.asArray(mapping.physicalLocations).some(location => titleLocations.has(location))
        );
    }

    hasLibraryRole(): boolean {
        if (!this.user()) {
            return false;
        }
        if (this.hasRole('kappAdmin')) {
            return true;
        }
        const roles = new Set(this.roles());
        return this.libraryPhysicalLocations().some(mapping => roles.has(mapping.library));
    }

    canEditTitles(titles: any[]): boolean {
        return titles.length > 0 && titles.every(title => this.canEditTitle(title));
    }

    setLabelSet(id: string, labels: Label[]): void {
        const sets = {...this.labelSets(), [id]: labels};
        this.labelSets.set(sets);
        const visible: Label[] = [];
        const names = new Set<string>();
        Object.values(sets).forEach(scopedLabels => scopedLabels.forEach(label => {
            if (!names.has(label.name)) {
                names.add(label.name);
                visible.push(label);
            }
        }));
        this.labels.set(visible);
    }

    private asArray(value: string | string[] | null | undefined): string[] {
        if (Array.isArray(value)) {
            return value;
        }
        return value ? [value] : [];
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
