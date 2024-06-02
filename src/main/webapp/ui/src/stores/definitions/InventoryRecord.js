//@flow

/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

import { match } from "../../util/Util";
import { globalIdPatterns, type Id, type GlobalId } from "./BaseRecord";
import { type Record, type ReadAccessLevel } from "./Record";
import { type BlobUrl } from "../stores/ImageStore";
import { type Editable } from "./Editable";
import { type HasChildren } from "./HasChildren";
import { type Factory } from "./Factory";
import { type Attachment } from "./Attachment";
import { type Identifier } from "./Identifier";
import { type Node } from "react";
import { type ExtraField, type ExtraFieldAttrs } from "./ExtraField";
import { type BarcodeRecord } from "./Barcode";
import { type AdjustableTableRowOptions } from "./Tables";
import { type CoreFetcherArgs } from "./Search";
import { type Tag } from "./Tag";
import { type Alert } from "../contexts/Alert";

export type State = "create" | "edit" | "preview";
export type Action = "LIMITED_READ" | "READ" | "UPDATE" | "CHANGE_OWNER";
export type SharingMode = "OWNER_GROUPS" | "WHITELIST" | "OWNER_ONLY";

export type LockStatus =
  | "LOCKED_OK"
  | "WAS_ALREADY_LOCKED"
  | "UNLOCKED_OK"
  | "CANNOT_LOCK";

/*
 * Where it is inconvenient to identify the class of an inventory record by any
 * other means, these strings provide a simple enum for switching over
 * instances of their respective classes.
 */
export type RecordType =
  | "sample"
  | "container"
  | "subSample"
  | "sampleTemplate";

/*
 * The API encodes the type of the various Inventory records using an all-caps
 * snake-case encoding.
 */
export type ApiRecordType =
  | "SAMPLE"
  | "CONTAINER"
  | "SUBSAMPLE"
  | "SAMPLE_TEMPLATE";

export function recordTypeToApiRecordType(rt: RecordType): ApiRecordType {
  if (rt === "sample") return "SAMPLE";
  if (rt === "container") return "CONTAINER";
  if (rt === "subSample") return "SUBSAMPLE";
  return "SAMPLE_TEMPLATE";
}

/*
 * This is the base definition of all Inventory records. In other words, this
 * is the minimum definition of what it means to be a record in the Inventory
 * system, that the rest of the frontend code can depend on, particularly where
 * depending on the runtime implementations would lead to the modules becoming
 * too tightly coupled together and may introduce cyclical dependencies.
 *
 * The API provides two views on the various Inventory records: a summary view
 * returned by the listing and search APIs, and a full view returned by the GET
 * endpoint for the specific record. It is possible to fully populate the
 * properties of this interface with the summary view. Some of the properties
 * and methods, as documented, have behaviours that are dependent on these two
 * different views.
 */
export interface InventoryRecord extends Record, Editable, HasChildren {
  /*
   * These properties are simply shared by all record classes.
   */
  description: ?string;
  tags: Array<Tag>;
  permittedActions: Set<Action>;
  type: ApiRecordType;
  attachments: Array<Attachment>;
  identifiers: Array<Identifier>;
  barcodes: Array<BarcodeRecord>;
  created: string;
  lastModified: string;
  modifiedByFullName: string;
  +illustration: Node;

  /*
   * Records may have an associated image. It MUST be possible to set the
   * various image types with setAttributesDirty
   */
  image: ?BlobUrl;
  fetchImage("image" | "locationsImage" | "thumbnail"): Promise<?BlobUrl>;

  /*
   * These relate to loading and fetching of the data associated with the Record.
   *
   * Loading MUST be true during ongoing network activity.
   *
   * When the instance of any implementation is populated from the summary view
   * infoLoaded MUST be false. When it has been populated from the full view
   * infoLoaded MUST be true.
   *
   * noFullDetails MUST expose the opposite information of infoLoaded, except
   * that it MUST be false when loading is true.
   */
  loading: boolean;
  infoLoaded: boolean;
  +noFullDetails: boolean;

  /*
   * These computed properties are related to permissions and are used to determine
   * which actions can be performed on a record, or which details can be displayed
   */
  +canRead: boolean;
  +canEdit: boolean;
  +canTransfer: boolean;
  +readAccessLevel: ReadAccessLevel;

  /*
   * When new data is available, typically by making a GET request, the
   * instance of InventoryRecord can be repopulated with this method.
   */
  populateFromJson(Factory, any, ?any): void;

  /*
   * After some set of other Records have been modified, it may be desirable to
   * refretch the data associated with this Record. This method SHOULD
   * determine whether the InventoryRecord should be updated from the set of
   * Global IDs and if so then it SHOULD trigger network activity.
   */
  updateBecauseRecordsChanged(Set<GlobalId>): void;

