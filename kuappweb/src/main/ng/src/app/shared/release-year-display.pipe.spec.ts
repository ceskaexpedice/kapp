import { ReleaseYearDisplayPipe } from './release-year-display.pipe';

describe('ReleaseYearDisplayPipe', () => {
  const pipe = new ReleaseYearDisplayPipe();

  it('adds one year for display', () => {
    expect(pipe.transform(2026)).toBe(2027);
  });

  it('keeps missing values empty', () => {
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeUndefined();
  });
});
