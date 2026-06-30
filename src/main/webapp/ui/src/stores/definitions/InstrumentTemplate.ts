import type { Field } from "./Field";
import type { InventoryRecord } from "./InventoryRecord";

export interface InstrumentTemplate extends InventoryRecord {
  version: number;
  fields: Array<Field>;
}
