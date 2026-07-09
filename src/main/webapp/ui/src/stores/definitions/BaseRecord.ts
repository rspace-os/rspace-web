import i18n from "@/modules/common/i18n";

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
export type GlobalIdPrefix = "SA" | "SS" | "IC" | "IT" | "BE" | "BA" | "IF" | "SF" | "SD" | "GP" | "IN" | "NT";

/**
 * The Global ID pattern is a regular expression that matches all Global IDs of
 * a given class of record, paired with the two-character prefix used to build
 * one from an id (see `globalId` below). Keeping the pattern and prefix
 * together, rather than in two separate maps, is what makes "type + id <->
 * Global ID" an isomorphism: there is only one place where a record class can
 * fall out of sync with its own Global ID format.
 */
export const globalIdDefinitions = {
  // samples, subsamples, containers, instruments, and templates (sample and
  // instrument) support an optional version suffix (e.g. SS4v1), identifying
  // a historical version
  sample: { pattern: /^sa\d+(v\d+)?$/i, prefix: "SA" },
  subsample: { pattern: /^ss\d+(v\d+)?$/i, prefix: "SS" },
  container: { pattern: /^ic\d+(v\d+)?$/i, prefix: "IC" },
  sampleTemplate: { pattern: /^it\d+(v\d+)?$/i, prefix: "IT" },
  bench: { pattern: /^be\d+$/i, prefix: "BE" },
  basket: { pattern: /^ba\d+$/i, prefix: "BA" },
  attachment: { pattern: /^if\d+$/i, prefix: "IF" },
  field: { pattern: /^sf\d+$/i, prefix: "SF" },
  document: { pattern: /^sd\d+$/i, prefix: "SD" },
  group: { pattern: /^gp\d+$/i, prefix: "GP" },
  instrument: { pattern: /^in\d+(v\d+)?$/i, prefix: "IN" },
  instrumentTemplate: { pattern: /^nt\d+(v\d+)?$/i, prefix: "NT" },
} satisfies Record<string, { pattern: RegExp; prefix: GlobalIdPrefix }>;

/*
 * The corresponding label, as should be rendered by the UI, for each Inventory
 * record.
 */
export const inventoryRecordTypeLabels = {
  get sample(): string {
    return i18n.t("inventory:recordTypes.sample.singular");
  },
  get subsample(): string {
    return i18n.t("inventory:recordTypes.subsample.singular");
  },
  get container(): string {
    return i18n.t("inventory:recordTypes.container.singular");
  },
  get sampleTemplate(): string {
    return i18n.t("inventory:recordTypes.sampleTemplate.singular");
  },
  get bench(): string {
    return i18n.t("inventory:recordTypes.bench.singular");
  },
  get basket(): string {
    return i18n.t("inventory:recordTypes.basket.singular");
  },
  get instrument(): string {
    return i18n.t("inventory:recordTypes.instrument.singular");
  },
  get instrumentTemplate(): string {
    return i18n.t("inventory:recordTypes.instrumentTemplate.singular");
  },
};

/**
 * Given a Global ID of an *Inventory* record, return a label for the type of
 * record.
 */
export const globalIdToInventoryRecordTypeLabel = (
  globalId: GlobalId,
): (typeof inventoryRecordTypeLabels)[keyof typeof inventoryRecordTypeLabels] => {
  const type = (Object.keys(inventoryRecordTypeLabels) as Array<keyof typeof inventoryRecordTypeLabels>).find((t) =>
    globalIdDefinitions[t].pattern.test(globalId),
  );
  if (!type) throw new Error("No pattern matches");
  return inventoryRecordTypeLabels[type];
};

/**
 * Creates the Global ID string for a given record type and ID.
 */
export function globalId({ type, id }: { type: keyof typeof globalIdDefinitions; id: number }): GlobalId {
  return `${globalIdDefinitions[type].prefix}${id}`;
}

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
  if (record.globalId === null || typeof record.globalId === "undefined") throw new TypeError('"globalId" is null.');
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
