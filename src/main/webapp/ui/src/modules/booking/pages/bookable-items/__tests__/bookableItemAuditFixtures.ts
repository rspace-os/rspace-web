export const snapshotFingerprint = "b".repeat(64);

type AuditPageOptions = {
  page?: number;
  totalPages?: number;
  hasPrevPage?: boolean;
  hasNextPage?: boolean;
};

export function auditPage(docs: readonly unknown[], options: AuditPageOptions = {}) {
  const page = options.page ?? 1;
  const totalPages = options.totalPages ?? 1;
  return {
    docs,
    totalDocs: totalPages * 20,
    limit: 20,
    page,
    pagingCounter: (page - 1) * 20 + 1,
    totalPages,
    hasPrevPage: options.hasPrevPage ?? page > 1,
    hasNextPage: options.hasNextPage ?? page < totalPages,
    prevPage: page > 1 ? page - 1 : null,
    nextPage: page < totalPages ? page + 1 : null,
    snapshotDate: "2026-08-25",
    snapshotFingerprint,
  };
}
