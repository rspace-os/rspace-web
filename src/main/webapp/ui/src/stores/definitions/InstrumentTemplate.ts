import { type InventoryRecord } from "./InventoryRecord";
import { type Field } from "./Field";

export interface InstrumentTemplate extends InventoryRecord {
  version: number;
  fields: Array<Field>;
}
