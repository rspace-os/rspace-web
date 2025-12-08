import type { GlobalId, Id } from "./BaseRecord";
import type { PersonAttrs } from "./Person";
import type { Record } from "./Record";

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
