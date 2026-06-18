import { type InventoryRecord } from "./InventoryRecord";
import { type HasLocation } from "./HasLocation";
import { type Field } from "./Field";

export interface Instrument extends InventoryRecord, HasLocation {
  fields: Array<Field>;
}
