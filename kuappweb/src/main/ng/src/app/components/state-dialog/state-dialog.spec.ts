import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StateDialog } from './state-dialog';

describe('StateDialog', () => {
  let component: StateDialog;
  let fixture: ComponentFixture<StateDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StateDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StateDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
