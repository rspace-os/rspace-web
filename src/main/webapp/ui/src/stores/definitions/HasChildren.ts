//@flow

import { type InventoryRecord } from "./InventoryRecord";

/**
 * Defines a generic tree data structure for Inventory records. Each class that
 * implements this interface can implement its own parent-child relationship,
 * allowing the UI code to be generic and not have to worry about the specifics
 * of the tree structure.
 */
export interface HasChildren {
  /**
   * If the implementor does not have the children available on construction
   * then this method MUST fetch the children. When it is done, `children`
   * should only be empty if there are not children.
   */
  loadChildren(): void;

  /**
   * The children of this record. If there are no children, then this array
   * MUST be empty. The array MAY also be empty if the children have not been
   * loaded yet.
   */
  readonly children: Array<InventoryRecord>;

  /**
   * If true, then the UI MAY provide a mechanism to navigate to a listing of
   * the child records from a search result of this record. The UI will do this
   * by relying on the data that all InventoryRecords have, such as id and
   * global id.
   */
  readonly canNavigateToChildren: boolean;
}
