import { HttpClient, HttpHeaders, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map, finalize, catchError, of } from 'rxjs';
import { Label } from './shared/label';

@Injectable({
  providedIn: 'root'
})
export class AppService {

  constructor(
    private http: HttpClient) { }

  private get(path: string, params = {}): Observable<Object> {
    const headers = new HttpHeaders({
      //'Accept-Language': this.getLang()
    })
    return this.http.get(encodeURI(`api/${path}`), { params: params, headers })
      .pipe(map((r: any) => {
        if (r.response?.status === -1) {
          r.response.errors = { path: [{ errorMessage: r.response.errorMessage }] };
        }
        return r;

      }))
      .pipe(finalize(() => this.stopLoading()))
      .pipe(catchError(err => this.handleError(err, this)));
  }

  private post(path: string, body: any, options: any = {}): Observable<Object> {
    return this.http.post(encodeURI(`api/${path}`), body, options)
      .pipe(map((r: any) => {
        if (r.response?.status === -1) {
          r.response.errors = [{ errorMessage: r.response.errorMessage }];
        }
        return r;

      }))
      .pipe(finalize(() => this.stopLoading()))
      .pipe(catchError(err => this.handleError(err, this)));
  }

  stopLoading() {
    // this.numLoading--;
    // if (this.numLoading === 0 && this.spinnerTopRef.hasAttached()) {
    //   this.spinnerTopRef.detach();
    // }
  }

  private handleError(error: HttpErrorResponse, me: any) {
    //  console.log(error);
    if (error.status === 0) {
      // A client-side or network error occurred. Handle it accordingly.
      console.error('An error occurred:', error.error);
    } else if (error.status === 503 || error.status === 504) {
      // Forbiden. Redirect to login
      console.log("Service Unavailable");
      const url = me.router.routerState.snapshot.url;
      if (me.router) {
        me.router.navigate(['/login'], { url: url, err: '503' });
      }
    } else if (error.status === 403) {
      // Forbiden. Redirect to login
      console.log("Forbiden");
      // const url = me.router.routerState.snapshot.url;
      // if (me.router) {
      //   me.router.navigate(['/login'], {url: url});
      // }
    } else {
      console.error(
        `Backend returned code ${error.status}, body was: `, error.error);
    }
    // Return an observable with a user-facing error message.
    // return throwError({'status':error.status, 'message': error.message});
    return of({ response: { 'status': error.status, 'message': error.message, 'errors': [error.error] } });
  }

  searchTitles(params: HttpParams): Observable<any> {
    return this.get('index/titles', params)
  }

  searchAuthors(params: HttpParams): Observable<any> {
    return this.get('index/authors', params)
  }

  checkAutor(params: HttpParams): Observable<any> {
    return this.get('index/check_autor', params)
  }

  getLinks(params: HttpParams): Observable<any> {
    return this.get('index/get_links', params)
  }

  saveLabels(labels: Label[]): Observable<any> {
    return this.post('index/save_labels', labels);
  }

  saveDocLabels(pid: string, labels: string[]): Observable<any> {
    return this.post('index/save_doc_labels', {pid,labels});
  }
  
}
