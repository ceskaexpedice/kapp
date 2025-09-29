import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LabelsDialog } from './labels-dialog';

describe('LabelsDialog', () => {
  let component: LabelsDialog;
  let fixture: ComponentFixture<LabelsDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LabelsDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(LabelsDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
