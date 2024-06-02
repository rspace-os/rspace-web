//@flow
import { type RecordType, type InventoryRecord } from "./InventoryRecord";
import { type GlobalId } from "./BaseRecord";

/*
 * This interfaces describes the data necessary for tree view
 * of search results.
 */
export interface TreeView {
  filteredTypes: Array<RecordType>;

  expanded: Array<string>;
  setExpanded(Array<string>): void;

  +selected: ?GlobalId;
  setSelected(?GlobalId): void;

  +selectedNode: ?InventoryRecord;
}
