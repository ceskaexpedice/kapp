import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContractDialog } from './contract-dialog';

describe('ContractDialog', () => {
  let component: ContractDialog;
  let fixture: ComponentFixture<ContractDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContractDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ContractDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
