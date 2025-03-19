//@flow

import React, { type Node } from "react";
import { type GalleryFile, idToString } from "../useGalleryListing";
import { Dialog } from "../../../components/DialogBoundary";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import IconButton from "@mui/material/IconButton";
import ZoomInIcon from "@mui/icons-material/ZoomIn";
import ZoomOutIcon from "@mui/icons-material/ZoomOut";
import ResetZoomIcon from "./ResetZoomIcon";
import ButtonGroup from "@mui/material/ButtonGroup";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Switch from "@mui/material/Switch";
import FormControlLabel from "@mui/material/FormControlLabel";
import Box from "@mui/material/Box";
import { ThemeProvider, styled } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import AppBar from "../../../components/AppBar";
import Drawer from "@mui/material/Drawer";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import axios from "@/common/axios";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import EnhancedTableHead from "../../../components/EnhancedTableHead";
import LoadingCircular from "../../../components/LoadingCircular";
import Grid from "@mui/material/Grid";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { stableSort, getSorting, paginationOptions } from "../../../util/table";
import { ACCENT_COLOR } from "../../../assets/branding/snapgene";

const CustomDrawer = styled(Drawer)(() => ({
  "& .MuiPaper-root": {
    paddingTop: "8px",
    position: "relative",
  },
}));

function DnaPreview({
  show,
  file,
  idOfDnaPreviewTab,
}: {|
  show: boolean,
  file: GalleryFile,
  idOfDnaPreviewTab: string,
|}) {
  const [image, setImage] = React.useState<null | string>(null);
  const [linear, setLinear] = React.useState(false);
  const [showEnzymes, setShowEnzymes] = React.useState(true);
  const [showORFs, setShowORFs] = React.useState(true);
  const [zoom, setZoom] = React.useState(1);
  const [error, setError] = React.useState<null | string>(null);
  const [scrollPos, setScrollPos] = React.useState<null | {|
    scrollLeft: number,
    scrollTop: number,
  |}>(null);
  const [cursorOffset, setCursorOffset] = React.useState<null | {|
    x: number,
    y: number,
  |}>(null);

  React.useEffect(() => {
    try {
      setImage(
        `/molbiol/dna/png/${idToString(file.id).elseThrow()}?linear=${
          linear ? "true" : "false"
        }&showEnzymes=${showEnzymes ? "true" : "false"}&showORFs=${
          showORFs ? "true" : "false"
        }`
      );
    } catch (e) {
      setError(e.message);
    }
  }, [file, linear, showEnzymes, showORFs]);

  return (
    <Stack
      component="section"
      role="tabpanel"
      direction="column"
      spacing={2}
      flexGrow={1}
      style={{ display: show ? "flex" : "none", minHeight: 0, height: "100%" }}
      aria-labelledby={idOfDnaPreviewTab}
    >
      <Stack direction="row" spacing={1}>
        <Select
          value={linear}
          onChange={(e) => setLinear(e.target.value)}
          size="small"
        >
          <MenuItem value={false}>Circular</MenuItem>
          <MenuItem value={true}>Linear</MenuItem>
        </Select>
        <FormControlLabel
          control={
            <Switch
              checked={showEnzymes}
              onChange={({ target: { checked } }) => setShowEnzymes(checked)}
            />
          }
          label="Show restriction sites"
        />
        <FormControlLabel
          control={
            <Switch
              checked={showORFs}
              onChange={({ target: { checked } }) => setShowORFs(checked)}
            />
          }
          label="Show ORFs"
        />
        <Box flexGrow={1} />
        <ButtonGroup>
          <IconButton
            onClick={() => setZoom((z) => z * 1.1)}
            aria-label="zoom in"
          >
            <ZoomInIcon />
          </IconButton>
          <IconButton
            onClick={() => setZoom((z) => z / 1.1)}
            aria-label="zoom out"
          >
            <ZoomOutIcon />
          </IconButton>
          <IconButton onClick={() => setZoom(1)} aria-label="reset zoom">
            <ResetZoomIcon />
          </IconButton>
        </ButtonGroup>
      </Stack>
      {/* eslint-disable-next-line jsx-a11y/no-static-element-interactions -- there is no semantic element */}
      <div
        style={{
          borderRadius: "3px",
          border: `2px solid hsl(${ACCENT_COLOR.background.hue}deg, ${ACCENT_COLOR.background.saturation}%, ${ACCENT_COLOR.background.lightness}%)`,
          overflow: "hidden",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          cursor: cursorOffset ? "grabbing" : "grab",
        }}
        onMouseDown={(e) => {
          const thisNode = e.currentTarget;
          setScrollPos({
            scrollLeft: thisNode.scrollLeft,
            scrollTop: thisNode.scrollTop,
          });
          setCursorOffset({
            x: e.nativeEvent.clientX,
            y: e.nativeEvent.clientY,
          });
        }}
        onMouseMove={(e) => {
          if (!scrollPos || !cursorOffset) return;
          const thisNode = e.currentTarget;
          const currentOffset = {
            x: e.nativeEvent.clientX,
            y: e.nativeEvent.clientY,
          };
          const moved = {
            x: currentOffset.x - cursorOffset.x,
            y: currentOffset.y - cursorOffset.y,
          };
          thisNode.scrollTo(
            scrollPos.scrollLeft - moved.x,
            scrollPos.scrollTop - moved.y
          );
        }}
        onMouseUp={() => {
          setCursorOffset(null);
          setScrollPos(null);
        }}
      >
        {error ? (
          <div>{error}</div>
        ) : (
          <img
            alt={`DNA preview of ${file.name}`}
            src={image}
            onError={() => {
              setError("Coult not load DNA preview image.");
            }}
            style={{
              maxHeight: "100%",
              maxWidth: "100%",
              transform: `scale(${zoom})`,
              transition: "transform .5s ease-in-out",
              transformOrigin: "left top",
              objectFit: "contain",
            }}
          />
        )}
      </div>
    </Stack>
  );
}

