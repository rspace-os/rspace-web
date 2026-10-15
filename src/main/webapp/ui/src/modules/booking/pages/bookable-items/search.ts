import { bookingFormSearch } from "../bookingFormSearch";

export function addBookableItemSearch(search: Record<string, unknown>): { target?: string } {
  const { target } = bookingFormSearch(search);
  return target === undefined ? {} : { target };
}

export function bookableItemSearch(search: Record<string, unknown>): { edit?: boolean } {
  return search.edit === true || search.edit === "true" ? { edit: true } : {};
}
