import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatIconModule} from '@angular/material/icon';
import {MatInputModule} from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { AppState } from '../../app-state';

@Component({
  selector: 'app-home',
  imports: [RouterModule, TranslateModule, FormsModule,
    MatFormFieldModule, MatIconModule, MatInputModule,
    MatCardModule, MatButtonModule],
  templateUrl: './home.html',
  styleUrl: './home.scss'
})
export class Home {


  constructor(
    private router: Router,
    public state: AppState
  ){}
doSearch() {
  this.router.navigate(['/results'])
}
}
