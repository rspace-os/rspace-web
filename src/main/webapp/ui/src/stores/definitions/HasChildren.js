// @flow

import { type InventoryRecord } from "./InventoryRecord";

/*
 * Defines a tree-like data structure for the purposes of Tree view. Is
 * implemented differently according to the parent-child relationship of
 * various record types.
 */
export interface HasChildren {
  loadChildren(): void;
  +children: Array<InventoryRecord>;

  /*
   * If true, then the UI MAY provide a mechanism to navigate to a listing of
   * the child records from a search result of this record.
   */
  +canNavigateToChildren: boolean;
}
