//@flow

import React, { type Node, type Context } from "react";
import { type GalleryFile } from "./useGalleryListing";
import { makeObservable, action, observable, computed } from "mobx";
import RsSet from "../../util/set";

/*
 * We use a Map even though the interface exposed by this module is more akin
 * to a Set because we want to be able to check the inclusion of files based on
 * their Id. There are various points in the UI where the particular objects in
 * memory that are modelling the Gallery Files are replaced, and so we must
 * compare based on their Ids to make sure we don't end up with the same file
 * added to the Selection twice. We can't use RsSet, even though it has
 * `hasWithEq` that would suffice, because it is not observable.
 */
class Selection {
  /*
   * Mobx does not support ECMA private fields, so we are forced to expose this
   * property. NOBODY outside of this module should fiddle with this Map.
   */
  _state: Map<GalleryFile["id"], GalleryFile>;

  constructor() {
    makeObservable(this, {
      _state: observable,
      isEmpty: computed,
      size: computed,
      clear: action,
      append: action,
      remove: action,
    });
    this._state = new Map<GalleryFile["id"], GalleryFile>();
  }

  get isEmpty(): boolean {
    return this._state.size === 0;
  }

  get size(): number {
    return this._state.size;
  }

  clear() {
    this._state.clear();
  }

  append(file: GalleryFile) {
    this._state.set(file.id, file);
  }

  remove(file: GalleryFile) {
    this._state.delete(file.id);
  }

  includes(file: GalleryFile): boolean {
    return this._state.has(file.id);
  }

  asSet(): RsSet<GalleryFile> {
    return new RsSet(this._state.values());
  }
}

const DEFAULT_SELECTION_CONTEXT: Selection = new Selection();

const SelectionContext: Context<Selection> = React.createContext(
  DEFAULT_SELECTION_CONTEXT
);

export const GallerySelection = ({ children }: {| children: Node |}): Node => (
  <SelectionContext.Provider value={new Selection()}>
    {children}
  </SelectionContext.Provider>
);

export function useGallerySelection(): Selection {
  return React.useContext(SelectionContext);
}
