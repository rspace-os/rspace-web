/*
 * The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
 * "SHOULD", "SHOULD NOT", "RECOMMENDED",  "MAY", and "OPTIONAL" in this
 * document are to be interpreted as described in RFC 2119.
 */

import { type Id, type GlobalId } from "./BaseRecord";
import { type Record, type ReadAccessLevel } from "./Record";
import { type BlobUrl } from "../../util/types";
import { type Editable } from "./Editable";
import { type HasChildren } from "./HasChildren";
import { type Factory } from "./Factory";
import { type Attachment } from "./Attachment";
import { type Identifier } from "./Identifier";
import React from "react";
import { type ExtraField, type ExtraFieldAttrs } from "./ExtraField";
import { type BarcodeRecord } from "./Barcode";
import { type AdjustableTableRowOptions } from "./Tables";
import { type CoreFetcherArgs } from "./Search";
import { type Tag } from "./Tag";
import { type Alert } from "../contexts/Alert";
import { type Container } from "./Container";

export type State = "create" | "edit" | "preview";
export type Action = "LIMITED_READ" | "READ" | "UPDATE" | "CHANGE_OWNER";
export type SharingMode = "OWNER_GROUPS" | "WHITELIST" | "OWNER_ONLY";

export type LockStatus =
  | "LOCKED_OK"
  | "WAS_ALREADY_LOCKED"
  | "UNLOCKED_OK"
  | "CANNOT_LOCK";

/**
 * Where it is inconvenient to identify the class of an inventory record by any
 * other means, these strings provide a simple enum for switching over
 * instances of their respective classes.
 */
export type RecordType =
  | "sample"
  | "container"
  | "subSample"
  | "sampleTemplate";

/**
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

export type CreateOptionParameter = {
  label: string;

  /**
   * A brief description of the required information. MUST describe any
   * conditions on the data that would prevent the user from proceeding to the
   * next step; see validState below.
   */
  explanation: string;

  /**
   * It is this object that determines the form field that will be presented to
   * the user in the create dialog. It MUST be an observable object so that
   * when the form field mutates the value, the component is re-rendered.
   */
  state:
    | { key: "split"; copies: number }
    | { key: "name"; value: string }
    | { key: "location"; container: Container }
    | {
        key: "fields";
        copyFieldContent: ReadonlyArray<{
          id: Id;
          name: string;
          content: string;
          hasContent: boolean;
          selected: boolean;
        }>;
      }
    | { key: "newSubsamplesCount"; count: number }
    | {
        key: "newSubsamplesQuantity";
        quantity: number | "";
        quantityLabel: string;
      };

  /**
   * The user is prevented from moving to the next step if the current step is
   * in an invalid state. The `explanation` text MUST describe the required
   * data in sufficient detail so as to make it obvious what the problem is
   * when this function returns false.
   */
  validState: () => boolean;
};

/**
 * The create dialog (../../Inventory/components/ContextMenu/CreateDialog.js)
 * presents the user with a series of contextual options for creating new
 * records with respect to the current record e.g. splitting a subsample.
 * This type defines a single such option, instructing the create dialog what
 * information to collect from the user and how to proceed when the dialog is
 * submitted.
 */
export type CreateOption = {
  /**
   * A brief description of what new record(s) are to be created. Disambiguiate
   * with a few words if there are multiple ways of creating the same record
   * type.
   */
  label: string;

  /**
   * Avoid disabling options where possible. However, where necessary this is
   * available. When true, `explanation` MUST explain why the user is unable to
   * use this option.
   */
  disabled?: boolean;

  /**
   * When enabled, this provides a bit more information about how the new
   * records will be created. If the option is disabled, this MUST explain why.
   */
  explanation: string;

  /**
   * Each option in the create dialog can have a series of steps that require
   * that the user provide some parameters to the creation of the new
   * record(s). This might be the new record's name, the number of new records
   * to be created, or anything else that the user must specify.
   */
  parameters?: ReadonlyArray<CreateOptionParameter>;

  /**
   * When called, the paramters MUST be put back into their initial values.
   * This is called when the create dialog is closed so that when the user
   * opens the dialog again the state is returned to an initial state.
   */
  onReset: () => void;

  /**
   * When the create dialog is submitted, this function will be invoked. It
   * COULD make a network to create the new record. It COULD redirect the user
   * to another part of the UI to collect more information. Whatever it does it
   * MUST move the user a step toward creating the new record(s) and it MUST be
   * clear how much work remains before the new record will be finalised.
   */
  onSubmit: () => Promise<void>;
};

