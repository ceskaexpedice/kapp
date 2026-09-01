import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AuthorTitleDialog } from './author-title-dialog';

describe('AuthorTitleDialog', () => {
  let component: AuthorTitleDialog;
  let fixture: ComponentFixture<AuthorTitleDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthorTitleDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AuthorTitleDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
