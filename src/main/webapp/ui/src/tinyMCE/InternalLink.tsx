import React, { useEffect } from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { makeStyles } from "tss-react/mui";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import axios from "@/common/axios";
import UserDetails from "../components/UserDetails";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import EnhancedTableHead, { type Cell } from "../components/EnhancedTableHead";
import Radio from "@mui/material/Radio";
import { stableSort, getSorting, paginationOptions } from "../util/table";
import { createRoot } from "react-dom/client";
import materialTheme from "../theme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { type Order } from "../util/types";

type RevisionIdentifier = string | number;
type RevisionVersion = RevisionIdentifier;
type RevisionSortKey = "version" | "name" | "ownerFullName" | "modificationDate";
type RevisionSelection = RevisionVersion | null;
type InternalLinkInsertParams = [
  RevisionIdentifier,
  string,
  string,
  typeof tinymce.activeEditor,
];

interface RevisionRecord {
  id: RevisionIdentifier;
  version: RevisionVersion;
  revision: RevisionIdentifier;
  name: string;
  oid: {
    idString: string;
  };
  ownerId: RevisionIdentifier;
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

interface EnhancedTableToolbarProps {
  selected?: RevisionVersion | null;
}

type InsertRevisionDetail = {
  id: RevisionIdentifier;
  version?: RevisionVersion | null;
  el?: RemovableElement | ArrayLike<RemovableElement | undefined> | null;
};

type InsertRevisionEvent = CustomEvent<InsertRevisionDetail>;
type RemovableElement = {
  remove: () => unknown;
};

function isRemovableElement(value: unknown): value is RemovableElement {
  return (
    typeof value === "object" &&
    value !== null &&
    "remove" in value &&
    typeof value.remove === "function"
  );
}

declare const tinymce: {
  activeEditor: {
    remove: () => void;
  };
};

declare global {
  interface RSGlobal {
    tinymceInsertInternalLink?: (...params: InternalLinkInsertParams) => void;
  }

  interface Window {
    RS: RSGlobal;
  }
}

const LATEST_REVISION_SELECTION = "__latest__" as const;
const LATEST_REVISION_LABEL = "Always automatically update link to latest version";

const headCells: Array<Cell<RevisionSortKey>> = [
  { id: "version", numeric: true, disablePadding: false, label: "Version" },
  { id: "name", numeric: false, disablePadding: false, label: "Name" },
  {
    id: "ownerFullName",
    numeric: false,
    disablePadding: false,
    label: "Modified by",
  },
  {
    id: "modificationDate",
    numeric: false,
    disablePadding: false,
    label: "Modified",
  },
];

const useToolbarStyles = makeStyles()((theme) => ({
  root: {
    paddingLeft: theme.spacing(2),
    paddingRight: theme.spacing(1),
  },
  title: {
    flex: "1 1 100%",
  },
}));

function matchesVersion(
  left: RevisionVersion | null | undefined,
  right: RevisionVersion | null | undefined,
): boolean {
  return left != null && right != null && String(left) === String(right);
}

function isLatestSelection(
  selection: RevisionSelection | null,
): selection is typeof LATEST_REVISION_SELECTION {
  return selection === LATEST_REVISION_SELECTION;
}

function createAuditUrl(
  id: RevisionIdentifier,
  revision: RevisionIdentifier,
): string {
  return `/workspace/editor/structuredDocument/audit/view?recordId=${String(id)}&revision=${String(revision)}`;
}

function normalizeInitialElement(
  initialEl: InsertRevisionDetail["el"],
): RemovableElement | null {
  if (!initialEl) {
    return null;
  }

  if (typeof initialEl === "object" && "0" in initialEl) {
    const firstElement = initialEl[0];
    if (isRemovableElement(firstElement)) {
      return firstElement;
    }
  }

  if (isRemovableElement(initialEl)) {
    return initialEl;
  }

  return null;
}

function EnhancedTableToolbar(
  props: EnhancedTableToolbarProps,
): React.ReactElement {
  const { classes } = useToolbarStyles();

  return (
    <Toolbar className={classes.root}>
      <Typography className={classes.title} color="inherit" variant="subtitle1">
        The link currently points at{" "}
        {props.selected ? `version ${props.selected}.` : "latest version."}
      </Typography>
    </Toolbar>
  );
}

const useStyles = makeStyles()((theme) => ({
  root: {
    width: "100%",
  },
  paper: {
    width: "100%",
    marginBottom: theme.spacing(2),
  },
  table: {
    minWidth: 750,
  },
  visuallyHidden: {
    border: 0,
    clip: "rect(0 0 0 0)",
    height: 1,
    margin: -1,
    overflow: "hidden",
    padding: 0,
    position: "absolute",
    top: 20,
    width: 1,
  },
}));

export default function InternalLink(
  props: InternalLinkProps,
): React.ReactElement {
  const { classes } = useStyles();
  const [open, setOpen] = React.useState(true);
  const [revisions, setRevisions] = React.useState<RevisionRecord[]>([]);
  const [latestRevision, setLatestRevision] = React.useState<RevisionRecord | null>(null);
  // const [preview, setPreview] = React.useState("");
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState<RevisionSortKey>("version");
  const [selected, setSelected] = React.useState<RevisionSelection | null>(null);
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(5);

  const handleClose = (): void => {
    setOpen(false);
  };

  const handleInsert = (): void => {
    const params = generateParams();
    if (!params) {
      return;
    }

    window.RS.tinymceInsertInternalLink?.(...params);
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
      revision.id, // id
      `${revision.oid.idString}${
        isLatestSelection(selected) ? "" : `v${String(selected)}`
      }`, // global id
      revision.name, // name
      tinymce.activeEditor, // ed
    ];
  };

