import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  type LinkedRecords,
  type PrivateLinkedRecordsByOwner,
  type ReadableLinkedRecord,
  type WorkspaceLinkedRecord,
  WorkspaceLinkedRecordsResponseSchema,
} from "@/modules/workspace/schema";
import {
  WORKSPACE_API_BASE_URL,
  toWorkspaceError,
} from "@/modules/workspace/utils";

const GALLERY_API_BASE_URL = "/gallery";

/**
 * Reduce a raw `getLinkedByRecords` / `getLinkedDocuments` list into the readable rows
 * and the per-owner count of unreadable ("private") rows. A row is readable iff it
 * carries an `id` (the server omits id/oid for records the caller cannot read and
 * returns only the owner's name). Mirrors the aggregation in `recordInfoPanel.js`.
 */
function splitLinkedRecords(
  rows: ReadonlyArray<WorkspaceLinkedRecord>,
): LinkedRecords {
  const readable: Array<ReadableLinkedRecord> = [];
  const privateCounts = new Map<string, number>();

  for (const row of rows) {
    if (row.id != null && row.oid) {
      readable.push({
        globalId: row.oid.idString,
        name: row.name ?? "",
        ownerFullName: row.ownerFullName ?? null,
      });
    } else {
      const owner = row.ownerFullName ?? "";
      privateCounts.set(owner, (privateCounts.get(owner) ?? 0) + 1);
    }
  }

  const privateByOwner: Array<PrivateLinkedRecordsByOwner> = [];
  for (const [ownerFullName, count] of privateCounts) {
    privateByOwner.push({ ownerFullName, count });
  }

  return { readable, privateByOwner };
}

async function fetchLinkedRecords(
  url: string,
  fallbackMessage: string,
): Promise<LinkedRecords> {
  const response = await fetch(url, {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  const data: unknown = await response.json();

  if (!response.ok) {
    throw toWorkspaceError(data, fallbackMessage);
  }

  const parsed = parseOrThrow(WorkspaceLinkedRecordsResponseSchema, data);

  if (parsed.data === null) {
    throw toWorkspaceError(parsed, fallbackMessage);
  }

  return splitLinkedRecords(parsed.data);
}

/**
 * Fetch the ELN documents that link TO the given record, via
 * `GET /workspace/getLinkedByRecords?targetRecordId={id}`. The id is the numeric DB id
 * (strip the global-id prefix before calling).
 */
export function getLinkedByRecords(
  targetRecordId: number,
): Promise<LinkedRecords> {
  const searchParams = new URLSearchParams();
  searchParams.set("targetRecordId", String(targetRecordId));
  return fetchLinkedRecords(
    `${WORKSPACE_API_BASE_URL}/getLinkedByRecords?${searchParams.toString()}`,
    "Failed to fetch linked-by records.",
  );
}

/**
 * Fetch the ELN documents that reference the given gallery media file, via
 * `GET /gallery/ajax/getLinkedDocuments/{mediaId}`. The id is the numeric DB id of the
 * gallery file.
 */
export function getLinkedDocuments(mediaId: number): Promise<LinkedRecords> {
  return fetchLinkedRecords(
    `${GALLERY_API_BASE_URL}/ajax/getLinkedDocuments/${mediaId}`,
    "Failed to fetch linked documents.",
  );
}
