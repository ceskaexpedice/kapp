import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ColumnsSettingsDialog } from './columns-settings-dialog';

describe('ColumnsSettingsDialog', () => {
  let component: ColumnsSettingsDialog;
  let fixture: ComponentFixture<ColumnsSettingsDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ColumnsSettingsDialog]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ColumnsSettingsDialog);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
