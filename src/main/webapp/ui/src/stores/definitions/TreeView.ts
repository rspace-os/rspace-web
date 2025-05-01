import { type RecordType, type InventoryRecord } from "./InventoryRecord";
import { type GlobalId } from "./BaseRecord";

/*
 * This interfaces describes the data necessary for tree view
 * of search results.
 */
export interface TreeView {
  filteredTypes: Array<RecordType>;

  expanded: Array<string>;
  setExpanded(ids: Array<string>): void;

  readonly selected: GlobalId | null;
  setSelected(globalId: GlobalId | null): void;

  readonly selectedNode: InventoryRecord | null;
}
