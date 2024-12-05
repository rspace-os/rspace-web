//@flow

import React, { type Node } from "react";
import { type GalleryFile } from "../useGalleryListing";

/**
 * There are various ways to open a folder in a listing:
 *   - Tapping the open button in the right info panel on larger viewports
 *   - Tapping the open button in the floating info panel on smaller viewports
 *   - Double tapping a tree view node or a grid view card
 *   - Pressing the space key when a grid view card is focussed
 *
 * Note how some of them are intrinsic to the current view of the listing and
 * others are independent of the current view. The info panels do not know nor
 * care how the listing is currently being presented, just that a file is
 * selected and its metadata should be presented. However, exactly what it
 * means to open a folder *is* dependent on the current view and this
 * complicates things.
 *
 * In grid and carousel view, opening a folder means appending to the path of
 * the listing the selected folder. In tree view, it means doing the same but
 * to the root listing, not the listing that the selected file is a part of as
 * that will only be a section of the whole tree. The file itself does not know
 * that the listing that instantiated it was itself created within the context
 * of another listing and thus the file has no way of updating the path all the
 * way at the top of the tree.
 *
 * What we need is a mechanism for the current view to broadcast what it means
 * to open a folder, so that whatever part of the UI is triggering an open
 * operation can do so in the correct way.
 */

const OpenFolderContext = React.createContext({
  open: { f: (_file: GalleryFile) => {} },
  setOpen: (_newFunc: {| f: (GalleryFile) => void |}) => {},
});

export default function OpenFolderProvider({
  children,
}: {|
  children: Node,
|}): Node {
  const [open, setOpen] = React.useState<{| f: (GalleryFile) => void |}>({
    f: () => {},
  });

  return (
    <OpenFolderContext.Provider value={{ open, setOpen }}>
      {children}
    </OpenFolderContext.Provider>
  );
}

export function useSetFolderOpen(): {|
  setFolderOpen: ((GalleryFile) => void) => void,
|} {
  const { setOpen } = React.useContext(OpenFolderContext);
  return {
    setFolderOpen: (f) => setOpen({ f }),
  };
}

export function useFolderOpen(): {| openFolder: (GalleryFile) => void |} {
  const { open } = React.useContext(OpenFolderContext);
  return {
    openFolder: open.f,
  };
}
