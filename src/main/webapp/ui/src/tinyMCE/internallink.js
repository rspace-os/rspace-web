"use strict";
import React, { useEffect, type Node } from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { lighten } from "@mui/material/styles";
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
import TimeAgoCustom from "../components/TimeAgoCustom";
import EnhancedTableHead from "../components/EnhancedTableHead";
import Radio from "@mui/material/Radio";
import { stableSort, getSorting, paginationOptions } from "../util/table";
import { createRoot } from "react-dom/client";
import materialTheme from "../theme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";

const headCells = [
  { id: "version", numeric: true, disablePadding: false, label: "Version" },
  { id: "name", numeric: false, disablePadding: false, label: "Name" },
  {
    id: "modifiedBy",
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

const EnhancedTableToolbar = (props) => {
  const { classes } = useToolbarStyles();

  return (
    <Toolbar className={classes.root}>
      <Typography className={classes.title} color="inherit" variant="subtitle1">
        The link currently points at{" "}
        {props.selected ? `version ${props.selected}.` : "latest version."}
      </Typography>
    </Toolbar>
  );
};

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

export default function InternalLink(props): Node {
  const { classes } = useStyles();
  const [open, setOpen] = React.useState(true);
  const [revisions, setRevisions] = React.useState([]);
  const [latestRevision, setLatestRevision] = React.useState({});
  // const [preview, setPreview] = React.useState("");
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("version");
  const [selected, setSelected] = React.useState(null);
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(5);

  const handleClose = () => {
    setOpen(false);
  };

  const handleInsert = () => {
    RS.tinymceInsertInternalLink(...generateParams());
    props.initialEl.remove();
    setOpen(false);
  };

  const generateParams = () => {
    let revision = revisions.find((r) => r.version == selected);
    if (selected == latestRevision.version) {
      revision = latestRevision;
    }

    return [
      revision.id, // id
      `${revision.oid.idString}${
        selected == latestRevision.version ? "" : `v${selected}`
      }`, // global id
      revision.name, // name
      tinymce.activeEditor, // ed
    ];
  };

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  const handleClick = (event, name) => {
    if (event.target.tagName != "A") {
      setSelected(name);
    }
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const emptyRows =
    rowsPerPage - Math.min(rowsPerPage, revisions.length - page * rowsPerPage);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axios.get(
          `/workspace/revisionHistory/ajax/${props.id}/versions`
        );
        const fetchedRevisions = response.data.data.reverse(); // reverse to show latest first
        let latest = fetchedRevisions[0]; // latest version is the first in the reversed list
        latest = { ...latest }; // make a copy so that we do not edit the last version
        latest.version = `Always automatically update link to latest version`;
        setLatestRevision(latest);

        setRevisions(fetchedRevisions);
        setSelected(props.version ? props.version : latest.version);

        // calculate the initial page so that current revision is visible
        let idx = fetchedRevisions.findIndex((r) => r.version == props.version);
        if (idx != -1) {
          // if revision's not found, selected "Always latest" and set to page 0
          setPage(Math.floor(idx / 5));
        }
      } catch (e) {
        console.log(e);
      }
    };

    fetchData();
  }, []);

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
        <Table
          className={classes.table}
          size="small"
          aria-label="Internal link version options"
        >
          <TableBody>
            <TableRow
              hover
              onClick={(event) => handleClick(event, latestRevision.version)}
              role="checkbox"
              aria-checked={selected == latestRevision.version}
              tabIndex={-1}
              key={latestRevision.version}
              selected={selected == latestRevision.version}
            >
              <TableCell scope="row" align="left">
                <Radio
                  checked={selected == latestRevision.version}
                  value="d"
                  color="default"
                  name="radio-button-demo"
                  inputProps={{ "aria-label": "D" }}
                />
              </TableCell>
              <TableCell scope="row" align="left">
                {latestRevision.version}
              </TableCell>
              <TableCell align="left">
                <a
                  target="_blank"
                  href={`/workspace/editor/structuredDocument/audit/view?recordId=${latestRevision.id}&revision=${latestRevision.revision}&settingsKey=bYlIKtlZMd`}
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
              classes={classes}
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
                  const isItemSelected = revision.version == selected;
                  const labelId = `enhanced-table-checkbox-${index}`;

                  return (
                    <TableRow
                      hover
                      onClick={(event) => handleClick(event, revision.version)}
                      role="checkbox"
                      aria-checked={isItemSelected}
                      tabIndex={-1}
                      key={revision.version}
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
                          href={`/workspace/editor/structuredDocument/audit/view?recordId=${revision.id}&revision=${revision.revision}&settingsKey=bYlIKtlZMd`}
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
            selected == props.version ||
            (!props.version && selected == latestRevision.version)
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
function WrappedInternalLink(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <InternalLink {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("tinymce-insert-revision", function (e) {
  $(document.body).append("<span class='revision-dialog'></span>");
  let container = $(".revision-dialog")[$(".revision-dialog").length - 1];
  const root = createRoot(container);
  root.render(
    <WrappedInternalLink
      id={e.detail.id}
      version={e.detail.version}
      initialEl={e.detail.el}
    />
  );
});
