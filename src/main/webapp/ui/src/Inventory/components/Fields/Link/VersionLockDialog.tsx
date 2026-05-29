import React, { useCallback, useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import ApiService from "../../../../common/InvApiService";
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
  /** Called with the chosen versionPin (revisionId) or null for latest. */
  onConfirm: (versionPin: number | null) => void;
  onCancel: () => void;
}

const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v\d+)?$/;
const PREFIX_TO_PATH: Record<string, string> = {
  SA: "samples",
  SS: "subSamples",
  IC: "containers",
  IN: "instruments",
};

function parseGlobalId(
  globalId: string,
): { prefix: string; id: number; pathSegment: string } | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  const segment = PREFIX_TO_PATH[match[1]];
  if (!segment) return null;
  return {
    prefix: match[1],
    id: Number(match[2]),
    pathSegment: segment,
  };
}

interface ApiRevisionEntry {
  revisionId: number;
  revisionType: string;
  record: {
    lastModified?: string;
  };
}

interface ApiRevisionList {
  revisions: ApiRevisionEntry[];
  revisionsCount: number;
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

  const fetchVersions = useCallback(
    async (): Promise<VersionRecord[]> => {
      if (!parsed) return [];
      const { data } = await ApiService.get<ApiRevisionList>(
        `${parsed.pathSegment}/${parsed.id}/revisions`,
      );
      return data.revisions.map((entry) => ({
        version: entry.revisionId,
        revisionId: entry.revisionId,
        modificationDate: entry.record.lastModified ?? "",
      }));
    },
    [parsed?.pathSegment, parsed?.id],
  );

  if (!props.open) return null;
  if (!parsed) {
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
