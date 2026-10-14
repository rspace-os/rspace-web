import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Radio from "@mui/material/Radio";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import type { InternalLinkInsertParams, RevisionIdentifier, TinyMceEditor } from "@/tinyMCE/types";
import EnhancedTableHead, { type Cell } from "../components/EnhancedTableHead";
import UserDetails from "../components/UserDetails";
import { getSorting, paginationOptions } from "../util/table";
import type { Order } from "../util/types";

type RevisionVersion = RevisionIdentifier;
type RevisionSortKey = "version" | "name" | "ownerFullName" | "modificationDate";
type RevisionSelection = RevisionVersion | null;
interface RevisionRecord {
  id: RevisionIdentifier;
  version: RevisionVersion;
  revision: RevisionIdentifier;
  name: string;
  oid: {
    idString: string;
  };
  ownerId: number;
  ownerFullName: string;
  modificationDate: string;
}
interface RevisionHistoryResponse {
  data: RevisionRecord[];
}
interface InternalLinkProps {
  id: RevisionIdentifier;
  version?: RevisionVersion | null;
  initialEl?: RemovableElement | null;
}
export type RemovableElement = {
  remove: () => unknown;
};
declare const tinymce: {
  activeEditor: TinyMceEditor;
};
declare global {
  interface RSGlobal {
    tinymceInsertInternalLink?: (...params: InternalLinkInsertParams) => void;
  }
  interface Window {
    RS?: RSGlobal;
  }
}
const LATEST_REVISION_SELECTION = "__latest__" as const;
function matchesVersion(left: RevisionVersion | null | undefined, right: RevisionVersion | null | undefined): boolean {
  return left != null && right != null && String(left) === String(right);
}
function isLatestSelection(selection: RevisionSelection | null): selection is typeof LATEST_REVISION_SELECTION {
  return selection === LATEST_REVISION_SELECTION;
}
function createAuditUrl(id: RevisionIdentifier, revision: RevisionIdentifier): string {
  return `/workspace/editor/structuredDocument/audit/view?recordId=${String(id)}&revision=${String(revision)}`;
}
export default function InternalLink(props: InternalLinkProps): React.ReactElement {
  const [open, setOpen] = React.useState(true);
  const [revisions, setRevisions] = React.useState<RevisionRecord[]>([]);
  const [latestRevision, setLatestRevision] = React.useState<RevisionRecord | null>(null);
  // const [preview, setPreview] = React.useState("");
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState<RevisionSortKey>("version");
  const [selected, setSelected] = React.useState<RevisionSelection | null>(null);
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(5);
  const { t } = useTranslation(["workspace", "common"]);
  const LATEST_REVISION_LABEL = t("tinymce.internalLink.alwaysLatest");
  const headCells: Array<Cell<RevisionSortKey>> = [
    { id: "version", numeric: true, disablePadding: false, label: t("tinymce.internalLink.columns.version") },
    { id: "name", numeric: false, disablePadding: false, label: t("tinymce.internalLink.columns.name") },
    {
      id: "ownerFullName",
      numeric: false,
      disablePadding: false,
      label: t("tinymce.internalLink.columns.modifiedBy"),
    },
    {
      id: "modificationDate",
      numeric: false,
      disablePadding: false,
      label: t("tinymce.internalLink.columns.modified"),
    },
  ];
  const handleClose = (): void => {
    setOpen(false);
  };
  const handleInsert = (): void => {
    const params = generateParams();
    if (!params) {
      return;
    }
    window.RS?.tinymceInsertInternalLink?.(...params);
    props.initialEl?.remove();
    setOpen(false);
  };
  const generateParams = (): InternalLinkInsertParams | null => {
    const revision = isLatestSelection(selected)
      ? latestRevision
      : revisions.find((record) => matchesVersion(record.version, selected));
    if (!revision || selected === null) {
      return null;
    }
    return [
      revision.id,
      // id
      `${revision.oid.idString}${isLatestSelection(selected) ? "" : `v${String(selected)}`}`,
      // global id
      revision.name,
      // name
      tinymce.activeEditor, // ed
    ];
  };
  const handleRequestSort = (_event: React.MouseEvent<HTMLSpanElement>, property: RevisionSortKey): void => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };
  const handleClick = (event: React.MouseEvent<HTMLTableRowElement>, name: RevisionSelection): void => {
    if (event.target instanceof HTMLElement && event.target.closest("a")) {
      return;
    }
    setSelected(name);
  };
  const handleChangePage = (_event: React.MouseEvent<HTMLButtonElement> | null, newPage: number): void => {
    setPage(newPage);
  };
  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>): void => {
    setRowsPerPage(Number.parseInt(event.target.value, 10));
    setPage(0);
  };
  const emptyRows = rowsPerPage - Math.min(rowsPerPage, revisions.length - page * rowsPerPage);
  useEffect(() => {
    const fetchData = async (): Promise<void> => {
      try {
        const response = await axios.get<RevisionHistoryResponse>(
          `/workspace/revisionHistory/ajax/${props.id}/versions`,
        );
        const fetchedRevisions = [...response.data.data].reverse();
        const latest = fetchedRevisions[0] ?? null;
        setLatestRevision(latest);
        setRevisions(fetchedRevisions);
        setSelected(props.version ?? LATEST_REVISION_SELECTION);

        // calculate the initial page so that current revision is visible
        const idx = fetchedRevisions.findIndex((revision) => matchesVersion(revision.version, props.version));
        if (idx !== -1) {
          // if revision's not found, selected "Always latest" and set to page 0
          setPage(Math.floor(idx / 5));
        }
      } catch (error: unknown) {
        console.log(error);
      }
    };
    void fetchData();
  }, [props.id, props.version]);
  return (
    <Dialog open={open} onClose={handleClose} aria-labelledby="form-dialog-title" fullWidth={true} maxWidth="lg">
      <DialogTitle id="form-dialog-title">{t("tinymce.internalLink.dialogTitle")}</DialogTitle>
      <DialogContent>
        <Toolbar sx={{ pl: 2, pr: 1 }}>
          <Typography sx={{ flex: "1 1 100%" }} color="inherit" variant="subtitle1">
            {t("tinymce.internalLink.currentTarget", {
              version: props.version ?? null,
            })}
          </Typography>
        </Toolbar>
        {latestRevision && (
          <Table sx={{ minWidth: 750 }} size="small" aria-label={t("tinymce.internalLink.dialogTitle")}>
            <TableBody>
              <TableRow
                hover
                onClick={(event) => handleClick(event, LATEST_REVISION_SELECTION)}
                role="checkbox"
                aria-checked={isLatestSelection(selected)}
                tabIndex={-1}
                key={LATEST_REVISION_SELECTION}
                selected={isLatestSelection(selected)}
              >
                <TableCell scope="row" align="left">
                  <Radio
                    checked={isLatestSelection(selected)}
                    value="d"
                    color="default"
                    name="radio-button-demo"
                    slotProps={{
                      input: {
                        "aria-label": LATEST_REVISION_LABEL,
                      },
                    }}
                  />
                </TableCell>
                <TableCell scope="row" align="left">
                  {LATEST_REVISION_LABEL}
                </TableCell>
                <TableCell align="left">
                  <a target="_blank" href={createAuditUrl(latestRevision.id, latestRevision.revision)} rel="noreferrer">
                    {latestRevision.name}
                  </a>
                </TableCell>
                <TableCell align="left">
                  <UserDetails
                    userId={latestRevision.ownerId}
                    fullName={latestRevision.ownerFullName}
                    position={["bottom", "right"]}
                  />
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        )}
        <Typography
          variant="overline"
          sx={{
            display: "block",
          }}
          gutterBottom
        >
          {t("tinymce.internalLink.chooseVersion")}
        </Typography>
        <TableContainer>
          <Table
            sx={{ minWidth: 750 }}
            aria-labelledby="tableTitle"
            size="small"
            aria-label={t("tinymce.internalLink.versionTableLabel")}
          >
            <EnhancedTableHead
              headCells={headCells}
              order={order}
              orderBy={orderBy}
              onRequestSort={handleRequestSort}
              rowCount={revisions.length}
              emptyCol={true}
            />
            <TableBody>
              {revisions
                .toSorted(getSorting(order, orderBy))
                .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                .map((revision, index) => {
                  const isItemSelected = matchesVersion(revision.version, selected);
                  const labelId = `enhanced-table-checkbox-${index}`;
                  return (
                    <TableRow
                      hover
                      onClick={(event) => handleClick(event, revision.version)}
                      role="checkbox"
                      aria-checked={isItemSelected}
                      tabIndex={-1}
                      key={String(revision.version)}
                      selected={isItemSelected}
                    >
                      <TableCell id={labelId} scope="row" align="left">
                        <Radio
                          checked={isItemSelected}
                          value="d"
                          color="default"
                          name="radio-button-demo"
                          slotProps={{
                            input: {
                              "aria-label": t("tinymce.internalLink.selectRevision"),
                            },
                          }}
                        />
                      </TableCell>
                      <TableCell id={labelId} scope="row" align="right">
                        {revision.version}
                      </TableCell>
                      <TableCell align="left">
                        <a target="_blank" href={createAuditUrl(revision.id, revision.revision)} rel="noreferrer">
                          {revision.name}
                        </a>
                      </TableCell>
                      <TableCell align="left">
                        <UserDetails
                          userId={revision.ownerId}
                          fullName={revision.ownerFullName}
                          position={["bottom", "right"]}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell align="left">
                        <TimeAgoCustom time={revision.modificationDate} />
                      </TableCell>
                    </TableRow>
                  );
                })}
              {emptyRows > 0 && (
                <TableRow
                  sx={{
                    height: 33 * emptyRows,
                  }}
                >
                  <TableCell colSpan={6} />
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          rowsPerPageOptions={paginationOptions(revisions.length)}
          component="div"
          count={revisions.length}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={handleChangePage}
          onRowsPerPageChange={handleChangeRowsPerPage}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} color="primary">
          {t("common:actions.cancel")}
        </Button>
        <Button
          onClick={handleInsert}
          color="primary"
          variant="contained"
          disableElevation
          disabled={
            selected === null ||
            matchesVersion(selected, props.version) ||
            (props.version == null && isLatestSelection(selected))
          }
        >
          {t("tinymce.internalLink.updateRevisionLink")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
