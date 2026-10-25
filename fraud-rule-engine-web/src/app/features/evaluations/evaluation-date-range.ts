export interface DateRange {
  from: string;
  through: string;
}

export interface UtcInterval {
  from: string;
  to: string;
}

const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;
export const MAX_QUERY_DAYS = 90;

export function defaultDateRange(now = new Date()): DateRange {
  const today = now.toISOString().slice(0, 10);
  const start = new Date(`${today}T00:00:00.000Z`);
  start.setUTCDate(start.getUTCDate() - 6);
  return { from: start.toISOString().slice(0, 10), through: today };
}

export function toUtcInterval(range: DateRange): UtcInterval | null {
  const fromMillis = parseUtcDate(range.from);
  const throughMillis = parseUtcDate(range.through);
  if (fromMillis === null || throughMillis === null || throughMillis < fromMillis) {
    return null;
  }

  const exclusiveToMillis = throughMillis + DAY_IN_MILLISECONDS;
  const spanDays = (exclusiveToMillis - fromMillis) / DAY_IN_MILLISECONDS;
  if (spanDays > MAX_QUERY_DAYS) {
    return null;
  }

  return {
    from: new Date(fromMillis).toISOString(),
    to: new Date(exclusiveToMillis).toISOString(),
  };
}

function parseUtcDate(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const timestamp = Date.parse(`${value}T00:00:00.000Z`);
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 10) !== value) {
    return null;
  }
  return timestamp;
}
