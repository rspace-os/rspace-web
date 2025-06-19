import { type InventoryRecord } from "./InventoryRecord";
import { type HasLocation } from "./HasLocation";

export interface SubSample extends InventoryRecord, HasLocation {}
