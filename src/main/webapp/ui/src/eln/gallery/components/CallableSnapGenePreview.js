//@flow

import React, { type Node } from "react";
import { type GalleryFile } from "../useGalleryListing";
import { Dialog } from "../../../components/DialogBoundary";

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
        <Dialog open onClose={() => {}}>
          {file.name}
        </Dialog>
      )}
    </SnapGenePreviewContext.Provider>
  );
}
