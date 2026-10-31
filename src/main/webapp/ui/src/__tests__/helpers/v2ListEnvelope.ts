export function v2ListEnvelope<T>(docs: T[], totals = false) {
  return {
    docs,
    ...(totals ? { totalDocs: docs.length, totalPages: docs.length === 0 ? 0 : 1 } : {}),
    limit: 1,
    page: 1,
    pagingCounter: 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}
