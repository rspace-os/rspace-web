// biome-ignore lint/style/useImportType: initial biome migration
import { type GlobalId, type Id } from "./BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type PersonAttrs } from "./Person";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Record } from "./Record";

/**
 * A simple definition of an ELN document, sufficient for use in Inventory.
 */
export type DocumentAttrs = {
  id: Id;
  globalId: GlobalId;
  name: string;
  owner: PersonAttrs | null;
};

export type Document = Record;
