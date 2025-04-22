import { match } from "../../util/Util";

/**
 * IDs are used to identify a record within a given class of record i.e. to
 * identify a container amongst all other containers.
 *
 * Record IDs can either be null (the record is new and not yet stored on the
 * server, the sample does not have an assigned template, etc) or it can have a
 * non-zero positive integer value. As such, boolean logic can be used to check
 * for null.
 */
export type Id = number | null;

/**
 * A Global ID is a uniue identifier for a record over all records of all
 * classes. It MUST be the ID prefixed with a identifier for the class of
 * record.  The record classes, and thus Global IDs, supported by the
 * frontend code are detailed below.
 */
export type GlobalId = string;

/**
 * The Global ID prefix is a two character string that identifies the class of
 * record that the Global ID refers to. Various record classes supported by the
 * frontend code are detailed below.
 */
export type GlobalIdPrefix =
  | "SA"
  | "SS"
  | "IC"
  | "IT"
  | "BE"
  | "BA"
  | "IF"
  | "SF"
  | "SD"
  | "GP";

/**
 * The Global ID pattern is a regular expression that matches all Global IDs of
 * a given class of record. This object provides a pattern for various classes
 * of record supported by the frontend code.
 */
export const globalIdPatterns: Record<string, RegExp> = {
  sample: /^sa\d+$/i,
  subsample: /^ss\d+$/i,
  container: /^ic\d+$/i,
  sampleTemplate: /^it\d+(v\d+)?$/i,
  bench: /^be\d+$/i,
  basket: /^ba\d+$/i,
  attachment: /^if\d+$/i,
  field: /^sf\d+$/i,
  document: /^sd\d+$/i,
  group: /^gp\d+$/i,
};

/*
 * The corresponding label, as should be rendered by the UI, for each Inventory
 * record.
 */
export const inventoryRecordTypeLabels = {
  sample: "Sample",
  subsample: "Subsample",
  container: "Container",
  sampleTemplate: "Sample Template",
  bench: "Bench",
  basket: "Basket",
};

/**
 * Given a Global ID of an *Inventory* record, return a label for the type of
 * record.
 */
export const globalIdToInventoryRecordTypeLabel: (
  globalId: GlobalId
) => (typeof inventoryRecordTypeLabels)[keyof typeof inventoryRecordTypeLabels] =
  match([
    [
      (globalId: GlobalId) => globalIdPatterns.sample.test(globalId),
      inventoryRecordTypeLabels.sample,
    ],
    [
      (globalId: GlobalId) => globalIdPatterns.subsample.test(globalId),
      inventoryRecordTypeLabels.subsample,
    ],
    [
      (globalId: GlobalId) => globalIdPatterns.container.test(globalId),
      inventoryRecordTypeLabels.container,
    ],
    [
      (globalId: GlobalId) => globalIdPatterns.sampleTemplate.test(globalId),
      inventoryRecordTypeLabels.sampleTemplate,
    ],
    [
      (globalId: GlobalId) => globalIdPatterns.bench.test(globalId),
      inventoryRecordTypeLabels.bench,
    ],
    [
      (globalId: GlobalId) => globalIdPatterns.basket.test(globalId),
      inventoryRecordTypeLabels.basket,
    ],
  ]);

/**
 * This is the canonical definition of what the frontend considers to be the
 * base record of all discrete pieces of data persisted by the backend that are
 * uniquely identifiable.
 */
export interface BaseRecord {
  id: Id;
  name: string;

  /**
   * Global ID MUST be null when user is creating a new record that has not yet
   * been persisted on the server, and MUST NOT be null at any other time.
   */
  globalId: null | GlobalId;
}

/**
 * Once saved to the server all records have a Global ID, but this fact is not
 * encoded in the BaseRecord type. As such, Flow will complain if the globalId
 * is extracted from a record and used as if it were not null, even in places
 * where other logic is enforcing that only saved records will be available.
 * This function extracts the Global ID in a way that enforces the invariant
 * that the Global ID will be available in a way that Flow can recognise.
 */
export const getSavedGlobalId = (record: BaseRecord): GlobalId => {
  if (record.globalId === null || typeof record.globalId === "undefined")
    throw new TypeError('"globalId" is null.');
  return record.globalId;
};

/**
 * The data needed by the components that render the icons of the various
 * record types.
 */
export interface RecordIconData {
  // Used for determining the icon
  readonly iconName: string;

  // Used for displaying tooltip and aria-label
  readonly recordTypeLabel: string;
}
