import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import ApiService from "../../../../common/InvApiService";
import VersionLockPicker, {
  LATEST_SELECTION,
  type VersionLockSelection,
  type VersionRecord,
} from "../../../../components/VersionLockPicker/VersionLockPicker";
import { groupByVersion } from "../../../../util/versionHistory";

export interface VersionLockDialogProps {
  open: boolean;
  globalId: string;
  /**
   * Current versionPin on the link. null means "latest" (unpinned).
   */
  currentVersionPin: number | null;
  /** Called with the chosen versionPin (the user-facing version) or null for latest. */
  onConfirm: (versionPin: number | null) => void;
  onCancel: () => void;
}

import { GLOBAL_ID_PATTERN, INVENTORY_PREFIX_TO_API_PATH } from "./linkTarget";

interface ParsedTarget {
  prefix: string;
  id: number;
  /**
   * Inventory revisions path segment (samples/subSamples/...), or null for ELN targets
   * (e.g. SD) whose revisions come from the ELN endpoint instead of the inventory API.
   */
  inventoryPathSegment: string | null;
}

function parseGlobalId(globalId: string): ParsedTarget | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  const prefix = match[1];
  return {
    prefix,
    id: Number(match[2]),
    inventoryPathSegment: INVENTORY_PREFIX_TO_API_PATH[prefix] ?? null,
  };
}

/**
 * Targets whose revision history this dialog can resolve: inventory items, SD documents and GL
 * gallery files.
 */
function isSupportedTarget(parsed: ParsedTarget): boolean {
  return parsed.inventoryPathSegment !== null || parsed.prefix === "SD" || parsed.prefix === "GL";
}

interface ApiRevisionEntry {
  revisionId: number;
  revisionType?: string;
  record: {
    // The user-facing version of the item at this audit revision. Several revisions can share
    // one version (non-version-bumping edits), so callers must group by it, not by revisionId.
    version?: number | null;
    lastModified?: string;
  };
}

interface ApiRevisionList {
  revisions: ApiRevisionEntry[];
  revisionsCount: number;
}

/**
 * An `AjaxReturnObject` envelope, which both ELN endpoints behind this dialog return. Failure is
 * signalled by a 200 carrying `data: null` and the reason in `error`, so both halves are modelled:
 * reading the payload without checking would turn a failed fetch into an empty version history.
 */
interface AjaxEnvelope<T> {
  data: T | null;
  error?: { errorMessages?: string[] } | null;
}

/**
 * The payload of an `AjaxReturnObject`, rejecting when the envelope reports a failure instead.
 * Mirrors how the workspace record-information query treats the same envelope
 * (`modules/workspace/queries.ts`).
 */
function payloadOrThrow<T>(envelope: AjaxEnvelope<T> | undefined, fallbackMessage: string): T {
  const payload = envelope?.data;
  if (payload === null || payload === undefined) {
    throw new Error(envelope?.error?.errorMessages?.[0] ?? fallbackMessage);
  }
  return payload;
}

/**
 * Collapses audit rows to one picker row per user-facing version (several revisions can share one
 * version), pinning the version and carrying its newest revision. Shared by all three branches,
 * whose responses reduce to the same list of audit rows.
 *
 * A response with no list at all is a broken response, not an item without versions, so it rejects
 * rather than resolving to an empty picker: an empty picker states "only the latest version exists",
 * which is a claim this function cannot make on that evidence.
 */
function toVersionRecords(revisions: ApiRevisionEntry[] | null | undefined): VersionRecord[] {
  if (!Array.isArray(revisions)) {
    throw new Error("version history response carried no revisions");
  }
  return groupByVersion(revisions).map(({ version, revision }) => ({
    version,
    revisionId: revision.revisionId,
    modificationDate: revision.record.lastModified ?? "",
  }));
}

// ELN revisions endpoint shape (/workspace/revisionHistory/ajax/{id}/versions). Each entry
// carries the document `version` number (used to pin, e.g. SD123v2) and a separate audit
// `revision` id. Mirrors tinyMCE/InternalLink.tsx.
interface ElnRevisionEntry {
  version?: number | null;
  revision: number;
  modificationDate?: string;
}

/** One ELN document revision as an audit row, so it groups by version like the other two paths. */
function elnEntryAsAuditRow(entry: ElnRevisionEntry): ApiRevisionEntry {
  return {
    revisionId: entry.revision,
    record: { version: entry.version, lastModified: entry.modificationDate },
  };
}

