import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterOutlet } from '@angular/router';
import { Footer } from "./components/footer/footer";
import { Navbar } from "./components/navbar/navbar";
import { switchMap, of } from 'rxjs';
import { AppState } from './app-state';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Footer, Navbar],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly route = inject(ActivatedRoute);
  state = inject(AppState);
  
  ngOnInit() {
    const s = this.route.queryParams.pipe(
      switchMap(p => {
        this.processParams(p);
        return of(true);
      })
    );
    s.subscribe();
  }

  processParams(p: any) {
    this.state.q = p['q'] ? p['q'] : '';
  }
}
