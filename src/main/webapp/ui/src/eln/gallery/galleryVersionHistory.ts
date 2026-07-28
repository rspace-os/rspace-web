import axios from "@/common/axios";
import { groupByVersion } from "../../util/versionHistory";

/**
 * One audit revision of a Gallery item, as /gallery/ajax/versionHistory returns
 * it. Shaped like the inventory revisions response so that the two share the
 * version-grouping helper.
 */
type ApiRevision = {
  revisionId: number;
  revisionType?: string | null;
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
  data: { revisions: Array<ApiRevision>; revisionsCount: number } | null;
  error?: { errorMessages?: Array<string> } | null;
};

/**
 * One user-facing version of a Gallery item.
 *
 * `name` and `description` are per-version, not per-item: uploading a new version
 * can replace the file with one of a different name, and either can be edited at
 * any time. So the live item's name and description say nothing about what an
 * earlier version carried.
 *
 * A null `description` means the audited revision had none, which is why it is
 * kept distinct from the empty string only in so far as both display as no
 * description.
 */
export type VersionRow = {
  version: number;
  revisionId: number;
  lastModified: string | null;
  modifiedByFullName: string | null;
  size: number | null;
  name: string | null;
  description: string | null;
};

/**
 * A Gallery item's versions, newest first.
 *
 * Both the version-history dialog and the pinned version view read this: the
 * dialog to list the versions, the view to learn the size and date of the one it
 * is pinned to. `loadFailedMessage` is passed in so the wording stays with the
 * caller that has a `t`.
 */
export async function fetchVersionHistory(fileId: string, loadFailedMessage: string): Promise<Array<VersionRow>> {
  const { data } = await axios.get<VersionHistoryResponse>(`/gallery/ajax/versionHistory/${fileId}`);
  if (!data.data) {
    // a 200 carrying an error envelope must not read as "no version history yet"
    throw new Error(data.error?.errorMessages?.[0] ?? loadFailedMessage);
  }
  return groupByVersion(data.data.revisions).map(({ version, revision }) => ({
    version,
    revisionId: revision.revisionId,
    lastModified: revision.record.lastModified ?? null,
    modifiedByFullName: revision.record.modifiedByFullName ?? null,
    size: revision.record.size ?? null,
    name: revision.record.name ?? null,
    description: revision.record.description ?? null,
  }));
}
