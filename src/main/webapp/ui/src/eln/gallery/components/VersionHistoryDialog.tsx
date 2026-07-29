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
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import NavigateContext from "../../../stores/contexts/Navigate";
import { getErrorMessage } from "../../../util/error";
import { formatFileSize } from "../../../util/files";
import { isoToLocale } from "../../../util/Util";
import { fetchVersionHistory, type VersionRow } from "../galleryVersionHistory";
import { type GalleryFile, idToString } from "../useGalleryListing";

type State = { state: "loading" } | { state: "success"; versions: Array<VersionRow> } | { state: "fail"; error: Error };

/**
 * Lists a Gallery item's versions, newest first. Each row opens that version's
 * pinned view, so what a user lands on is also a URL they can copy and share.
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
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

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
        const versions = await fetchVersionHistory(idToString(file.id).elseThrow(), loadFailedMessage);
        if (cancelled) return;
        setState({ state: "success", versions });
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
   * The live version has no version segment: it is the ordinary, editable item
   * view, and linking straight to it saves a redirect.
   */
  const versionUrl = (version: number, isLive: boolean) => {
    const id = idToString(file.id).elseThrow();
    return isLive ? `/gallery/item/${id}` : `/gallery/item/${id}/${version}`;
  };

  /* Rows are labelled "v1", "v2"; the column header stays spelled out. */
  const versionLabel = (version: number) => t("actionsMenu.versionHistory.versionShort", { version });

  /*
   * On a pinned view `file.version` is the version being shown rather than the
   * live one, so the newest row stands in for "current" there. Marking the shown
   * version "current" would be a plain lie about an item that has newer content.
   */
  const liveVersion =
    state.state === "success" && typeof file.pinnedVersion === "number"
      ? (state.versions[0]?.version ?? null)
      : (file.version ?? null);

  return (
    /* md rather than sm: four columns of filenames, dates and names need the room */
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
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
                  <TableCell>{t("actionsMenu.versionHistory.columns.version")}</TableCell>
                  <TableCell>{t("actionsMenu.versionHistory.columns.name")}</TableCell>
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
                        href={versionUrl(row.version, row.version === liveVersion)}
                        onClick={(e: React.MouseEvent) => {
                          e.preventDefault();
                          navigate(versionUrl(row.version, row.version === liveVersion));
                          onClose();
                        }}
                      >
                        {versionLabel(row.version)}
                      </Link>
                      {row.version === file.pinnedVersion && t("actionsMenu.versionHistory.viewing")}
                      {row.version === liveVersion && t("actionsMenu.versionHistory.current")}
                    </TableCell>
                    {/* per-version: a new version can be a differently named file */}
                    <TableCell>{row.name ?? "—"}</TableCell>
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
