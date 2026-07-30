import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Skeleton from "@mui/material/Skeleton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { getErrorMessage } from "../../../util/error";
import { formatFileSize } from "../../../util/files";
import { isoToLocale } from "../../../util/Util";
import { fetchVersionHistory, type VersionRow } from "../galleryVersionHistory";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { GalleryItemLink, galleryItemHref } from "./GalleryItemLink";

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
   * On a pinned view `file.version` is the version being shown rather than the
   * live one, so the newest row stands in for "current" there. Marking the shown
   * version "current" would be a plain lie about an item that has newer content.
   */
  const liveVersion =
    state.state === "success" && typeof file.pinnedVersion === "number"
      ? (state.versions[0]?.version ?? null)
      : (file.version ?? null);

  /*
   * Rows are labelled "v1", "v2"; the column header stays spelled out. Each state is a whole
   * message rather than a base label plus an appended fragment: a translator needs to move
   * "(current)" relative to the number, and only one state can apply to a row, so building the
   * label in one place is also what stops "v3 (viewing) (current)" being expressible.
   */
  /* the live row links without a version segment, which saves a redirect */
  const rowHref = (version: number) =>
    galleryItemHref(idToString(file.id).elseThrow(), version === liveVersion ? undefined : version);

  const versionLabel = (version: number) => {
    if (version === file.pinnedVersion) return t("actionsMenu.versionHistory.versionViewing", { version });
    if (version === liveVersion) return t("actionsMenu.versionHistory.versionCurrent", { version });
    return t("actionsMenu.versionHistory.versionShort", { version });
  };

  return (
    /* lg rather than md: five columns of filenames, dates and names need the room */
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
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
                {/*
                 * Headers stay on one line: a long filename in the Name column would otherwise
                 * squeeze the neighbouring columns until two-word headers such as "Modification
                 * Date" broke across lines. The data cells still wrap, so only the header row is
                 * held rigid.
                 */}
                <TableRow sx={{ "& th": { whiteSpace: "nowrap" } }}>
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
                      <GalleryItemLink href={rowHref(row.version)} onNavigate={onClose}>
                        {versionLabel(row.version)}
                      </GalleryItemLink>
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
