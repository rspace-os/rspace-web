import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Skeleton from "@mui/material/Skeleton";
import type React from "react";
import { useTranslation } from "react-i18next";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { useGetWorkspaceRecordInformationAjaxQuery } from "@/modules/workspace/queries";
import DocumentSections from "./DocumentSections";
import GallerySections from "./GallerySections";
import { iconForGlobalId, openUrlForTarget, prefixOf } from "./iconForGlobalId";
import { GLOBAL_ID_PATTERN } from "./linkTarget";

export interface ElnRecordInfoDialogProps {
  open: boolean;
  globalId: string;
  /**
   * When the link is pinned to a specific version of an SD target, the version number
   * (the `vN` in `SDxxxvN`). null/undefined means the link points at the latest version.
   */
  versionPin?: number | null;
  /**
   * When the ELN target has been deleted, the Open button is hidden: a deleted
   * ELN record's route only produces an error page (unlike a deleted Inventory
   * item, which is still viewable in the trash and so keeps Open). Mirrors the
   * link card's Open rule.
   */
  targetDeleted?: boolean;
  /**
   * When the ELN target is not readable by the viewer (e.g. shared then
   * unshared), the Open button is hidden for the same reason: its route is only
   * an error page. Together with targetDeleted this matches the link card's
   * Open rule for ELN targets.
   */
  noAccess?: boolean;
  onClose: () => void;
}

/**
 * Extract the numeric DB id from a global id (e.g. `SD123` -> 123, `SD123v2` -> 123).
 * `getRecordInformation` takes the numeric id, not the global-id string.
 */
function numericIdOf(globalId: string): number | null {
  const id = GLOBAL_ID_PATTERN.exec(globalId)?.[2];
  return id !== undefined ? Number(id) : null;
}

/**
 * The per-type body. Runs the record-information query and renders explicitly by
 * state: a loading skeleton, an inline error Alert, or the matching per-type
 * sections. A failed load (e.g. 404 / not readable / unknown version) is an
 * expected outcome handled here from the query's error state, not thrown to an
 * error boundary. Re-opening the dialog remounts this body, so React Query
 * refetches a previously-errored query automatically.
 */
function DialogBody({
  globalId,
  recordId,
  versionPin,
}: {
  globalId: string;
  recordId: number;
  versionPin?: number | null;
}): React.ReactElement {
  const { t } = useTranslation("inventory");
  const {
    data: info,
    isPending,
    isError,
    refetch,
  } = useGetWorkspaceRecordInformationAjaxQuery({
    recordId,
    version: versionPin ?? undefined,
  });

  if (isPending) {
    return <Skeleton variant="rectangular" width="100%" height={240} />;
  }

  if (isError || !info) {
    // A pinned link that can no longer resolve its version gets a specific message
    // plus a link to the latest, rather than the generic not-available text.
    if (versionPin != null) {
      return (
        <Alert severity="error">
          <TransRichText
            ns="inventory"
            i18nKey="fields.link.elnInfoDialog.versionUnavailable"
            values={{ versionPin, globalId }}
            components={{ a: <Link href={`/globalId/${globalId}`} target="_blank" rel="noopener noreferrer" /> }}
          />
        </Alert>
      );
    }
    return <Alert severity="error">{t("fields.link.elnInfoDialog.unavailable")}</Alert>;
  }

  const prefix = prefixOf(globalId);
  if (prefix === "GL") {
    return (
      <GallerySections
        info={info}
        onRecordChanged={() => {
          void refetch();
        }}
      />
    );
  }
  return <DocumentSections info={info} isNotebook={prefix === "NB"} pinnedVersion={versionPin} />;
}

/**
 * Modal that reproduces the ELN record-info dialog (`#recordInfoDialog`) for an ELN
 * link target (structured document, notebook or gallery file). It fetches the same
 * data the ELN info button does via `/workspace/getRecordInformation` and renders the
 * per-type body (documents/notebooks vs gallery files) to match the ELN exactly.
 *
 * Relies on the app-level QueryClient (the Inventory app is mounted under App.tsx's
 * QueryClientProvider), so it does not provide its own.
 */
export default function ElnRecordInfoDialog(props: ElnRecordInfoDialogProps): React.ReactElement | null {
  const { t } = useTranslation(["inventory", "common"]);
  if (!props.open) return null;

  const iconData = iconForGlobalId(props.globalId);
  const recordId = numericIdOf(props.globalId);
  // Only SD documents are versionable; ignore a pin on any other target (NB/GL).
  const effectiveVersionPin = prefixOf(props.globalId) === "SD" ? (props.versionPin ?? null) : null;

  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      aria-label={t("fields.link.infoDialog.label", { globalId: props.globalId })}
      fullWidth
      maxWidth="md"
    >
      <DialogTitle>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {iconData && <RecordTypeIcon record={iconData} aria-hidden />}
          {props.globalId}
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        {recordId === null ? (
          <Alert severity="error">{t("fields.link.elnInfoDialog.unavailable")}</Alert>
        ) : (
          <DialogBody globalId={props.globalId} recordId={recordId} versionPin={effectiveVersionPin} />
        )}
      </DialogContent>
      <DialogActions>
        {!(props.targetDeleted || props.noAccess) && (
          <Button
            size="small"
            startIcon={<OpenInNewIcon />}
            href={openUrlForTarget(props.globalId, effectiveVersionPin)}
            target="_blank"
            rel="noopener noreferrer"
          >
            {t("common:actions.open")}
          </Button>
        )}
        <Button onClick={props.onClose}>{t("common:actions.close")}</Button>
      </DialogActions>
    </Dialog>
  );
}
