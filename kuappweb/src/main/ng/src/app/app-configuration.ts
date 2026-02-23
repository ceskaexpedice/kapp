import { EventEmitter, Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, switchMap, tap } from 'rxjs';
import { AppState } from './app-state';
import { Configuration } from './shared/config';
import { Label } from './shared/label';

@Injectable({
    providedIn: 'root'
}) export class AppConfiguration {

    interceptresponse: EventEmitter<any> = new EventEmitter<any>();

    public config: Configuration;
    
    public get deployPath() {
        return this.config.deployPath;
    }
    
    public get coreBaseUrl() {
        return this.config.coreBaseUrl;
    }

    public get clientApiBaseUrl() {
        return this.coreBaseUrl + '/api/client/v7.0';
    }
    
    public get keycloakUrl() {
        return this.config.keycloakUrl;
    }
    
    public get keycloak() {
        return this.config.keycloak;
    }

    public get defaultLang() {
        return this.config.defaultLang;
    }

    public get states() {
        return this.config.states;
    }

    public get licenses() {
        return this.config.licenses;
    }

    public get titleColumns() {
        return this.config.titleColumns;
    }

    constructor(
        private http: HttpClient,
        public state: AppState) { }

    public configLoaded() {
        return this.config && true;
    }

    public load() {
        console.log('Loading config...')
        return this.http.get('assets/config.json').pipe(
            switchMap((cfg: any) => {
                this.config = cfg as Configuration;
                return this.http.get('api/index/config').pipe(tap((resp: any) => {
                    const b = resp.config.find((a: any) => a.id === 'batches');
                    if (b && b.value.length > 0) {
                        this.state.batches.set(b.value);
                    }
                    
                    const confC = [...this.config.titleColumns];
                    // const c = resp.config.find((a: any) => a.id === 'titleColumns')?.value;
                    const c = localStorage.getItem('titleColumns');
                    if (c) {
                        const cols: {field: string, selected: boolean}[] = JSON.parse(c);
                        // Check if new columns are in config. Then add to state
                        confC.forEach(col => {
                            const localCol = cols.find((lc: {field: string, selected: boolean}) => lc.field === col.field)
                            if (localCol) {
                                col.selected = localCol.selected;
                            }
                        });
                        this.state.titleColumns.update(a => [...cols]);
                    } else {
                        //this.state.titleColumns.update(a => [...this.config.titleColumns]);
                    }

                    this.state.titleColumns.update(a => [...confC]);

                    const labelsC: Label[] = resp.config.find((a: any) => a.id === 'labels')?.value;
                    if (labelsC) {
                        labelsC.forEach(l => {
                            const r = parseInt(l.color.substring(1,3), 16);
                            const g = parseInt(l.color.substring(3,5), 16);
                            const b = parseInt(l.color.substring(5,7), 16);
                            // const c = parseInt(l.color.substring(1), 16);
                            const c = r+g+b;
                            l.text_color = c > 256 ? '#000' : '#fff';
                        });
                        this.state.labels.set(labelsC);
                    }
                    
                    const licenses: string[] = resp.config.find((a: any) => a.id === 'licenses')?.value;
                    if (licenses) {
                        this.state.licenses.set(licenses);
                    } else {
                        this.state.licenses.set([...this.config.licenses]);
                    }
                    
                    const dk: string[] = resp.config.find((a: any) => a.id === 'digitalni_knihovny')?.value;
                    if (dk) {
                        this.state.digitalni_knihovny.set(dk);
                    } else {
                        this.state.digitalni_knihovny.set([...this.config.digitalni_knihovny]);
                    }

                }));
            }),
            catchError((err) => {
                // this.alertSubject.next(err);
                return of(err);
            })
        );
    }

}