export interface CreateFrom {
  readonly createOptions: ReadonlyArray<CreateOption>;
}

/**
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
export interface InventoryRecord
  extends Record,
    Editable,
    HasChildren,
    CreateFrom {
  /*
   * These properties are simply shared by all record classes.
   */
  description: string | null;
  tags: Array<Tag>;
  permittedActions: Set<Action>;
  type: ApiRecordType;
  attachments: Array<Attachment>;
  identifiers: Array<Identifier>;
  barcodes: Array<BarcodeRecord>;
  created: string;
  lastModified: string;
  modifiedByFullName: string;
  readonly illustration: React.ReactNode;

  /*
   * Records may have an associated image. It MUST be possible to set the
   * various image types with setAttributesDirty
   */
  image: BlobUrl | null;
  fetchImage(
    imageType: "image" | "locationsImage" | "thumbnail"
  ): Promise<BlobUrl | null>;

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
  readonly noFullDetails: boolean;

  /*
   * These computed properties are related to permissions and are used to determine
   * which actions can be performed on a record, or which details can be displayed
   */
  readonly canRead: boolean;
  readonly canEdit: boolean;
  readonly canTransfer: boolean;
  readonly readAccessLevel: ReadAccessLevel;

  /*
   * When new data is available, typically by making a GET request, the
   * instance of InventoryRecord can be repopulated with this method.
   */
  populateFromJson(
    factory: Factory,
    params: any,
    defaultParams: any | null
  ): void;

  /*
   * After some set of other Records have been modified, it may be desirable to
   * refretch the data associated with this Record. This method SHOULD
   * determine whether the InventoryRecord should be updated from the set of
   * Global IDs and if so then it SHOULD trigger network activity.
   */
  updateBecauseRecordsChanged(recordIds: Set<GlobalId>): void;

  /*
   * For creating and editing InventoryRecord and their associated fields. For
   * more information see ./Editable.js
   */
  readonly state: State;
  create(): Promise<void>;
  setEditing(
    value: boolean,
    refresh: boolean | null,
    silent: boolean | null
  ): Promise<LockStatus>;
  editing: boolean;
  isFieldEditable(field: string): boolean;
  setFieldsStateForBatchEditing(): void;
  readonly supportsBatchEditing: boolean;
  currentlyEditableFields: Set<string>;

  /**
   * For identifiers (intended as Digital Object Identifier or DOI)
   * e.g. IGSN Identifiers
   * see: https://www.doi.org and https://www.igsn.org
   */
  addIdentifier(): Promise<void>;
  removeIdentifier(id: Id): Promise<void>;
  updateIdentifiers(): void;

  /*
   * When the user has created a new record, they should be navigated to a
   * listing of records that includes their newly created record. For items
   * that physically exist this will most often be their bench, but for purely
   * virtual records this will be some other search listing.
   */
  readonly showNewlyCreatedRecordSearchParams: CoreFetcherArgs;

  /*
   * The value of the enum, as defined above, for the current instance of the
   * implementation of this interface. Using this value to switch on behaviour
   * should be avoided, using polymorphism instead, but it is necessary in some
   * places as a pragmatic solution to simple logic.
   */
  readonly recordType: RecordType;

  /*
   * These labels are used for rendering purposes. Best to consult the
   * components where they are used for more details.
   */
  readonly ownerLabel: string | null;
  readonly recordLinkLabel: string;
  readonly showRecordOnNavigate: boolean;

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
  readonly paramsForBackend: any;

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
  readonly hasSubSamples: boolean;

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
  addScopedToast(alert: Alert): void;
  clearAllScopedToasts(): void;

  /*
   * InventoryRecords can be selected for operating over many at once e.g. by
   * using checkboxes.
   */
  selected: boolean;
  toggleSelected(value: boolean | null): void;

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
  addExtraField(newExtraFieldAttrs: ExtraFieldAttrs): void;
  updateExtraField(
    oldFieldName: string,
    updatedField: { name: string; type: string }
  ): void;
  removeExtraField(id: number | null, index: number): void;
  readonly visibleExtraFields: Array<ExtraField>;
  readonly hasUnsavedExtraField: boolean;
  readonly fieldNamesInUse: Array<string>;

  /*
   * An InventoryRecord can specify that any associated context menu MUST be
   * disabled.
   */
  contextMenuDisabled(): string | null;

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

  readonly usableInLoM: boolean;
  readonly beingCreatedInContainer: boolean;
}
