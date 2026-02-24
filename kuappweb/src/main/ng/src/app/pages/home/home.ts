import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { AppState } from '../../app-state';
import { AppService } from '../../app.service';

@Component({
  selector: 'app-home',
  imports: [RouterModule, TranslateModule, FormsModule,
    MatFormFieldModule, MatIconModule, MatInputModule,
    MatCardModule, MatButtonModule],
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home {

  readonly service = inject(AppService);

  q: string;
  // titles = signal<number>(0);
  // authors = signal<number>(0);
  totals = signal<{titles:number, authors: number, contracts: number}>(null);
  constructor(
    private router: Router,
    public state: AppState
  ) { }

  ngOnInit() {
    this.getHome();
  }

  getHome() {
    this.service.getHome().subscribe((res: any) => {
      this.totals.set(res)
    })
  }

  doSearch(target: string) {

    const params = {
      q: this.q
    }
    this.router.navigate(['/' + target], { queryParams: params, queryParamsHandling: 'merge' });
  }

  newImported() {

    this.state.usedFacets.set([
      { field: 'labels', value: 'new_imported' }
    ]);
    this.doSearch('titles');

  }

  toControll() {

    this.state.usedFacets.set([
      { field: 'state', value: 'to_control' }
    ]);
    this.doSearch('titles');
  }

  contracts() {

    const params = {
      q: this.q
    }
    this.router.navigate(['/contracts'], { queryParams: params, queryParamsHandling: 'merge' });
  }

}