const enzymeSetOptions = {
  UNIQUE_SIX_PLUS: "Unique six plus",
  UNIQUE: "Unique",
  SIX_PLUS: "Six plus",
  UNIQUE_AND_DUAL: "Unique and dual",
  COMMERCIAL_NONREDUNDANT: "Commercial nonredundant",
};

const enzymeHeadCells = [
  { id: "name", numeric: false, disablePadding: false, label: "Enzyme" },
  {
    id: "bottomCutPosition",
    numeric: true,
    disablePadding: false,
    label: "Bottom cut position",
  },
  {
    id: "topCutPosition",
    numeric: true,
    disablePadding: false,
    label: "Top cut position",
  },
];

function RestrictionSites({
  show,
  file,
  idOfRestrictionSitesTab,
}: {|
  show: boolean,
  file: GalleryFile,
  idOfRestrictionSitesTab: string,
|}) {
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("enzyme");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);
  const [enzymeSet, setEnzymeSet] = React.useState("UNIQUE_SIX_PLUS");
  const [enzymeList, setEnzymeList] = React.useState<
    $ReadOnlyArray<{|
      name: string,
      id: number,
      topCutPosition: number,
      bottomCutPosition: number,
    |}>
  >([]);
  const [error, setError] = React.useState<null | string>(null);

  const generateEnzymeList = (
    list: $ReadOnlyArray<{|
      id: number,
      name: string,
      hits: $ReadOnlyArray<{|
        topCutPosition: number,
        bottomCutPosition: number,
      |}>,
    |}>
  ) => {
    setEnzymeList(
      list
        .map((enzyme) =>
          enzyme.hits.map((hit) => ({
            name: enzyme.name,
            id: enzyme.id,
            topCutPosition: hit.topCutPosition,
            bottomCutPosition: hit.bottomCutPosition,
          }))
        )
        .flat()
    );
  };

  const fetchEnzymes = async () => {
    setLoading(true);

    try {
      const url = `/molbiol/dna/enzymes/${idToString(
        file.id
      ).elseThrow()}?enzymeSet=${enzymeSet}`;
      const response = await axios.get<{|
        enzymes: $ReadOnlyArray<{|
          id: number,
          name: string,
          hits: $ReadOnlyArray<{|
            topCutPosition: number,
            bottomCutPosition: number,
          |}>,
        |}>,
      |}>(url);
      generateEnzymeList(response.data.enzymes);
    } catch (e) {
      setError(e.response.data);
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    setPage(0);
    setEnzymeList([]);
    void fetchEnzymes();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - fetchEnzymes will not meaningfully change
     */
  }, [enzymeSet]);

  const handleRequestSort = (_event: mixed, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_event: mixed, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: {
    target: { value: number, ... },
    ...
  }) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const emptyRows =
    rowsPerPage - Math.min(rowsPerPage, enzymeList.length - page * rowsPerPage);

  return (
    <Grid
      container
      spacing={2}
      component="section"
      role="tabpanel"
      style={{ display: show ? "flex" : "none" }}
      flexWrap="nowrap"
      aria-labelledby={idOfRestrictionSitesTab}
    >
      <Grid item flexGrow={1}>
        {error && <div>{error}</div>}
        {!error && loading && <LoadingCircular />}
        {!error && !loading && (
          <>
            <TableContainer style={{ maxHeight: "387px" }}>
              <Table
                stickyHeader
                aria-labelledby="Enzyme table"
                size="small"
                aria-label="enhanced table"
              >
                <EnhancedTableHead
                  headCells={enzymeHeadCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={enzymeList.length}
                />
                <TableBody>
                  {stableSort(enzymeList, getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    .map((enzyme) => (
                      <TableRow
                        hover
                        tabIndex={-1}
                        key={`${enzyme.name}-${enzyme.id}-${enzyme.topCutPosition}-${enzyme.bottomCutPosition}`}
                      >
                        <TableCell align="left">{enzyme.name}</TableCell>
                        <TableCell align="right">
                          {enzyme.bottomCutPosition}
                        </TableCell>
                        <TableCell align="right">
                          {enzyme.topCutPosition}
                        </TableCell>
                      </TableRow>
                    ))}
                  {emptyRows > 0 && (
                    <TableRow style={{ height: 33 * emptyRows }}>
                      <TableCell colSpan={6} />
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={paginationOptions(enzymeList.length)}
              component="div"
              count={enzymeList.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </>
        )}
      </Grid>

      <Grid item>
        <FormControl component="fieldset">
          <FormLabel component="legend" sx={{ textAlign: "right" }}>
            Enzyme Sets
          </FormLabel>
          <RadioGroup
            aria-label="Enzyme type"
            name="enzymeSet"
            value={enzymeSet}
            onChange={(event) => setEnzymeSet(event.target.value)}
          >
            {Object.keys(enzymeSetOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={enzymeSetOptions[key]}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </Grid>
  );
}

function ViewAsFasta({
  show,
  file,
  idOfViewAsFastaTab,
}: {|
  show: boolean,
  file: GalleryFile,
  idOfViewAsFastaTab: string,
|}) {
  const [sequence, setSequence] = React.useState<null | string>(null);

  React.useEffect(() => {
    try {
      const url = `/molbiol/dna/fasta/${idToString(file.id).elseThrow()}`;
      void axios
        .get<string>(url)
        .then((response) => {
          setSequence(response.data);
        })
        .catch((e) => {
          setSequence(e.response.data);
        });
    } catch (e) {
      setSequence(e.message);
    }
  }, [file]);

  return (
    <section
      role="tabpanel"
      style={{ display: show ? "flex" : "none" }}
      aria-labelledby={idOfViewAsFastaTab}
    >
      <pre>{sequence}</pre>
    </section>
  );
}

const readingFrameOptions = {
  ALL: { label: "All", filter: [-3, -2, -1, 1, 2, 3] },
  FORWARD: { label: "Forward", filter: [1, 2, 3] },
  REVERSE: { label: "Reverse", filter: [-1, -2, -3] },
  FIRST_FORWARD: { label: "First forward", filter: [1] },
  FIRST_REVERSE: { label: "First reverse", filter: [-1] },
};

const orfHeadCells = [
  {
    id: "fullRangeBegin",
    numeric: false,
    disablePadding: false,
    label: "Full Range Begin",
  },
  {
    id: "fullRangeEnd",
    numeric: false,
    disablePadding: false,
    label: "Full Range End",
  },
  {
    id: "molecularWeight",
    numeric: false,
    disablePadding: false,
    label: "Molecular Weight",
  },
  {
    id: "readingFrame",
    numeric: false,
    disablePadding: false,
    label: "Reading Frame",
  },
  {
    id: "translation",
    numeric: false,
    disablePadding: false,
    label: "Translation",
  },
];

type Orf = {|
  id: number,
  fullRangeBegin: number,
  fullRangeEnd: number,
  molecularWeight: number,
  readingFrame: number,
  translation: string,
|};

function OrfTable({
  show,
  file,
  idOfOrfTableTab,
}: {|
  show: boolean,
  file: GalleryFile,
  idOfOrfTableTab: string,
|}) {
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("version");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(10);
  const [loading, setLoading] = React.useState(true);
  const [readingFrameOption, setReadingFrameOption] = React.useState("ALL");
  const [results, setResults] = React.useState<$ReadOnlyArray<Orf>>([]);
  const [filteredResults, setFilteredResults] = React.useState<
    $ReadOnlyArray<Orf>
  >([]);
  const [error, setError] = React.useState<null | string>(null);

  const filterResults = (passedResults: $ReadOnlyArray<Orf>) => {
    const toInclude = readingFrameOptions[readingFrameOption].filter;
    const filtered = passedResults.filter((r) =>
      toInclude.includes(r.readingFrame)
    );
    setFilteredResults(filtered);
  };

  const fetchData = async () => {
    setLoading(true);

    try {
      const url = `/molbiol/dna/orfs/${idToString(file.id).elseThrow()}`;
      const response = await axios.get<{
        ORFs: $ReadOnlyArray<Orf>,
      }>(url);
      setResults(response.data.ORFs);
      filterResults(response.data.ORFs);
    } catch (e) {
      setError(e.response.data);
    } finally {
      setLoading(false);
    }
  };

  React.useEffect(() => {
    void fetchData();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - fetchData will not meaningfully change
     */
  }, []);

  React.useEffect(() => {
    setPage(0);
    filterResults(results);
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - filterResultswill not meaningfully change
     */
  }, [readingFrameOption]);

  const handleRequestSort = (_event: mixed, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_event: mixed, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: {
    target: { value: string, ... },
    ...
  }) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const emptyRows =
    rowsPerPage -
    Math.min(rowsPerPage, filteredResults.length - page * rowsPerPage);

  return (
    <Grid
      container
      spacing={2}
      flexWrap="nowrap"
      component="section"
      role="tabpanel"
      style={{ display: show ? "flex" : "none" }}
      aria-labelledby={idOfOrfTableTab}
    >
      <Grid item sx={{ minWidth: 0 }}>
        {error && <div>{error}</div>}
        {loading && !error && <LoadingCircular />}
        {!loading && !error && (
          <>
            <TableContainer style={{ maxHeight: "449px" }}>
              <Table
                stickyHeader
                aria-labelledby="ORF table"
                size="small"
                aria-label="enhanced table"
              >
                <EnhancedTableHead
                  headCells={orfHeadCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={filteredResults.length}
                />
                <TableBody>
                  {stableSort(filteredResults, getSorting(order, orderBy))
                    .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                    .map((result) => (
                      <TableRow hover tabIndex={-1} key={result.id}>
                        <TableCell align="left">
                          {result.fullRangeBegin}
                        </TableCell>
                        <TableCell align="left">
                          {result.fullRangeEnd}
                        </TableCell>
                        <TableCell align="left">
                          {result.molecularWeight}
                        </TableCell>
                        <TableCell align="left">
                          {result.readingFrame}
                        </TableCell>
                        <TableCell align="left">{result.translation}</TableCell>
                      </TableRow>
                    ))}
                  {emptyRows > 0 && (
                    <TableRow style={{ height: 33 * emptyRows }}>
                      <TableCell colSpan={6} />
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination
              rowsPerPageOptions={paginationOptions(filteredResults.length)}
              component="div"
              count={filteredResults.length}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </>
        )}
      </Grid>

      <Grid item sx={{ minWidth: "200px" }}>
        <FormControl component="fieldset">
          <FormLabel component="legend" sx={{ textAlign: "right" }}>
            Open Reading Frames
          </FormLabel>
          <RadioGroup
            aria-label="Enzyme type"
            name="enzymeSet"
            value={readingFrameOption}
            onChange={(event) => setReadingFrameOption(event.target.value)}
          >
            {Object.keys(readingFrameOptions).map((key) => (
              <FormControlLabel
                value={key}
                key={key}
                control={<Radio color="primary" />}
                label={readingFrameOptions[key].label}
                labelPlacement="start"
              />
            ))}
          </RadioGroup>
        </FormControl>
      </Grid>
    </Grid>
  );
}

/*
 * If snapgene is configured, then users can preview the contents of various
 * common dna file types by passing the file id to the snapgene microservice,
 * and visualizing the resulting data.
 *
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a dialog by
 * providing a GalleryFile that can be previewed by SnapGene.
 */

const SnapGenePreviewContext = React.createContext({
  setFile: (_file: GalleryFile) => {},
});

/**
 * Use the callable snapgene preview component to display a dna sequence in a dialog.
 */
export function useSnapGenePreview(): {|
  /**
   * Preview the dna sequence at this GalleryFile.
   */
  openSnapGenePreview: (GalleryFile) => void,
|} {
  const { setFile: openSnapGenePreview } = React.useContext(
    SnapGenePreviewContext
  );
  return {
    openSnapGenePreview,
  };
}

/**
 * This components provides a mechanism for any other component that is its
 * descendent to trigger the previewing of a dna sequence by passing the GalleryFile
 * to a call to `useSnapGenePreview`'s `openSnapGenePreview`. Just do something like
 *   const { openSnapGenePreview } = useSnapGenePreview();
 *   openSnapGenePreview(dnaFile);
 */
export function CallableSnapGenePreview({
  children,
}: {|
  children: Node,
|}): Node {
  const [file, setFile] = React.useState<GalleryFile | null>(null);
  const [tab, setTab] = React.useState("DNA preview");
  const idOfDnaPreviewTab = React.useId();
  const idOfRestrictionSitesTab = React.useId();
  const idOfViewAsFastaTab = React.useId();
  const idOfOrfTableTab = React.useId();

  function switchTab(
    _e: Event,
    value: "DNA preview" | "Restriction sites" | "View as FASTA" | "ORF table"
  ) {
    setTab(value);
  }

  const openSnapGenePreview = (f: GalleryFile) => {
    setFile(f);
  };

  return (
    <SnapGenePreviewContext.Provider value={{ setFile: openSnapGenePreview }}>
      {children}
      {file && (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Dialog
            open
            onClose={() => {
              setFile(null);
            }}
            fullWidth
            maxWidth="xl"
          >
            <AppBar
              variant="dialog"
              currentPage="SnapGene"
              accessibilityTips={{
                supportsHighContrastMode: true,
              }}
            />
            <Stack direction="row" spacing={1} sx={{ minHeight: 0 }}>
              <CustomDrawer
                variant="permanent"
                sx={{ mt: 2 }}
                role="tablist"
                aria-orientation="vertical"
              >
                <ListItem
                  disablePadding
                  role="tab"
                  aria-label="DNA preview"
                  aria-selected={tab === "DNA preview"}
                  id={idOfDnaPreviewTab}
                >
                  <ListItemButton
                    selected={tab === "DNA preview"}
                    onClick={(e) => switchTab(e, "DNA preview")}
                  >
                    <ListItemText primary="DNA Preview" />
                  </ListItemButton>
                </ListItem>
                <ListItem
                  disablePadding
                  role="tab"
                  aria-label="Restriction sites"
                  aria-selected={tab === "Restriction sites"}
                  id={idOfRestrictionSitesTab}
                >
                  <ListItemButton
                    selected={tab === "Restriction sites"}
                    onClick={(e) => switchTab(e, "Restriction sites")}
                  >
                    <ListItemText primary="Restriction Sites" />
                  </ListItemButton>
                </ListItem>
                <ListItem
                  disablePadding
                  role="tab"
                  aria-label="FASTA"
                  aria-selected={tab === "View as FASTA"}
                  id={idOfViewAsFastaTab}
                >
                  <ListItemButton
                    selected={tab === "View as FASTA"}
                    onClick={(e) => switchTab(e, "View as FASTA")}
                  >
                    <ListItemText primary="FASTA" />
                  </ListItemButton>
                </ListItem>
                <ListItem
                  disablePadding
                  role="tab"
                  aria-label="ORF table"
                  aria-selected={tab === "ORF table"}
                  id={idOfOrfTableTab}
                >
                  <ListItemButton
                    selected={tab === "ORF table"}
                    onClick={(e) => switchTab(e, "ORF table")}
                  >
                    <ListItemText primary="ORF Table" />
                  </ListItemButton>
                </ListItem>
              </CustomDrawer>
              <Stack
                orientation="vertical"
                spacing={1}
                flexGrow={1}
                sx={{ minWidth: 0 }}
              >
                <DialogContent>
                  <DnaPreview
                    show={tab === "DNA preview"}
                    file={file}
                    idOfDnaPreviewTab={idOfDnaPreviewTab}
                  />
                  <RestrictionSites
                    show={tab === "Restriction sites"}
                    file={file}
                    idOfRestrictionSitesTab={idOfRestrictionSitesTab}
                  />
                  <ViewAsFasta
                    show={tab === "View as FASTA"}
                    file={file}
                    idOfViewAsFastaTab={idOfViewAsFastaTab}
                  />
                  <OrfTable
                    show={tab === "ORF table"}
                    file={file}
                    idOfOrfTableTab={idOfOrfTableTab}
                  />
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setFile(null)}>Close</Button>
                </DialogActions>
              </Stack>
            </Stack>
          </Dialog>
        </ThemeProvider>
      )}
    </SnapGenePreviewContext.Provider>
  );
}
