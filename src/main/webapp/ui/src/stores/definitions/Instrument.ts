import type { Field } from "./Field";
import type { HasLocation } from "./HasLocation";
import type { InventoryRecord } from "./InventoryRecord";

export interface Instrument extends InventoryRecord, HasLocation {
  fields: Array<Field>;
}
