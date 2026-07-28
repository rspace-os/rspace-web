import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Skeleton from "@mui/material/Skeleton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { getErrorMessage } from "../../../util/error";
import { formatFileSize } from "../../../util/files";
import { isoToLocale } from "../../../util/Util";
import { groupByVersion } from "../../../util/versionHistory";
import {
  useAsposePreviewOfGalleryFile,
  useImagePreviewOfGalleryFile,
  usePdfPreviewOfGalleryFile,
} from "../primaryActionHooks";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { useAsposePreview } from "./CallableAsposePreview";
import { useImagePreview } from "./CallableImagePreview";
import { usePdfPreview } from "./CallablePdfPreview";

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
  };
};

/** The endpoint's AjaxReturnObject envelope. */
type VersionHistoryResponse = {
  data: { revisions: Array<ApiRevision>; revisionsCount: number } | null;
  error?: { errorMessages?: Array<string> } | null;
};

type VersionRow = {
  version: number;
  revisionId: number;
  lastModified: string | null;
  modifiedByFullName: string | null;
  size: number | null;
};

type State = { state: "loading" } | { state: "success"; versions: Array<VersionRow> } | { state: "fail"; error: Error };

/** Shapes the shared version grouping into the rows this table renders. */
function toVersionRows(revisions: Array<ApiRevision>): Array<VersionRow> {
  return groupByVersion(revisions).map(({ version, revision }) => ({
    version,
    revisionId: revision.revisionId,
    lastModified: revision.record.lastModified ?? null,
    modifiedByFullName: revision.record.modifiedByFullName ?? null,
    size: revision.record.size ?? null,
  }));
}

/**
 * Lists a Gallery item's versions, newest first. Each row opens that version:
 * previewed in-app where the file type has a previewer, downloaded otherwise.
 *
 * The history is read-only. Making an old version current is not offered here;
 * "Upload new version" is the forward-only equivalent.
 */
export default function VersionHistoryDialog({
  open,
  onClose,
  file,
}: {
  open: boolean;
  onClose: () => void;
  file: GalleryFile;
}): React.ReactNode {
  const { t } = useTranslation(["gallery", "common"]);
  const [state, setState] = useState<State>({ state: "loading" });

  const canPreviewAsImage = useImagePreviewOfGalleryFile();
  const canPreviewAsPdf = usePdfPreviewOfGalleryFile();
  const canPreviewWithAspose = useAsposePreviewOfGalleryFile();
  const { openImagePreview } = useImagePreview();
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreviewFromDetails } = useAsposePreview();

  /*
   * Depended on as a string rather than calling t() inside the effect, so a new
   * `t` identity cannot re-trigger the fetch.
   */
  const loadFailedMessage = t("actionsMenu.versionHistory.loadFailed");

  useEffect(() => {
    if (!open) return;
    // ignore a response landing after the dialog closed or the file changed
    let cancelled = false;
    setState({ state: "loading" });
    void (async () => {
      try {
        const id = idToString(file.id).elseThrow();
        const { data } = await axios.get<VersionHistoryResponse>(`/gallery/ajax/versionHistory/${id}`);
        if (cancelled) return;
        if (!data.data) {
          // a 200 carrying an error envelope must not read as "no history yet"
          throw new Error(data.error?.errorMessages?.[0] ?? loadFailedMessage);
        }
        setState({
          state: "success",
          versions: toVersionRows(data.data.revisions),
        });
      } catch (e) {
        if (cancelled) return;
        setState({
          state: "fail",
          error: new Error(getErrorMessage(e, loadFailedMessage)),
        });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, file, loadFailedMessage]);

  /*
   * Historical bytes only come from /Streamfile: the API's file endpoint has no
   * version parameter.
   */
  const versionUrl = (version: number) => `/Streamfile/${idToString(file.id).elseThrow()}?version=${version}`;

  /*
   * Which previewer to use depends on the file's type, which does not change
   * between versions, so the live file decides it. Only the bytes differ, and
   * those are addressed per version. Collabora and Office Online are excluded
   * deliberately: they edit the live file and cannot open a historical version,
   * so those types fall through to a download.
   */
  const openVersion = (row: VersionRow) => {
    const url = versionUrl(row.version);
    if (canPreviewAsImage(file).isOk) {
      openImagePreview(url, { caption: [file.name] });
      return;
    }
    if (canPreviewAsPdf(file).isOk) {
      openPdfPreview(url);
      return;
    }
    if (canPreviewWithAspose(file).isOk && file.extension) {
      // Aspose conversion keys on the audit revision, not the user-facing version
      void openAsposePreviewFromDetails({
        documentId: Number(idToString(file.id).elseThrow()),
        fileExtension: file.extension,
        revisionId: row.revisionId,
      });
      return;
    }
    window.open(url);
  };

  const versionLabel = (version: number | "none") =>
    t("actionsMenu.versionHistory.columns.version", { version: String(version) });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("actionsMenu.versionHistory.title", { name: file.name })}</DialogTitle>
      <DialogContent>
        {state.state === "loading" && <Skeleton variant="rectangular" width="100%" height={118} />}
        {state.state === "fail" && <Alert severity="error">{state.error.message}</Alert>}
        {state.state === "success" &&
          (state.versions.length === 0 ? (
            <Alert severity="info">{t("actionsMenu.versionHistory.none")}</Alert>
          ) : (
            <Table size="small" aria-label={t("actionsMenu.versionHistory.tableLabel")}>
              <TableHead>
                <TableRow>
                  <TableCell>{versionLabel("none")}</TableCell>
                  <TableCell>{t("actionsMenu.versionHistory.columns.modified")}</TableCell>
                  <TableCell>{t("actionsMenu.versionHistory.columns.by")}</TableCell>
                  <TableCell>{t("actionsMenu.versionHistory.columns.size")}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {state.versions.map((row) => (
                  <TableRow key={row.version} data-test-id={`VersionHistory-row-${row.version}`}>
                    <TableCell>
                      <Link
                        href={versionUrl(row.version)}
                        onClick={(e: React.MouseEvent) => {
                          e.preventDefault();
                          openVersion(row);
                        }}
                      >
                        {versionLabel(row.version)}
                      </Link>
                      {row.version === file.version && t("actionsMenu.versionHistory.current")}
                    </TableCell>
                    <TableCell>{row.lastModified ? isoToLocale(row.lastModified) : "—"}</TableCell>
                    <TableCell>{row.modifiedByFullName ?? "—"}</TableCell>
                    <TableCell>{row.size === null ? "—" : formatFileSize(row.size)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ))}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.close")}</Button>
      </DialogActions>
    </Dialog>
  );
}
