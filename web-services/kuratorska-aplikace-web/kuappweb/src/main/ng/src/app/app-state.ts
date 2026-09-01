import { Injectable, signal } from "@angular/core";
import { Label } from "./shared/label";
import { Batch } from "./shared/batch";
import { User } from "./shared/user";
import { UsedFacet } from "./shared/facet";
import { LibraryPhysicalLocations } from "./shared/library-rights";

const SEARCH_FACET_FIELDS = [
    'date_issued_year',
    'labels',
    'state',
    'modified_by_user',
    'batch',
    'contracts',
    'assigned_licenses',
    'licenses',
    'digital_library',
    'model',
    'physical_locations',
    'authors',
    'authors_ids',
    'state_date',
    'last_modified_date',
    'no_nameIdentifier',
    'no_year_death'
] as const;

type SearchParams = Record<string, string | string[] | null | undefined>;

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

    canEditTitleLabels(): boolean {
        return !!this.user();
    }

    setLabelSet(id: string, labels: Label[]): void {
        const sets = {...this.labelSets(), [id]: labels};
        this.labelSets.set(sets);
        const visible: Label[] = [];
        const names = new Set<string>();
        Object.values(sets).forEach(scopedLabels => scopedLabels.forEach(label => {
            const storageName = label.storageName || label.name;
            if (!names.has(storageName)) {
                names.add(storageName);
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

    toQueryParams(): Record<string, string | string[]> {
        const params: Record<string, string | string[]> = {};
        if (this.q) {
            params['q'] = this.q;
        }

        this.usedFacets().forEach(facet => {
            const parameter = facet.negated ? `${facet.field}_not` : facet.field;
            const current = params[parameter];
            if (current === undefined) {
                params[parameter] = facet.value;
            } else if (Array.isArray(current)) {
                current.push(facet.value);
            } else {
                params[parameter] = [current, facet.value];
            }
        });
        return params;
    }

    decodeQueryParams(params: SearchParams): void {
        const hasReadableState = Object.prototype.hasOwnProperty.call(params, 'q')
            || SEARCH_FACET_FIELDS.some(field =>
                Object.prototype.hasOwnProperty.call(params, field)
                || Object.prototype.hasOwnProperty.call(params, `${field}_not`)
            );

        if (!hasReadableState && typeof params['s'] === 'string') {
            this.decodeState(params['s']);
            return;
        }

        this.q = this.firstValue(params['q']) || '';
        const facets: UsedFacet[] = [];
        SEARCH_FACET_FIELDS.forEach(field => {
            this.values(params[field]).forEach(value => facets.push({field, value}));
            this.values(params[`${field}_not`]).forEach(value => facets.push({field, value, negated: true}));
        });
        this.usedFacets.set(facets);
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


    private values(value: string | string[] | null | undefined): string[] {
        if (Array.isArray(value)) {
            return value.filter(item => item !== '');
        }
        return value ? [value] : [];
    }

    private firstValue(value: string | string[] | null | undefined): string | undefined {
        return Array.isArray(value) ? value[0] : value ?? undefined;
    }
}
