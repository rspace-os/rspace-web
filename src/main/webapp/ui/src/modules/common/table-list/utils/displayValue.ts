import { isNil } from "es-toolkit";
import { formatList } from "@/modules/common/i18n/listFormat";
import { formatDate, formatDateTime, formatNumber, formatTime } from "@/modules/common/i18n/localeFormat";

/** How a cell value is localised: numbers, instants, calendar dates and wall-clock times. */
export type DisplayFormat = "number" | "dateTime" | "date" | "time";

/** The active language and its yes and no words, read from the rendering i18next instance. */
export type DisplayLocale = { language: string | undefined; yes: string; no: string };

export function displayValue(value: unknown, locale: DisplayLocale, format?: DisplayFormat): string {
  const { language } = locale;
  if (isNil(value)) return "";
  if (value instanceof Date) return formatDateTime(value, language);
  if (typeof value === "number") return format === "number" ? formatNumber(value, language) : String(value);
  if (typeof value === "string") {
    if (format === "dateTime") return formatDateTime(value, language);
    if (format === "date") return formatDate(value, language);
    if (format === "time") return formatTime(value, language);
    return value;
  }
  if (typeof value === "boolean") return value ? locale.yes : locale.no;
  if (Array.isArray(value))
    return formatList(
      value.map((item) => displayValue(item, locale, format)),
      language,
      { type: "unit", style: "short" },
    );
  if (typeof value === "object") {
    if ("name" in value && typeof value.name === "string") return value.name;
    if ("title" in value && typeof value.title === "string") return value.title;
    if ("id" in value) return String(value.id);
  }
  return String(value);
}
