import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthorItem } from './author-item';

describe('AuthorItem', () => {
  let component: AuthorItem;
  let fixture: ComponentFixture<AuthorItem>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthorItem]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorItem);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
