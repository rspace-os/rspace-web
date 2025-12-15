import { type GlobalId, type Id } from "./BaseRecord";
import { type Record } from "./Record";
import { type PersonAttrs } from "./Person";

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
