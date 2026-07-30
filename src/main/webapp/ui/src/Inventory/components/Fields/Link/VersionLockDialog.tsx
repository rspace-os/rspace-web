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
  revisionType: string;
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

/** The gallery endpoint's AjaxReturnObject envelope around the same revision shape. */
interface AjaxRevisionList {
  data: ApiRevisionList | null;
}

/**
 * Collapses audit rows to one picker row per user-facing version (several revisions can share one
 * version), pinning the version and carrying its newest revision. Shared by the inventory and
 * gallery branches, whose responses have the same shape.
 */
function toVersionRecords(revisions: ApiRevisionEntry[]): VersionRecord[] {
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
  version: number;
  revision: number;
  modificationDate?: string;
}

interface ElnRevisionHistoryResponse {
  data: ElnRevisionEntry[];
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

  const fetchVersions = useCallback(async (): Promise<VersionRecord[]> => {
    if (!parsed) return [];
    try {
      if (parsed.prefix === "SD") {
        // ELN document revisions come from the workspace endpoint, not the inventory
        // API. Pin to the document version number (entry.version); carry the audit id.
        const { data } = await axios.get<ElnRevisionHistoryResponse>(
          `/workspace/revisionHistory/ajax/${parsed.id}/versions`,
        );
        // Several audit revisions can share one document version: non-version-bumping
        // edits, and the soft-delete MOD of a deleted document (a delete does not bump the
        // version). Collapse to one row per version, keeping the newest revision of each,
        // so the final version is not listed twice (mirrors the inventory path below).
        const byVersion = new Map<number, VersionRecord>();
        for (const entry of data.data.toSorted((a, b) => a.revision - b.revision)) {
          byVersion.set(entry.version, {
            version: entry.version,
            revisionId: entry.revision,
            modificationDate: entry.modificationDate ?? "",
          });
        }
        return [...byVersion.values()].sort((a, b) => b.version - a.version);
      }
      if (parsed.prefix === "GL") {
        // Gallery revisions come from the ELN gallery endpoint (RSDEV-1250), which wraps the same
        // revision shape as the inventory API in an AjaxReturnObject.
        const { data } = await axios.get<AjaxRevisionList>(`/gallery/ajax/versionHistory/${parsed.id}`);
        return toVersionRecords(data.data?.revisions ?? []);
      }
      if (parsed.inventoryPathSegment) {
        const { data } = await ApiService.get<ApiRevisionList>(`${parsed.inventoryPathSegment}/${parsed.id}/revisions`);
        return toVersionRecords(data.revisions);
      }
      return [];
    } catch {
      // A failed revisions fetch degrades to the latest-only view rather than
      // surfacing an unhandled rejection through the picker.
      return [];
    }
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
