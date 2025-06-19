import React from "react";
import { type InventoryRecord } from "./InventoryRecord";
import { type Person } from "./Person";
import { type Tag } from "./Tag";

export type AdjustableTableRowLabel = string;
export type CellContent =
  | { renderOption: "node"; data: React.ReactNode }
  | { renderOption: "name"; data: InventoryRecord }
  | { renderOption: "globalId"; data: InventoryRecord }
  | { renderOption: "location"; data: InventoryRecord }
  | { renderOption: "owner"; data: Person }
  | { renderOption: "tags"; data: Array<Tag> };
export type AdjustableTableRowOptions<T extends AdjustableTableRowLabel> = Map<
  T,
  () => CellContent
>;

/*
 * All classes which implement this interface can be used as the source data
 * for a table row that has an adjustable column.
 */
export interface AdjustableTableRow<T extends AdjustableTableRowLabel> {
  adjustableTableOptions(): AdjustableTableRowOptions<T>;
}
