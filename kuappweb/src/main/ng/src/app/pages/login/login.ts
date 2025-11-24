import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { switchMap, of } from 'rxjs';
import { AppState } from '../../app-state';

@Component({
  selector: 'app-login',
  imports: [],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {

  readonly route = inject(ActivatedRoute);
  state = inject(AppState);

  ngOnInit() {
    const s = this.route.queryParams.pipe(
      switchMap(p => {
        if (p['session_state']) {
          this.state.session_state = p['session_state'];
        }
        if (p['code']) {
          this.state.code = p['code'];
        }
        return of(true);
      })
    );
    s.subscribe();
  }
}
