export function auditPage(docs: readonly unknown[], options: { limit?: number; truncated?: boolean } = {}) {
  return {
    docs,
    limit: options.limit ?? 1000,
    truncated: options.truncated ?? false,
    start: "2026-08-01T00:00:00Z",
    endExclusive: "2026-08-26T00:00:00Z",
  };
}

// Timestamp serialized by BookingAuditTrailTest.permanentDeleteSnapshotSerializesItsTimestampForTheAuditLog.
export const permanentDeleteEvent = {
  action: "DELETE",
  description: null,
  payload: { target: { type: "INSTRUMENT", id: 7 }, state: "ARCHIVED", deletedAt: "2026-09-01T12:00:00Z" },
};
