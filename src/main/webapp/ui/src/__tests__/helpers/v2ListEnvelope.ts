export function v2ListEnvelope<T>(docs: T[]) {
  return {
    docs,
    totalDocs: docs.length,
    limit: 1,
    page: 1,
    pagingCounter: 1,
    totalPages: docs.length === 0 ? 0 : 1,
    hasPrevPage: false,
    hasNextPage: false,
    prevPage: null,
    nextPage: null,
  };
}
