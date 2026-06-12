// biome-ignore lint/style/useImportType: initial biome migration
import { type Id } from "./BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Field } from "./Field";
import type { InventoryRecord } from "./InventoryRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Template } from "./Template";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Temperature } from "./Units";

export type Alias = { alias: string; plural: string };
export type SampleSource = "LAB_CREATED" | "VENDOR_SUPPLIED" | "OTHER";

export interface Sample extends InventoryRecord {
  template: Template | null;
  storageTempMin: Temperature | null;
  storageTempMax: Temperature | null;
  sampleSource: SampleSource;
  subSampleAlias: Alias;
  expiryDate: string | null;

  setTemplate(template: Template): Promise<void>;
  sampleCreationParams(includeContentForFields: Set<Id>): Promise<object>;

  fields: Array<Field>;
  /*
   * If true, then FieldModel's mandatory flag MUST be respected when checking
   * whether it is valid, and consequently whether the whole sample is valid.
   * By implementing this as false, implementations MAY allow for its fields to
   * have no content even when the mandatory flag is true.
   */
  readonly enforceMandatoryFields: boolean;

  subSamplesCount: number;
}
