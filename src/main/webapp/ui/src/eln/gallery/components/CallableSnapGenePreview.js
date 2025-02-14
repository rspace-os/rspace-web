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
  loading: false,
});

/**
 * Use the callable snapgene preview component to display a dna sequence in a dialog.
 */
export function useSnapGenePreview(): {|
  /**
   * Preview the dna sequence at this GalleryFile.
   */
  openSnapGenePreview: (GalleryFile) => void,

  loading: boolean,
|} {
  const { setFile: openSnapGenePreview, loading } = React.useContext(
    SnapGenePreviewContext
  );
  return {
    openSnapGenePreview,
    loading,
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
  const [loading, setLoading] = React.useState(false);
  const [tab, setTab] = React.useState(0);

  function switchTab(e: Event, value: number) {
    setTab(value);
  }

  const openSnapGenePreview = (f: GalleryFile) => {
    setFile(f);
    setLoading(true);
  };

  return (
    <SnapGenePreviewContext.Provider
      value={{ setFile: openSnapGenePreview, loading }}
    >
      {children}
      {file && (
        <Dialog
          open
          onClose={() => {
            setFile(null);
            setLoading(false);
          }}
        >
          <DialogTitle>SnapGene</DialogTitle>
          <DialogContent>
            <Tabs value={tab} onChange={switchTab}>
              <Tab label="DNA preview" />
              <Tab label="Enzyme sites" />
              <Tab label="View as FASTA" />
              <Tab label="ORF table" />
            </Tabs>
            <DnaPreview show={tab === 0} />
            <EnzymeSites show={tab === 1} />
            <ViewAsFasta show={tab === 2} />
            <OrfTable show={tab === 3} />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setFile(null)}>Close</Button>
          </DialogActions>
        </Dialog>
      )}
    </SnapGenePreviewContext.Provider>
  );
}
