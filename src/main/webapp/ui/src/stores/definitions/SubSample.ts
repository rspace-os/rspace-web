// biome-ignore lint/style/useImportType: initial biome migration
import { type HasLocation } from "./HasLocation";
import type { InventoryRecord } from "./InventoryRecord";

export interface SubSample extends InventoryRecord, HasLocation {}
