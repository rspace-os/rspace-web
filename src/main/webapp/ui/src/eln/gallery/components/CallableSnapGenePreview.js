//@flow

import React, { type Node } from "react";
import { type GalleryFile } from "../useGalleryListing";
import { Dialog } from "../../../components/DialogBoundary";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";

function DnaPreview({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "block" : "none" }}>
      Previewing DNA
    </section>
  );
}

function EnzymeSites({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "block" : "none" }}>
      Enzyme Sites
    </section>
  );
}

function ViewAsFasta({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "block" : "none" }}>
      View as FASTA
    </section>
  );
}

function OrfTable({ show }: {| show: boolean |}) {
  return (
    <section role="tabpanel" style={{ display: show ? "block" : "none" }}>
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
    <SnapGenePreviewContext.Provider
      value={{ setFile: openSnapGenePreview }}
    >
      {children}
      {file && (
        <Dialog
          open
          onClose={() => {
            setFile(null);
          }}
        >
          <DialogTitle>SnapGene</DialogTitle>
          <DialogContent>
            <Tabs value={tab} onChange={switchTab}>
              <Tab label="DNA preview" value="DNA preview" />
              <Tab label="Enzyme sites" value="Enzyme sites" />
              <Tab label="View as FASTA" value="View as FASTA" />
              <Tab label="ORF table" value="ORF table" />
            </Tabs>
            <DnaPreview show={tab === "DNA preview"} />
            <EnzymeSites show={tab === "Enzyme sites"} />
            <ViewAsFasta show={tab === "View as FASTA"} />
            <OrfTable show={tab === "ORF table"} />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setFile(null)}>Close</Button>
          </DialogActions>
        </Dialog>
      )}
    </SnapGenePreviewContext.Provider>
  );
}
