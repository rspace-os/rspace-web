export function addBookableItemSearch(search: Record<string, unknown>): { target?: string } {
  return {
    ...(typeof search.target === "string" && /^IN\d+$/.test(search.target) ? { target: search.target } : {}),
  };
}

export function bookableItemSearch(search: Record<string, unknown>): { edit?: boolean } {
  return search.edit === true || search.edit === "true" ? { edit: true } : {};
}
