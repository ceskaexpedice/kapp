import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddToBatchDialog } from './add-to-batch-dialog';

describe('AddToBatchDialog', () => {
  let component: AddToBatchDialog;
  let fixture: ComponentFixture<AddToBatchDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddToBatchDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AddToBatchDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
