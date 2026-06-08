import { type InventoryRecord } from "./InventoryRecord";
import { type HasLocation } from "./HasLocation";

export interface Instrument extends InventoryRecord, HasLocation {}
