import { HttpClient, HttpHeaders, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map, finalize, catchError, of } from 'rxjs';
import { Label } from './shared/label';
import { Batch } from './shared/batch';
import { Author } from './shared/author';
import { Contract } from './shared/contract';

@Injectable({
  providedIn: 'root'
})
export class AppService {

  constructor(
    private http: HttpClient) { }

  private get(path: string, params = {}, responseType: any = null): Observable<Object> {
    const headers = new HttpHeaders({
      //'Accept-Language': this.getLang()
    })
    return this.http.get(encodeURI(`api/${path}`), { params: params, headers, responseType })
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

  getHome(): Observable<any> {
    return this.get('index/home');
  }

  searchTitles(params: HttpParams, csv: boolean = false): Observable<any> {
    
    const headers = csv ? 'plain/text' : null;
    
    return this.get('index/titles', params, headers)
  }

  getDoc(pid: string): Observable<any> {
    return this.get('index/get_doc', {pid});
  }

  getRelated(pid: string): Observable<any> {
    return this.get('index/get_related', {pid});
  }

  searchAuthors(params: HttpParams): Observable<any> {
    return this.get('index/authors', params)
  }

  checkAuthor(params: HttpParams): Observable<any> {
    return this.get('index/check_author', params)
  }

  getAuthorById(params: HttpParams): Observable<any> {
    return this.get('index/get_author_by_id', params);
  }

  getLinks(params: HttpParams): Observable<any> {
    return this.get('index/get_links', params)
  }

  saveDK(dk: string[]): Observable<any> {
    return this.post('index/save_dk', dk);
  }

  saveLicenses(licenses: string[]): Observable<any> {
    return this.post('index/save_licenses', licenses);
  }

  saveLabels(labels: Label[]): Observable<any> {
    return this.post('index/save_labels', labels);
  }

  saveDocLabels(pid: string, labels: string[]): Observable<any> {
    return this.post('index/save_doc_labels', {pid,labels});
  }

  saveBatches(batches: Batch[]): Observable<any> {
    return this.post('index/save_batches', batches);
  }

  saveDocBatch(pid: string, batch: string): Observable<any> {
    return this.post('index/save_doc_batch', {pid,batch});
  }

  saveDocContract(pid: string, contract: string, withState: boolean, selected: boolean, licenses: string[]): Observable<any> {
    return this.post('index/save_doc_contract', {pid,contract,withState,selected, licenses});
  }

  saveTitleColumns(columns: {field: string, selected: boolean}[]): Observable<any> {
    return this.post('index/save_title_columns', columns);
  }

  saveAuthor(author: Author): Observable<any> {
    return this.post('index/save_author', author);
  }

  saveDecisionDate(pid: string, decisionDate: number): Observable<any> {
    return this.post('index/save_decision_date', {pid,decisionDate});
  }

  createBatchFromLabel(label: string): Observable<any> {
    return this.post('index/create_batch_from_label', {label});
  }

  getUsedBatches(): Observable<any> {
    return this.get('index/get_used_batches');
  }

  getDocsInBatch(batch: string): Observable<any> {
    return this.get('index/get_batch_docs', {batch});
  }

  changeState(state: any): Observable<any> {
    return this.post('index/change_state', state);
  }

  changeStateInBatch(state: any): Observable<any> {
    return this.post('index/change_state_in_batch', state);
  }

  login(): Observable<any> {
    return this.post('user/login', {});
  }

  searchContracts(params: HttpParams): Observable<any> {
    return this.get('index/contracts', params);
  }

  searchTitlesByContract(params: HttpParams): Observable<any> {
    return this.get('index/titles_contract', params);
  }

  findTitlesByContract(params: HttpParams): Observable<any> {
    return this.get('index/titles_contract_find', params);
  }

  getContract(id: string): Observable<any> {
    return this.get('index/get_contract', {id});
  }

  uploadContract(form: any): Observable<any> {
    return this.post('index/upload_batch', form);
  }

  saveContract(c: Contract): Observable<any> {
    return this.post('index/save_contract', c);
  }

  analyzeContract(c: string): Observable<any> {
    return this.get('index/analyze_contract', {filename:c});
  }

  checkContractExists(filename: string, ) {
    const url = `/index/check_contract_exists?filename=${filename}`;
    return this.get(url);
  }
  
}
