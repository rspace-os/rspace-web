import { TZDate } from "react-day-picker";

export function dateInTimeZone(date: string, timeZone: string): Date {
  const [year, month, day] = date.split("-").map(Number);
  return new TZDate(year, month - 1, day, timeZone);
}