/**
 * Modal hosting the shared VersionLockPicker. Used by inventory link fields to
 * pin a link to a specific revision of the target item, or release it back to
 * latest.
 */
export default function VersionLockDialog(props: VersionLockDialogProps): React.ReactElement | null {
  const { t } = useTranslation(["inventory", "common"]);
  const parsed = React.useMemo(() => parseGlobalId(props.globalId), [props.globalId]);
  const initialSelection: VersionLockSelection =
    props.currentVersionPin == null ? LATEST_SELECTION : props.currentVersionPin;
  const [selection, setSelection] = useState<VersionLockSelection>(initialSelection);

  // The component instance stays mounted while closed (open=false renders
  // null but keeps hook state), so an abandoned selection from a previous
  // open would otherwise leak into the next one. Re-sync on each open.
  useEffect(() => {
    if (props.open) {
      setSelection(props.currentVersionPin == null ? LATEST_SELECTION : props.currentVersionPin);
    }
  }, [props.open, props.currentVersionPin]);

  /**
   * Resolves the target's versions, and rejects when they cannot be fetched. Failures are
   * deliberately not caught here: VersionLockPicker handles a rejection by falling back to the
   * latest-only view AND telling the user the history could not be loaded, which swallowing the
   * error would turn into a silent "this item has only one version".
   */
  const fetchVersions = useCallback(async (): Promise<VersionRecord[]> => {
    if (!parsed) return [];
    if (parsed.prefix === "SD") {
      // ELN document revisions come from the workspace endpoint, not the inventory
      // API. Pin to the document version number (entry.version); carry the audit id.
      const { data } = await axios.get<AjaxEnvelope<ElnRevisionEntry[]>>(
        `/workspace/revisionHistory/ajax/${parsed.id}/versions`,
      );
      const entries = payloadOrThrow(data, "document version history unavailable");
      if (!Array.isArray(entries)) {
        throw new Error("document version history was not a list of revisions");
      }
      // Several audit revisions can share one document version: non-version-bumping
      // edits, and the soft-delete MOD of a deleted document (a delete does not bump the
      // version). Collapsing is the shared helper's job, so the three paths cannot disagree.
      return toVersionRecords(entries.map(elnEntryAsAuditRow));
    }
    if (parsed.prefix === "GL") {
      // Gallery revisions come from the ELN gallery endpoint (RSDEV-1250), which wraps the same
      // revision shape as the inventory API in an AjaxReturnObject.
      const { data } = await axios.get<AjaxEnvelope<ApiRevisionList>>(`/gallery/ajax/versionHistory/${parsed.id}`);
      return toVersionRecords(payloadOrThrow(data, "gallery version history unavailable").revisions);
    }
    if (parsed.inventoryPathSegment) {
      const { data } = await ApiService.get<ApiRevisionList>(`${parsed.inventoryPathSegment}/${parsed.id}/revisions`);
      return toVersionRecords(data.revisions);
    }
    return [];
  }, [parsed?.prefix, parsed?.id, parsed?.inventoryPathSegment]);

  if (!props.open) return null;
  if (!parsed || !isSupportedTarget(parsed)) {
    return (
      <Dialog open onClose={props.onCancel} fullWidth maxWidth="sm">
        <DialogTitle>{t("fields.link.versionLock.title")}</DialogTitle>
        <DialogContent>{t("fields.link.versionLock.cannotResolve", { globalId: props.globalId })}</DialogContent>
        <DialogActions>
          <Button onClick={props.onCancel}>{t("common:actions.close")}</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog
      open={props.open}
      onClose={props.onCancel}
      aria-label={t("fields.link.versionLock.label", { globalId: props.globalId })}
      fullWidth
      maxWidth="md"
    >
      <DialogTitle>{t("fields.link.versionLock.titleFor", { globalId: props.globalId })}</DialogTitle>
      <DialogContent>
        <VersionLockPicker
          recordId={parsed.id}
          currentSelection={selection}
          fetchVersions={fetchVersions}
          onChange={setSelection}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onCancel}>{t("common:actions.cancel")}</Button>
        <Button
          color="callToAction"
          disableElevation
          variant="contained"
          onClick={() => props.onConfirm(selection === LATEST_SELECTION ? null : selection)}
        >
          {t("fields.link.versionLock.lockToSelectedVersion")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
