import axios from "@/common/axios";
import { groupByVersion } from "../../util/versionHistory";

/**
 * One audit revision of a Gallery item, as /gallery/ajax/versionHistory returns
 * it. Shaped like the inventory revisions response so that the two share the
 * version-grouping helper.
 */
type ApiRevision = {
  revisionId: number;
  record: {
    version?: number | null;
    lastModified?: string | null;
    modifiedByFullName?: string | null;
    size?: number | null;
    name?: string | null;
    description?: string | null;
  };
};

/** The endpoint's AjaxReturnObject envelope. */
type VersionHistoryResponse = {
  data: { revisions: Array<ApiRevision> } | null;
  error?: { errorMessages?: Array<string> } | null;
};

/**
 * One user-facing version of a Gallery item. Every field describes the version,
 * not the item: see CONTEXT.md, "Version". A null `description` means the
 * audited revision carried none.
 */
export type VersionRow = {
  version: number;
  lastModified: string | null;
  modifiedByFullName: string | null;
  size: number | null;
  name: string | null;
  description: string | null;
};

/**
 * A Gallery item's versions, newest first. Read by the version-history dialog
 * and by the pinned version view. `loadFailedMessage` is passed in so the
 * wording stays with the caller that has a `t`.
 */
export async function fetchVersionHistory(fileId: string, loadFailedMessage: string): Promise<Array<VersionRow>> {
  const { data } = await axios.get<VersionHistoryResponse>(`/gallery/ajax/versionHistory/${fileId}`);
  if (!data.data) {
    // a 200 carrying an error envelope must not read as "no version history yet"
    throw new Error(data.error?.errorMessages?.[0] ?? loadFailedMessage);
  }
  return groupByVersion(data.data.revisions).map(({ version, revision }) => ({
    version,
    lastModified: revision.record.lastModified ?? null,
    modifiedByFullName: revision.record.modifiedByFullName ?? null,
    size: revision.record.size ?? null,
    name: revision.record.name ?? null,
    description: revision.record.description ?? null,
  }));
}
