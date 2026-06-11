import React, { useCallback, useEffect, useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import ApiService from "../../../../common/InvApiService";
import axios from "@/common/axios";
import VersionLockPicker, {
  LATEST_SELECTION,
  type VersionLockSelection,
  type VersionRecord,
} from "../../../../components/VersionLockPicker/VersionLockPicker";

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

const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v\d+)?$/;
const INVENTORY_PREFIX_TO_PATH: Record<string, string> = {
  SA: "samples",
  SS: "subSamples",
  IC: "containers",
  IN: "instruments",
  IT: "sampleTemplates",
};

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
    inventoryPathSegment: INVENTORY_PREFIX_TO_PATH[prefix] ?? null,
  };
}

/** Targets whose revision history this dialog can resolve: inventory items and SD documents. */
function isSupportedTarget(parsed: ParsedTarget): boolean {
  return parsed.inventoryPathSegment !== null || parsed.prefix === "SD";
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
export default function VersionLockDialog(
  props: VersionLockDialogProps,
): React.ReactElement | null {
  const parsed = parseGlobalId(props.globalId);
  const initialSelection: VersionLockSelection =
    props.currentVersionPin == null
      ? LATEST_SELECTION
      : props.currentVersionPin;
  const [selection, setSelection] =
    useState<VersionLockSelection>(initialSelection);

  // The component instance stays mounted while closed (open=false renders
  // null but keeps hook state), so an abandoned selection from a previous
  // open would otherwise leak into the next one. Re-sync on each open.
  useEffect(() => {
    if (props.open) {
      setSelection(
        props.currentVersionPin == null
          ? LATEST_SELECTION
          : props.currentVersionPin,
      );
    }
  }, [props.open, props.currentVersionPin]);

  const fetchVersions = useCallback(
    async (): Promise<VersionRecord[]> => {
      if (!parsed) return [];
      try {
        if (parsed.prefix === "SD") {
          // ELN document revisions come from the workspace endpoint, not the inventory
          // API. Pin to the document version number (entry.version); carry the audit id.
          const { data } = await axios.get<ElnRevisionHistoryResponse>(
            `/workspace/revisionHistory/ajax/${parsed.id}/versions`,
          );
          return data.data.map((entry) => ({
            version: entry.version,
            revisionId: entry.revision,
            modificationDate: entry.modificationDate ?? "",
          }));
        }
        if (parsed.inventoryPathSegment) {
          const { data } = await ApiService.get<ApiRevisionList>(
            `${parsed.inventoryPathSegment}/${parsed.id}/revisions`,
          );
          // Audit rows carry the user-facing record.version; non-version-bumping edits create
          // several revisions sharing one version. Collapse to one row per version, keeping the
          // newest revision of each, and pin the user-facing version (mirrors VersionHistory).
          const byVersion = new Map<number, VersionRecord>();
          for (const entry of [...data.revisions].sort(
            (a, b) => a.revisionId - b.revisionId,
          )) {
            const version = entry.record.version;
            if (version == null) continue;
            byVersion.set(version, {
              version,
              revisionId: entry.revisionId,
              modificationDate: entry.record.lastModified ?? "",
            });
          }
          return [...byVersion.values()].sort((a, b) => b.version - a.version);
        }
        return [];
      } catch {
        // A failed revisions fetch degrades to the latest-only view rather than
        // surfacing an unhandled rejection through the picker.
        return [];
      }
    },
    [parsed?.prefix, parsed?.id, parsed?.inventoryPathSegment],
  );

  if (!props.open) return null;
  if (!parsed || !isSupportedTarget(parsed)) {
    return (
      <Dialog open onClose={props.onCancel} fullWidth maxWidth="sm">
        <DialogTitle>Version history</DialogTitle>
        <DialogContent>
          Cannot resolve version history for {props.globalId}.
        </DialogContent>
        <DialogActions>
          <Button onClick={props.onCancel}>Close</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog
      open={props.open}
      onClose={props.onCancel}
      aria-label={`Version history for ${props.globalId}`}
      fullWidth
      maxWidth="md"
    >
      <DialogTitle>Version history for {props.globalId}</DialogTitle>
      <DialogContent>
        <VersionLockPicker
          recordId={parsed.id}
          prefix={parsed.prefix}
          currentSelection={selection}
          fetchVersions={fetchVersions}
          onChange={setSelection}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onCancel}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() =>
            props.onConfirm(
              selection === LATEST_SELECTION ? null : selection,
            )
          }
        >
          Lock to selected version
        </Button>
      </DialogActions>
    </Dialog>
  );
}
