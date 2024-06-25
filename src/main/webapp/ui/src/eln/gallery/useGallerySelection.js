//@flow

import React, { type Node, type Context } from "react";
import { type GalleryFile, idToString } from "./useGalleryListing";
import { observable, runInAction } from "mobx";
import RsSet from "../../util/set";

/*
 * Other modules should not be accessing the context directly, but instead
 * using the custom hook defined below. By not exporting the type flow will
 * complain if the context is accessed directly.
 */
type Selection = Map<string, GalleryFile>;

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
  asSetOfIds: () => Set<GalleryFile["id"]>,
  asTreeViewModel: () => $ReadOnlyArray<string>,
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
        selection.set(idToString(file.id), file);
      });
    },
    remove: (file) => {
      runInAction(() => {
        selection.delete(idToString(file.id));
      });
    },
    includes: (file) => {
      return selection.has(idToString(file.id));
    },
    asSet: () => new RsSet(selection.values()),
    asSetOfIds: () => new Set([...selection.values()].map(({ id }) => id)),
    asTreeViewModel: () => [...selection.keys()],
  };
}
