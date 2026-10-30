import { useState } from "react";

/**
 * Client-side pagination over an already-filtered/sorted array. The page actually shown is derived
 * at render time from the current item count rather than stored, so a page number left over from a
 * longer list never points past the end of a shorter one (e.g. once a status mutation elsewhere
 * shrinks the result set) - no effect is needed to notice and correct that after the fact.
 */
export function useClientPagination<T>(items: ReadonlyArray<T>, defaultPageSize: number) {
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSizeState] = useState(defaultPageSize);

  const lastPage = Math.max(0, Math.ceil(items.length / pageSize) - 1);
  const page = Math.min(pageNumber, lastPage);
  const pagedItems = items.slice(page * pageSize, page * pageSize + pageSize);

  const setPageSize = (newPageSize: number) => {
    setPageSizeState(newPageSize);
    setPageNumber(0);
  };

  return {
    pagedItems,
    page,
    pageSize,
    setPage: setPageNumber,
    setPageSize,
    /** Call from any handler that changes what "the list" even is (a filter, a search, a sort). */
    reset: () => setPageNumber(0),
  };
}
