import type { ListMaintenances200 } from "@/modules/common/api/v2/generated/models/listMaintenances200";

export function v2ListEnvelope<T>(docs: T[], totals = false): Omit<ListMaintenances200, "docs"> & { docs: T[] } {
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
