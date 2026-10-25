import { defaultDateRange, toUtcInterval } from './evaluation-date-range';

describe('evaluation date range', () => {
  it('defaults to the last seven UTC calendar days including today', () => {
    expect(defaultDateRange(new Date('2026-10-22T17:00:00.000Z'))).toEqual({
      from: '2026-10-16',
      through: '2026-10-22',
    });
  });

  it('converts inclusive date inputs to a half-open UTC interval', () => {
    expect(toUtcInterval({ from: '2026-10-01', through: '2026-10-01' })).toEqual({
      from: '2026-10-01T00:00:00.000Z',
      to: '2026-10-02T00:00:00.000Z',
    });
  });

  it('allows exactly 90 days and rejects longer or reversed ranges', () => {
    expect(toUtcInterval({ from: '2026-01-01', through: '2026-03-31' })).not.toBeNull();
    expect(toUtcInterval({ from: '2026-01-01', through: '2026-04-01' })).toBeNull();
    expect(toUtcInterval({ from: '2026-02-02', through: '2026-02-01' })).toBeNull();
  });

  it('rejects invalid calendar dates', () => {
    expect(toUtcInterval({ from: '2026-02-30', through: '2026-03-01' })).toBeNull();
  });
});
