import { Component, input } from '@angular/core';

@Component({
  selector: 'app-author-item',
  imports: [],
  templateUrl: './author-item.html',
  styleUrl: './author-item.scss'
})
export class AuthorItem {

  doc = input<any>();
}
