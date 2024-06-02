//@flow

import { type Node } from "react";
import { type InventoryRecord } from "./InventoryRecord";
import { type Person } from "./Person";
import { type Tag } from "./Tag";

export type AdjustableTableRowLabel = string;
export type CellContent =
  | {| renderOption: "node", data: Node |}
  | {| renderOption: "name", data: InventoryRecord |}
  | {| renderOption: "globalId", data: InventoryRecord |}
  | {| renderOption: "location", data: InventoryRecord |}
  | {| renderOption: "owner", data: Person |}
  | {| renderOption: "tags", data: Array<Tag> |}
export type AdjustableTableRowOptions<T: AdjustableTableRowLabel> = Map<
  T,
  () => CellContent
>;

/*
 * All classes which implement this interface can be used as the source data
 * for a table row that has an adjustable column.
 */
export interface AdjustableTableRow<T: AdjustableTableRowLabel> {
  adjustableTableOptions(): AdjustableTableRowOptions<T>;
}
