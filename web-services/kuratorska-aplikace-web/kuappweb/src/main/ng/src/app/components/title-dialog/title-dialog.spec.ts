import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TitleDialog } from './title-dialog';

describe('TitleDialog', () => {
  let component: TitleDialog;
  let fixture: ComponentFixture<TitleDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TitleDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TitleDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