  /*
   * For creating and editing InventoryRecord and their associated fields. For
   * more information see ./Editable.js
   */
  +state: State;
  create(): Promise<void>;
  setEditing(boolean, ?boolean, ?boolean): Promise<LockStatus>;
  editing: boolean;
  isFieldEditable(string): boolean;
  setFieldsStateForBatchEditing(): void;
  +supportsBatchEditing: boolean;
  currentlyEditableFields: Set<string>;

  /**
   * For identifiers (intended as Digital Object Identifier or DOI)
   * e.g. IGSN Identifiers
   * see: https://www.doi.org and https://www.igsn.org
   */
  addIdentifier(): Promise<void>;
  removeIdentifier(Id): Promise<void>;
  updateIdentifiers(): void;

  /*
   * When the user has created a new record, they should be navigated to a
   * listing of records that includes their newly created record. For items
   * that physically exist this will most often be their bench, but for purely
   * virtual records this will be some other search listing.
   */
  +showNewlyCreatedRecordSearchParams: CoreFetcherArgs;

  /*
   * The value of the enum, as defined above, for the current instance of the
   * implementation of this interface. Using this value to switch on behaviour
   * should be avoided, using polymorphism instead, but it is necessary in some
   * places as a pragmatic solution to simple logic.
   */
  +recordType: RecordType;

  /*
   * These labels are used for rendering purposes. Best to consult the
   * components where they are used for more details.
   */
  +ownerLabel: ?string;
  +recordLinkLabel: string;
  +showRecordOnNavigate: boolean;

  /*
   * When the instance of the implementation of this interface has been
   * populated with the summary view of the record, this method MUST fetch and
   * repopulate the record with the full view of the record. When it has been
   * populate with the full view, it MUST fetch the latest version of that
   * data.
   */
  fetchAdditionalInfo(): Promise<void>;

  /*
   * The marshalled version of the data modelled by the implementation of this
   * interface, including the properties listed above. This computed property
   * MUST always be JSON serialisable, and it is advisable to write a unit test
   * to assert as such for each implementation.
   */
  +paramsForBackend: any;

  /*
   * Many of the classes of Inventory records are analogous to physical objects
   * and as such have properties like quantity (covering volume and mass),
   * location, etc. The properties listed here MUST be implemented by all
   * classes of Inventory records, regardless of physicality, returning false
   * where they aren't applicable, due to implementation details of various
   * components. It would be ideal if this were not necessary.
   */
  hasParentContainers(): boolean;
  isInWorkbench(): boolean; // any parent is a bench
  isOnWorkbench(): boolean; // immediate parent is a bench
  isMovable(): boolean;
  +hasSubSamples: boolean;

  /*
   * At times, it is desirable to edit multiple attributes of an
   * InventoryRecord at once whilst also setting a dirty flag to signify to
   * various UI elements that the user has made changes.
   */
  setAttributesDirty({}): void;

  /*
   * The UI SHOULD display clear error and success messages when significant
   * actions are performed on InventoryRecords. Those toast messages SHOULD
   * then be scoped to the particular Record to which they relate so that they
   * may be operated on as a group e.g. when navigating away from a particular
   * record all of the scoped toasts can be cleared.
   */
  addScopedToast(Alert): void;
  clearAllScopedToasts(): void;

  /*
   * InventoryRecords can be selected for operating over many at once e.g. by
   * using checkboxes.
   */
  selected: boolean;
  toggleSelected(?boolean): void;

  /*
   * this method is for exposing a collection of properties that may be
   * displayable using an adjustable table. For more information see
   * ./Tables.js
   */
  adjustableTableOptions(): AdjustableTableRowOptions<string>;

  /*
   * InventoryRecords can have a collection of associated ExtraFields. For more
   * information see ./ExtraFields.js
   */
  extraFields: Array<ExtraField>;
  addExtraField(ExtraFieldAttrs): void;
  updateExtraField(string, { name: string, type: string }): void;
  removeExtraField(?number, number): void;
  +visibleExtraFields: Array<ExtraField>;
  +hasUnsavedExtraField: boolean;
  +fieldNamesInUse: Array<string>;

  /*
   * An InventoryRecord can specify that any associated context menu MUST be
   * disabled.
   */
  contextMenuDisabled(): ?string;

  /*
   * An InventoryRecord MAY have an associated Search which can be refreshed
   * after significant user interactions that may render the search results
   * being displaying inaccurate.
   */
  refreshAssociatedSearch(): void;

  /*
   * When showing breadcrumbs of the location of this InventoryRecord this
   * computed boolean determines whether a "top link" to the root of the
   * container tree is shown. This is so that some records, like benches, can
   * be shown as if they are independent of the container tree.
   */
  showTopLinkInBreadcrumbs(): boolean;

  +usableInLoM: boolean;
  +beingCreatedInContainer: boolean;
}

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

export const globalIdToInventoryRecordTypeLabel: (GlobalId) => $Values<
  typeof inventoryRecordTypeLabels
> = match([
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
