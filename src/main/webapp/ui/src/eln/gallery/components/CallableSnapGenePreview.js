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
import axios from "axios";

const COLOR = {
  main: {
    hue: 188,
    saturation: 46,
    lightness: 70,
  },
  darker: {
    hue: 188,
    saturation: 93,
    lightness: 33,
  },
  contrastText: {
    hue: 188,
    saturation: 35,
    lightness: 26,
  },
  background: {
    hue: 188,
    saturation: 25,
    lightness: 71,
  },
  backgroundContrastText: {
    hue: 188,
    saturation: 11,
    lightness: 24,
  },
};

const CustomDrawer = styled(Drawer)(() => ({
  "& .MuiPaper-root": {
    paddingTop: "8px",
    position: "relative",
  },
}));

function DnaPreview({ show, file }: {| show: boolean, file: GalleryFile |}) {
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
          label="Show enzymes"
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
          border: `2px solid hsl(${COLOR.background.hue}deg, ${COLOR.background.saturation}%, ${COLOR.background.lightness}%)`,
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

function EnzymeSites({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "flex" : "none" }}>
      Enzyme Sites
    </section>
  );
}

function ViewAsFasta({ show, file }: {| show: boolean, file: GalleryFile |}) {
  const [sequence, setSequence] = React.useState<null | string>(null);

  React.useEffect(() => {
    try {
      const url = `/molbiol/dna/fasta/${idToString(file.id).elseThrow()}`;
      void axios.get<string>(url).then((response) => {
        setSequence(response.data);
      });
    } catch (e) {
      setSequence(e.message);
    }
  }, [file]);

  return (
    <section role="tabpanel" style={{ display: show ? "flex" : "none" }}>
      <pre>{sequence}</pre>
    </section>
  );
}

function OrfTable({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "flex" : "none" }}>
      ORF Table
    </section>
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

  function switchTab(
    _e: Event,
    value: "DNA preview" | "Enzyme sites" | "View as FASTA" | "ORF table"
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
        <ThemeProvider theme={createAccentedTheme(COLOR)}>
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
              <CustomDrawer variant="permanent" sx={{ mt: 2 }}>
                <ListItem disablePadding>
                  <ListItemButton
                    selected={tab === "DNA preview"}
                    onClick={(e) => switchTab(e, "DNA preview")}
                  >
                    <ListItemText primary="DNA Preview" />
                  </ListItemButton>
                </ListItem>
                <ListItem disablePadding>
                  <ListItemButton
                    selected={tab === "Enzyme sites"}
                    onClick={(e) => switchTab(e, "Enzyme sites")}
                  >
                    <ListItemText primary="Enzyme Sites" />
                  </ListItemButton>
                </ListItem>
                <ListItem disablePadding>
                  <ListItemButton
                    selected={tab === "View as FASTA"}
                    onClick={(e) => switchTab(e, "View as FASTA")}
                  >
                    <ListItemText primary="FASTA" />
                  </ListItemButton>
                </ListItem>
                <ListItem disablePadding>
                  <ListItemButton
                    selected={tab === "ORF table"}
                    onClick={(e) => switchTab(e, "ORF table")}
                  >
                    <ListItemText primary="ORF Table" />
                  </ListItemButton>
                </ListItem>
              </CustomDrawer>
              <Stack orientation="vertical" spacing={1} flexGrow={1}>
                <DialogContent>
                  <DnaPreview show={tab === "DNA preview"} file={file} />
                  <EnzymeSites show={tab === "Enzyme sites"} />
                  <ViewAsFasta show={tab === "View as FASTA"} file={file} />
                  <OrfTable show={tab === "ORF table"} />
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
