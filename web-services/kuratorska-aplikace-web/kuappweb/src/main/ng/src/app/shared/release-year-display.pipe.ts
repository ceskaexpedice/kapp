import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'releaseYearDisplay',
  standalone: true
})
export class ReleaseYearDisplayPipe implements PipeTransform {
  transform(value: number | null | undefined): number | null | undefined {
    return value == null ? value : value + 1;
  }
}
