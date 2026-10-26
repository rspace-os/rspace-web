import { bookingFormSearch } from "../bookingFormSearch";

const availabilityModes = ["available-now", "free-later-today"] as const;

function stringList(value: unknown, pattern: RegExp): string[] {
  const values = Array.isArray(value) ? value : typeof value === "string" ? [value] : [];
  return values.filter((entry): entry is string => typeof entry === "string" && pattern.test(entry));
}

export function allBookableItemsSearch(search: Record<string, unknown>) {
  const availability = availabilityModes.find((mode) => mode === search.availability);
  const types = stringList(search.types, /^[A-Z][A-Z0-9_]*$/);
  const page = typeof search.page === "number" ? search.page : Number(search.page);
  const pageSize = Number(search.pageSize);
  return {
    ...bookingFormSearch(search),
    ...(availability ? { availability } : {}),
    ...(search.mine === true || search.mine === "true" ? { mine: true } : {}),
    ...(typeof search.where === "string" && search.where.trim() ? { where: search.where } : {}),
    ...(typeof search.q === "string" && search.q.trim() ? { q: search.q.slice(0, 255) } : {}),
    ...(types.length > 0 ? { types } : {}),
    ...(Number.isSafeInteger(page) && page > 1 ? { page } : {}),
    ...([10, 20, 30, 40, 50].includes(pageSize) ? { pageSize } : {}),
  };
}
