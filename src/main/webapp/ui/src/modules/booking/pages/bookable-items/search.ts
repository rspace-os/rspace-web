import { createLoader, parseAsBoolean } from "nuqs";

export function addBookableItemSearch(search: Record<string, unknown>): { target?: string } {
  return {
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  };
}

export const bookableItemEditParser = parseAsBoolean
  .withDefault(false)
  .withOptions({ history: "replace", clearOnDefault: true });

const loadBookableItemSearch = createLoader({ edit: bookableItemEditParser });

export function bookableItemSearch(search: Record<string, unknown>): { edit?: boolean } {
  const loaded = loadBookableItemSearch({
    edit: typeof search.edit === "string" || typeof search.edit === "boolean" ? String(search.edit) : undefined,
  });
  return loaded.edit ? { edit: true } : {};
}