  const handleRequestSort = (
    _event: React.MouseEvent<HTMLSpanElement>,
    property: RevisionSortKey,
  ): void => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  const handleClick = (
    event: React.MouseEvent<HTMLTableRowElement>,
    name: RevisionSelection,
  ): void => {
    if (event.target instanceof HTMLElement && event.target.closest("a")) {
      return;
    }
    setSelected(name);
  };

  const handleChangePage = (
    _event: React.MouseEvent<HTMLButtonElement> | null,
    newPage: number,
  ): void => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ): void => {
    setRowsPerPage(Number.parseInt(event.target.value, 10));
    setPage(0);
  };

  const emptyRows =
    rowsPerPage - Math.min(rowsPerPage, revisions.length - page * rowsPerPage);

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
        const idx = fetchedRevisions.findIndex(
          (revision) => matchesVersion(revision.version, props.version),
        );
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
    <Dialog
      open={open}
      onClose={handleClose}
      aria-labelledby="form-dialog-title"
      fullWidth={true}
      maxWidth="lg"
    >
      <DialogTitle id="form-dialog-title">
        Internal link version options
      </DialogTitle>
      <DialogContent>
        <EnhancedTableToolbar selected={props.version} />
        {latestRevision && (
          <Table
            className={classes.table}
            size="small"
            aria-label="Internal link version options"
          >
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
                    inputProps={{ "aria-label": "D" }}
                  />
                </TableCell>
                <TableCell scope="row" align="left">
                  {LATEST_REVISION_LABEL}
                </TableCell>
                <TableCell align="left">
                  <a
                    target="_blank"
                    href={createAuditUrl(latestRevision.id, latestRevision.revision)}
                    rel="noreferrer"
                  >
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
        <Typography variant="overline" display="block" gutterBottom>
          Or choose a version that the link should always point at:
        </Typography>
        <TableContainer>
          <Table
            className={classes.table}
            aria-labelledby="tableTitle"
            size="small"
            aria-label="enhanced table"
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
              {stableSort(revisions, getSorting(order, orderBy))
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
                          inputProps={{ "aria-label": "Select revision" }}
                        />
                      </TableCell>
                      <TableCell id={labelId} scope="row" align="right">
                        {revision.version}
                      </TableCell>
                      <TableCell align="left">
                        <a
                          target="_blank"
                          href={createAuditUrl(revision.id, revision.revision)}
                          rel="noreferrer"
                        >
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
                <TableRow style={{ height: 33 * emptyRows }}>
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
          Cancel
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
          Update revision link
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
function WrappedInternalLink(props: InternalLinkProps): React.ReactElement {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <InternalLink {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("tinymce-insert-revision", (event: Event): void => {
  const detail = (event as InsertRevisionEvent).detail;
  if (!detail) {
    return;
  }

  const container = document.createElement("span");
  container.className = "revision-dialog";
  document.body.appendChild(container);
  const root = createRoot(container);
  root.render(
    <WrappedInternalLink
      id={detail.id}
      version={detail.version}
      initialEl={normalizeInitialElement(detail.el)}
    />,
  );
});
