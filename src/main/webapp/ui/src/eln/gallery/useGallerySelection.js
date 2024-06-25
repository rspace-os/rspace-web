//@flow

import React, { type Node, type Context } from "react";
import { type GalleryFile } from "./useGalleryListing";
import { observable, runInAction } from "mobx";
import RsSet from "../../util/set";

/*
 * Other modules should not be accessing the context directly, but instead
 * using the custom hook defined below. By not exporting the type flow will
 * complain if the context is accessed directly.
 *
 * We use a Map even though the interface exposed by this module is more akin
 * to a Set because we want to be able to check the inclusion of files based on
 * their Id. There are various points in the UI where the particular objects in
 * memory that are modelling the Gallery Files are replaced, and so we must
 * compare based on their Ids to make sure we don't end up with the same file
 * added to the Selection twice. We can't use RsSet, even though it has
 * `hasWithEq` that would suffice, because it is not observable.
 */
type Selection = Map<GalleryFile["id"], GalleryFile>;

function mkSelection(): Selection {
  // $FlowExpectedError[prop-missing] Difficult to get this library type right
  return observable.map();
}

type SelectionContextType = {|
  selection: Selection,
|};

const DEFAULT_SELECTION_CONTEXT: SelectionContextType = {
  selection: mkSelection(),
};

const SelectionContext: Context<SelectionContextType> = React.createContext(
  DEFAULT_SELECTION_CONTEXT
);

export const GallerySelection = ({ children }: {| children: Node |}): Node => (
  <SelectionContext.Provider
    value={{
      selection: mkSelection(),
    }}
  >
    {children}
  </SelectionContext.Provider>
);

export function useGallerySelection(): {|
  isEmpty: () => boolean,
  clear: () => void,
  append: (GalleryFile) => void,
  remove: (GalleryFile) => void,
  includes: (GalleryFile) => boolean,
  asSet: () => RsSet<GalleryFile>,
|} {
  const { selection } = React.useContext(SelectionContext);
  return {
    isEmpty: () => selection.size === 0,
    clear: () => {
      runInAction(() => {
        selection.clear();
      });
    },
    append: (file) => {
      runInAction(() => {
        selection.set(file.id, file);
      });
    },
    remove: (file) => {
      runInAction(() => {
        selection.delete(file.id);
      });
    },
    includes: (file) => {
      return selection.has(file.id);
    },
    asSet: () => new RsSet(selection.values()),
  };
}
