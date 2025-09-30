import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, of, switchMap, tap } from 'rxjs';
import { AppState } from './app-state';
import { Configuration } from './shared/config';
import { Label } from './shared/label';

@Injectable({
    providedIn: 'root'
}) export class AppConfiguration {

    public config: Configuration;
    public get context() {
        return this.config.context;
    }

    public get defaultLang() {
        return this.config.defaultLang;
    }

    constructor(
        private http: HttpClient,
        public state: AppState) { }

    public configLoaded() {
        return this.config && true;
    }

    public load() {
        return this.http.get('assets/config.json').pipe(
            switchMap((cfg: any) => {
                this.config = cfg as Configuration;
                return this.http.get('api/index/config').pipe(tap((resp: any) => {
                    
                    const labelsC: Label[] = resp.config.find((a: any) => a.id === 'labels').value;
                    labelsC.forEach(l => {
                        const r = parseInt(l.color.substring(1,3), 16);
                        const g = parseInt(l.color.substring(3,5), 16);
                        const b = parseInt(l.color.substring(5,7), 16);
                        // const c = parseInt(l.color.substring(1), 16);
                        const c = r+g+b;
                        l.text_color = c > 256 ? '#000' : '#fff';
                    });
                    this.state.labels.set(labelsC);

                }));
            }),
            catchError((err) => {
                // this.alertSubject.next(err);
                return of(err);
            })
        );
    }

}
