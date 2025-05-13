import { type URL as URLType, type BlobUrl, _LINK } from "../../util/types";
import ApiService from "../../common/InvApiService";
import { sameKeysAndValues, match, isoToLocale } from "../../util/Util";
import * as ArrayUtils from "../../util/ArrayUtils";
import { capImageAt1MB } from "../../util/images";
import {
  globalIdPatterns,
  type GlobalId,
  type Id,
} from "../definitions/BaseRecord";
import {
  type RecordDetails,
  type Thumbnail,
  type ReadAccessLevel,
} from "../definitions/Record";
import { type Location } from "../definitions/Container";
import {
  type Action,
  type RecordType,
  type InventoryRecord,
  type LockStatus,
  type State,
  type ApiRecordType,
  type SharingMode,
  type CreateOption,
} from "../definitions/InventoryRecord";
import {
  type AdjustableTableRow,
  type AdjustableTableRowOptions,
} from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import { mkAlert, type Alert } from "../contexts/Alert";
import { AttachmentJson, newExistingAttachment } from "./AttachmentModel";
import ExtraFieldModel from "./ExtraFieldModel";
import {
  type ExtraFieldAttrs,
  type ExtraField,
} from "../definitions/ExtraField";
import { type CoreFetcherArgs } from "../definitions/Search";
import { PersonAttrs, type Person } from "../definitions/Person";
import {
  action,
  computed,
  observable,
  makeObservable,
  runInAction,
} from "mobx";
import React from "react";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";
import { type Factory } from "../definitions/Factory";
import { type Attachment } from "../definitions/Attachment";
import {
  PersistedBarcodeAttrs,
  type BarcodeRecord,
} from "../definitions/Barcode";
import { GeneratedBarcode, PersistedBarcode } from "./Barcode";
import { type SharedWithGroup } from "../definitions/Group";
import { type ContainerInContainerParams } from "./ContainerModel";
import { type SampleInContainerParams } from "./SampleModel";
import {
  calculateProgress,
  noProgress,
  type Progress,
} from "../../util/progress";
import { type SortProperty } from "../../Inventory/components/Tables/SortableProperty";
import {
  type Identifier,
  type IdentifierAttrs,
} from "../definitions/Identifier";
import IdentifierModel from "./IdentifierModel";
import { type Tag } from "../definitions/Tag";
import { Optional } from "../../util/optional";
import {
  encodeTagString,
  decodeTagString,
} from "../../components/Tags/ParseEncodedTagStrings";
import { pick } from "../../util/unsafeUtils";
import {
  IsValid,
  IsInvalid,
  allAreValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";
import { getErrorMessage } from "../../util/error";
import * as Parsers from "../../util/parsers";
import UtilResult from "../../util/result";
import { AxiosProgressEvent } from "@/common/axios";

export type ResultEditableFields = {
  /*
   * As far as the database is concerned, the description can be null, and the
   * records pre-populated in the databases of the development servers have a
   * `null` description. When the user creates a record in the UI, however, we
   * set the description to the empty string if they have not set a value. As
   * such, the description is a very rare edge case only occuring on
   * development servers or where the records have been created directly from
   * the API as thus has a higher probability of bugs.
   */
  description: string | null;
  name: string;
  tags: Array<Tag>;
  image: BlobUrl | null;
  newBase64Image: string | null;
  barcodes: Array<BarcodeRecord>;
  sharingMode: SharingMode;
  sharedWith: Array<SharedWithGroup> | null;
};

export type ResultUneditableFields = {
  owner: Person | null;
};

export const sortProperties: Array<SortProperty> = [
  { key: "name", label: "Name", adjustColumn: false },
  { key: "type", label: "Type", adjustColumn: false },
  // note: there is a non-breaking space (U+00A0) between "Global" and "ID"
  { key: "globalId", label: "Global ID", adjustColumn: true },
  { key: "creationDate", label: "Created", adjustColumn: true },
  {
    key: "modificationDate",
    label: "Last Modified",
    adjustColumn: true,
  },
];

export const isSortable = (propKey: string): boolean =>
  sortProperties.map((p) => p.key).includes(propKey);

const calculateUploadProgress = (soFar: number, total: number): number =>
  Math.floor((soFar / total) * 10) * 10;

type LockOwner = {
  firstName: string;
  lastName: string;
  username: string;
};

export class RecordLockedError extends Error {
  record: Result; //eslint-disable-line
  lockOwner: LockOwner;

  //eslint-disable-next-line
  constructor(record: Result, lockOwner: LockOwner) {
    super();
    this.name = "RecordLockedError";
    this.record = record;
    this.lockOwner = lockOwner;
  }
}

const FIELDS: Set<string> = new Set([
  "name",
  "description",
  "tags",
  "extraFields",
  "image",
  "identifiers",
  "attachments",
  "barcodes",
  "sharingMode",
  "sharedWith",
]);
export { FIELDS as RESULT_FIELDS };
const defaultVisibleFields: Set<string> = new Set([...FIELDS]);
export { defaultVisibleFields as defaultVisibleResultFields };
const defaultEditableFields: Set<string> = new Set();
export { defaultEditableFields as defaultEditableResultFields };

type ResultAttrs = {
  id: Id | null;
  globalId: string | null;
  type: ApiRecordType;
  name: string;
  description: string;
  extraFields?: Array<ExtraFieldAttrs>;
  tags:
    | Array<{
        value: string;
        uri: string;
        ontologyName: string;
        ontologyVersion: string;
      }>
    | null
    | "";
  lastModified: string;
  created: string;
  modifiedByFullName: string;
  owner: PersonAttrs | null;
  deleted: boolean;
  permittedActions: Array<Action>;
  attachments: Array<AttachmentJson>;
  iconId: number | null;
  sharingMode: SharingMode;
  sharedWith: Array<SharedWithGroup>;
  _links: Array<_LINK>;
  barcodes: Array<PersistedBarcodeAttrs>;
  identifiers: Array<IdentifierAttrs>;
};

/*
 * Typically the result of some search action, this class provides the
 * implementation for the Record interface at RUNTIME. It facilitates the
 * listing, viewing, editing, creating, and various other contextual actions of
 * all Inventory records.
 *
 * Unless explicitly testing this implemetation it is best to provide an
 * alternative implementation of the Record interface when writing unit tests.
 *
 * NOTE: This class is abstract and should not itself be instantiated. Derive
 * subclasses from it, implement the various unimplemented methods, and
 * instantiate those classes instead.
 */
export default class Result
  implements
    InventoryRecord,
    AdjustableTableRow<string>,
    HasEditableFields<ResultEditableFields>,
    HasUneditableFields<ResultUneditableFields>
{
  loading: boolean = false;
  editing: boolean = false;
  id: Id | null = null;
  globalId: GlobalId | null = null;
  type: ApiRecordType;
  name: ResultEditableFields["name"] = "";
  description: ResultEditableFields["description"] = "";
  selected: boolean = false; // whether its checkbox is selected
  infoLoaded: boolean = false; // whether the full information is fetched
  extraFields: Array<ExtraField> = [];
  currentlyVisibleFields: Set<string>;
  currentlyEditableFields: Set<string>;
  image: ResultEditableFields["image"] = null;
  thumbnail: Thumbnail = null;
  tags: ResultEditableFields["tags"] = [];
  created: string;
  lastModified: string;
  modifiedByFullName: string;
  owner: ?Person;
  deleted: boolean = false;
  _links: { [_: string]: URLType };
  uploadProgress: Progress;
  lastEditInput: Date;
  lockExpiry: Date;
  lockExpired: boolean = false;
  expiryCheckInterval: NodeJS.Timeout | undefined;
  permittedActions: Set<Action>;
  newBase64Image: ResultEditableFields["newBase64Image"] = null;
  attachments: Array<Attachment> = [];
  identifiers: Array<Identifier> = [];
  iconId: number | null = null;
  barcodes: Array<BarcodeRecord> = [];
  factory: Factory;
  fetchingAdditionalInfo: Promise<{ data: object }> | null = null;
  sharingMode: SharingMode;
  sharedWith: Array<SharedWithGroup> | null;

  /*
   * This list of toasts is used to clear alert toasts specific to this record
   * when the user navigates away from this record. For example, when editing
   * there may be some info toasts that should be hidden when editing is
   * completed or cancelled.
   *
   * Note that this array may contain toasts that have previously been shown
   * but are no longer visible, such as those that have automatically been
   * dismissed after a timeout or those that have been closed by logic that
   * prevents the same toast from being shown multiple times. As such, this
   * array should only ever be appended to or cleared entirely.
   */
  scopedToasts: Array<Alert> = [];

  constructor(factory: Factory) {
    makeObservable(this, {
      currentlyVisibleFields: observable,
      currentlyEditableFields: observable,
      loading: observable,
      editing: observable,
      id: observable,
      globalId: observable,
      type: observable,
      name: observable,
      description: observable,
      selected: observable,
      infoLoaded: observable,
      barcodes: observable,
      extraFields: observable,
      image: observable,
      thumbnail: observable,
      tags: observable,
      scopedToasts: observable,
      lastModified: observable,
      created: observable,
      modifiedByFullName: observable,
      owner: observable,
      deleted: observable,
      _links: observable,
      uploadProgress: observable,
      lastEditInput: observable,
      lockExpiry: observable,
      lockExpired: observable,
      permittedActions: observable,
      attachments: observable,
      identifiers: observable,
      iconId: observable,
      sharingMode: observable,
      sharedWith: observable,
      setAttributesDirty: action,
      unsetDirtyFlag: action,
      setAttributes: action,
      handleLockExpiry: action,
      setEditing: action,
      updateFieldsState: action,
      toggleSelected: action,
      fetchAdditionalInfo: action,
      create: action,
      update: action,
      setVisible: action,
      setEditable: action,
      setEditableExtraFields: action,
      addExtraField: action,
      removeExtraField: action,
      updateExtraField: action,
      addIdentifier: action,
      removeIdentifier: action,
      updateIdentifiers: action,
      populateFromJson: action,
      setLoading: action,
      setFieldsDirty: action,
      visibleExtraFields: computed,
      hasUnsavedExtraField: computed,
      recordType: computed,
      state: computed,
      paramsForBackend: computed,
      submittable: computed,
      cardTypeLabel: computed,
      recordTypeLabel: computed,
      permalinkURL: computed,
      ownerLabel: computed,
      currentUserIsOwner: computed,
      isNewItem: computed,
      isWorkbench: computed,
      isTemplate: computed,
      fieldNamesInUse: computed,
      recordLinkLabel: computed,
      noFullDetails: computed,
      hasSubSamples: computed,
      iconName: computed,
      showNewlyCreatedRecordSearchParams: computed,
      children: computed,
      recordDetails: computed,
      fieldValues: computed,
      supportsBatchEditing: computed,
      canChooseWhichToEdit: computed,
      usableInLoM: computed,
      beingCreatedInContainer: computed,
      dataAttachedToRecordCreatedAnaylticsEvent: computed,
      canRead: computed,
      canEdit: computed,
      canTransfer: computed,
      readAccessLevel: computed,
      showRecordOnNavigate: computed,
      inContainerParams: computed,
    });
    this.factory = factory;
  }

  // all fields are editable, and all values will be saved to server
  get canChooseWhichToEdit(): boolean {
    return false;
  }

  populateFromJson(
    factory: Factory,
    passedParams: object,
    defaultParams: object = {}
  ): void {
    const params = { ...defaultParams, ...passedParams } as ResultAttrs;
    this.id = params.id;
    this.globalId = params.globalId;
    this.type = params.type;
    this.name = params.name;
    this.description = params.description;
    this.extraFields = (params.extraFields ?? []).map(
      (efParams) => new ExtraFieldModel(efParams, this)
    );
    this.tags =
      params.tags === null || params.tags === ""
        ? []
        : params.tags.map((tag) => ({
            value: decodeTagString(tag.value),
            uri:
              tag.uri === ""
                ? Optional.empty()
                : Optional.present(decodeTagString(tag.uri)),
            vocabulary:
              tag.ontologyName === ""
                ? Optional.empty()
                : Optional.present(decodeTagString(tag.ontologyName)),
            version:
              tag.ontologyVersion === ""
                ? Optional.empty()
                : Optional.present(decodeTagString(tag.ontologyVersion)),
          }));
    this.lastModified = params.lastModified;
    this.created = params.created;
    this.modifiedByFullName = params.modifiedByFullName;
    this.owner = params.owner ? factory.newPerson(params.owner) : null;
    this.deleted = params.deleted;
    this.permittedActions = new Set(params.permittedActions);
    this.attachments = (params.attachments ?? []).map((a) =>
      newExistingAttachment(a, this.permalinkURL, () =>
        this.setAttributesDirty({})
      )
    );
    this.iconId = params.iconId;
    this.sharingMode = params.sharingMode;
    if ("sharedWith" in params) {
      this.sharedWith = params.sharedWith;
    } else {
      this.sharedWith = null;
    }
    this.processLinks(params._links);

    if (
      this.id !== null &&
      this.permalinkURL &&
      this.readAccessLevel !== "public"
    ) {
      this.barcodes = [
        new GeneratedBarcode({
          data: `${window.location.origin}${this.permalinkURL}`,
        }),
        ...params.barcodes.map((attrs) => factory.newBarcode(attrs)),
      ];

      this.identifiers = params.identifiers.map((idAttrs) => {
        if (!this.globalId) throw new Error("Global Id must be known.");
        return factory.newIdentifier(idAttrs, this.globalId, ApiService);
      });
      void this.fetchImage("thumbnail");
    } else {
      this.barcodes = [];
    }

    this.updateFieldsState();
  }

  /*
   * Each search result returns, in addition to its properties, a _link value
   * which implements the REST principle of "Hypermedia as the Engine of
   * Application State". The codebase and API do not adhere to this strictly
   * (there are many URLs that are hard-coded in the JS codebase) but some
   * endpoints, such as the locations of image, are described in this way.
   *
   * Mobx incurs a high memory footprint when storing deeply nested objects and
   * so it is more efficient to store them as a single level object rather than
   * as a nested object. This difference of around 2KB may seem small but it
   * compounds when there are many thousands of search results in memory.
   */
  processLinks(_links: Array<_LINK>): void {
    this._links = Object.fromEntries(
      _links.map(({ link, rel }) => [rel, link])
    );
  }

  setLoading(value: boolean): void {
    this.loading = value;
  }

  setFieldsDirty(newFieldValues: {}) {
    this.setAttributesDirty(newFieldValues);
  }

  setDirtyFlag() {
    getRootStore().uiStore.setPageNavigationConfirmation(true);
    getRootStore().uiStore.setDirty(async () => {
      if (this.id) await this.setEditing(false);
      else this.unsetDirtyFlag();
    });
  }

  setAttributesDirty(params: {}) {
    if (["create", "edit"].includes(this.state)) {
      if (this.state === "edit") {
        /* debouncing */
        const pause = 3000;
        if (Date.now() > this.lastEditInput.getTime() + pause) {
          this.autoExtendLock();
          this.lastEditInput = new Date();
        }
      }
      this.setAttributes({ ...params });
      this.setDirtyFlag();
    } else {
      this.setAttributes(params);
    }
  }

  unsetDirtyFlag() {
    getRootStore().uiStore.unsetDirty();
  }

  setAttributes(params: {}) {
    Object.assign(this, params);
  }

  get visibleExtraFields(): Array<ExtraField> {
    return this.extraFields.filter((ef) => !ef.deleteFieldRequest);
  }

  get hasUnsavedExtraField(): boolean {
    return this.extraFields.some((ef) => ef.initial);
  }

  get recordType(): RecordType {
    throw new Error("Abstract method; not implemented.");
  }

  get state(): State {
    return match<{ editing: boolean; id: Id | null }, State>([
      [({ editing }) => editing, "edit"],
      [({ id }) => Boolean(id), "preview"],
      [() => true, "create"],
    ])({ editing: this.editing, id: this.id });
  }

  /*
   * A plain object that can be encoded to JSON for submission to the backend
   * when API calls are made. It is vital that there are no cyclical memory
   * references in the object returned by this computed properties. Each
   * subclass should have a unit test that asserts that this object can be
   * serialised and any changes here should be reflect in each of those.
   */
  get paramsForBackend(): Record<string, unknown> {
    const extraFields = this.extraFields.map((ef) => ({
      name: ef.name,
      content: ef.content,
      id: ef.id,
      type: ef.type === "Text" ? "text" : "number",
      newFieldRequest: ef.newFieldRequest,
      deleteFieldRequest: ef.deleteFieldRequest,
    }));

    const params: {
      id: Id;
      globalId: GlobalId | null;
      name?: string;
      description?: string | null;
      extraFields?: typeof extraFields;
      tags?: Array<{
        value: string;
        uri: string | null;
        ontologyName: string | null;
        ontologyVersion: string | null;
      }>;
      newBase64Image?: string | null;
      barcodes?: Array<object>;
      identifiers?: unknown;
      sharingMode?: SharingMode;
      sharedWith?: Array<SharedWithGroup> | null;
    } = {
      id: this.id,
      globalId: this.globalId,
    };
    if (this.currentlyEditableFields.has("name")) params.name = this.name;
    if (this.currentlyEditableFields.has("description"))
      params.description = this.description;
    if (this.currentlyEditableFields.has("extraFields"))
      params.extraFields = extraFields;
    if (this.currentlyEditableFields.has("tags")) {
      params.tags = this.tags.map((tag) => ({
        value: encodeTagString(tag.value),
        uri: tag.uri.map(encodeTagString).orElse(null),
        ontologyName: tag.vocabulary.map(encodeTagString).orElse(null),
        ontologyVersion: tag.version.map(encodeTagString).orElse(null),
      }));
    }

    if (this.currentlyEditableFields.has("image"))
      params.newBase64Image = this.newBase64Image;
    if (this.currentlyEditableFields.has("barcodes"))
      params.barcodes = ArrayUtils.filterClass(
        PersistedBarcode,
        this.barcodes
      ).map((b) => b.paramsForBackend);
    if (this.currentlyEditableFields.has("identifiers"))
      params.identifiers = this.identifiers.map((i) => i.toJson());
    if (this.currentlyEditableFields.has("sharingMode"))
      params.sharingMode = this.sharingMode;
    if (this.currentlyEditableFields.has("sharedWith"))
      params.sharedWith = this.sharedWith;
    return params;
  }

  validate(): ValidationResult {
    const validateName = () => {
      if (!this.isFieldEditable("name")) return IsValid();
      if (this.name.length < 1) return IsInvalid("Name cannot be empty.");
      if (this.name.length < 2)
        return IsInvalid("Name cannot be a single character.");
      if (this.name.length > 255)
        return IsInvalid("Name cannot be more than 255 characters.");
      if (this.name.trim().length < 1)
        return IsInvalid("Name cannot be just whitespace.");
      return IsValid();
    };

    const validateDescription = () => {
      if (!this.isFieldEditable("description")) return IsValid();
      if (typeof this.description === "undefined" || this.description === null)
        return IsValid();
      if (this.description.length <= 250) return IsValid();
      return IsInvalid("Description cannot be longer than 250 characters.");
    };

    const validateTags = () => {
      if (!this.isFieldEditable("tags")) return IsValid();
      if (typeof this.tags === "undefined" || this.tags === null)
        return IsValid();
      if (this.tags.join(",").length > 255) return IsInvalid("Too many tags.");
      return IsValid();
    };

    const validateExtraFields = () => {
      if (!ArrayUtils.allAreUnique(this.fieldNamesInUse))
        return IsInvalid("All field names must be distinct.");
      return allAreValid(this.extraFields.map((e) => e.isValid));
    };

    return allAreValid([
      validateName(),
      validateDescription(),
      validateTags(),
      validateExtraFields(),
    ]);
  }

  get submittable(): ValidationResult {
    return this.validate().flatMap(() => {
      if (this.lockExpired)
        return IsInvalid("Edit lock has expired. Please refresh.");
      return IsValid();
    });
  }

  get canRead(): boolean {
    return this.permittedActions.has("READ");
  }

  get canEdit(): boolean {
    return this.permittedActions.has("UPDATE");
  }

  get canTransfer(): boolean {
    return this.permittedActions.has("CHANGE_OWNER");
  }

  get readAccessLevel(): ReadAccessLevel {
    return this.permittedActions.has("READ")
      ? "full"
      : this.permittedActions.has("LIMITED_READ")
      ? "limited"
      : "public";
  }

  async expiryCheck() {
    const warningTime = 60000;
    const runningOut = Date.now() >= this.lockExpiry.getTime() - warningTime;
    const editLockExpired = Date.now() >= this.lockExpiry.getTime();
    // prevent error: don't open a second dialog if one is already open
    const dialogOpen = getRootStore().uiStore.confirmationDialogProps !== null;
    if (runningOut && !dialogOpen) {
      if (
        getRootStore().uiStore.recentBatchEditExpiryCheck ??
        (await getRootStore().uiStore.confirm(
          "Your editing session is about to expire",
          <>
            This session will expire in one minute. Please confirm if you want
            to continue editing this {this.recordTypeLabel.toLowerCase()}. If
            you cancel, your unsaved changes will be lost.
          </>,
          "CONTINUE"
        ))
      ) {
        /*
         * by setting this flag, we only display the above dialog once
         * every 4 minutes, refreshing every lock for that period
         */
        const { uiStore } = getRootStore();
        const recentBatchEditExpiryCheck = uiStore.recentBatchEditExpiryCheck;
        uiStore.recentBatchEditExpiryCheck = true;
        if (recentBatchEditExpiryCheck === null) {
          setTimeout(() => {
            uiStore.recentBatchEditExpiryCheck = null;
          }, 4 * 60 * 1000);
        }

        runInAction(() => {
          this.lastEditInput = new Date();
        });
        clearInterval(this.expiryCheckInterval);
        await this.setEditing(true);
      } else if (getRootStore().searchStore.search.batchEditingRecords) {
        await getRootStore().searchStore.search.batchEditableInstance.cancel();
      } else {
        await this.setEditing(false);
      }
    }

    if (editLockExpired) {
      // updating observed boolean to disable Save button
      runInAction(() => {
        this.lockExpired = true;
      });
      clearInterval(this.expiryCheckInterval);
      if (dialogOpen) {
        getRootStore().uiStore.closeConfirmationDialog();
      }
      await getRootStore().uiStore.confirm(
        "Your editing session has expired",
        <>
          Another user may be editing this {this.recordTypeLabel.toLowerCase()}.
          Please copy any information you need and then press{" "}
          <strong>Cancel</strong>.
        </>,
        "OK",
        ""
      );
    }
  }

  handleLockExpiry(remainingSeconds: number) {
    if (remainingSeconds) {
      this.lockExpiry = new Date(
        this.lastEditInput.getTime() + remainingSeconds * 1000
      );
      void this.expiryCheck();
      this.expiryCheckInterval = setInterval(
        () => void this.expiryCheck(),
        5000
      );
    } else {
      clearInterval(this.expiryCheckInterval);
    }
  }

  autoExtendLock() {
    clearInterval(this.expiryCheckInterval);
    void this.checkLock().then(({ status, remainingTimeInSeconds }) => {
      if (status === "WAS_ALREADY_LOCKED") {
        this.handleLockExpiry(remainingTimeInSeconds);
      }
    });
  }

  async releaseLock(silent: boolean = false): Promise<boolean | null> {
    try {
      if (!this.globalId) throw new Error("globalId is required.");
      const response = await ApiService.delete<void>(
        `editLocks/${this.globalId}`,
        ""
      );
      return response.status === 200;
    } catch (error) {
      if (!silent) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: `Relinquishing control of ${
              this.globalId ?? "UNKNOWN"
            } failed`,
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          })
        );
      }
      console.error(
        `Error relinquishing control of ${this.globalId ?? "UNKNOWN"}`,
        error
      );
      throw new Error(
        `Error relinquishing control of ${this.globalId ?? "UNKNOWN"}`,
        { cause: error }
      );
    }
  }

  async checkLock(silent: boolean = false): Promise<{
    status: LockStatus;
    remainingTimeInSeconds: number;
    lockOwner: LockOwner;
  }> {
    if (!this.globalId) throw new Error("globalId is required.");
    const globalId = this.globalId;
    try {
      return (
        await ApiService.post<{
          status: LockStatus;
          remainingTimeInSeconds: number;
          lockOwner: LockOwner;
        }>(`editLocks/${globalId}`, {})
      ).data;
    } catch (error) {
      if (!silent) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: `Something went wrong while checking the lock for "${this.name}"`,
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          })
        );
      }

      console.error(`Error checking lock for ${globalId}`, error);

      throw new Error(`Error checking lock for ${globalId}`, {
        cause: error,
      });
    } finally {
      this.setLoading(false);
    }
  }

  async setEditing(
    value: boolean,
    refresh?: boolean | null,
    silent?: boolean | null
  ): Promise<LockStatus> {
    refresh = refresh ?? true;
    silent = silent ?? false;
    const doSetEditing = action<() => void>(() => {
      this.editing = value;
      this.updateFieldsState();
    });

    this.setLoading(true);

    try {
      if (value) {
        // editing (fresh or extended)
        const { status, lockOwner, remainingTimeInSeconds } =
          await this.checkLock(silent);
        if (status === "LOCKED_OK") {
          runInAction(() => {
            this.lastEditInput = new Date();
          });
          this.handleLockExpiry(remainingTimeInSeconds);
          await this.fetchAdditionalInfo(silent);
          doSetEditing();
          return "LOCKED_OK";
        }
        if (status === "WAS_ALREADY_LOCKED") {
          if (this.state !== "edit") {
            if (!silent) {
              getRootStore().uiStore.addAlert(
                mkAlert({
                  title: "Unsaved changes?",
                  message:
                    "It appears that you already started editing this " +
                    this.recordTypeLabel.toLowerCase() +
                    " " +
                    "in another browser tab or on another device. We advise " +
                    "you cancel or save those changes first otherwise " +
                    "editing here could result in an error.",
                  variant: "warning",
                  isInfinite: true,
                })
              );
            }
            runInAction(() => {
              this.lastEditInput = new Date();
            });
            this.handleLockExpiry(remainingTimeInSeconds);
            doSetEditing();
            return "WAS_ALREADY_LOCKED";
          }
          this.handleLockExpiry(remainingTimeInSeconds);

          return "WAS_ALREADY_LOCKED";
        }
        if (status === "CANNOT_LOCK") {
          throw new RecordLockedError(this, lockOwner);
        } else {
          throw new Error("Unknown lock status.");
        }
      } else {
        // canceling
        let lockReleased;
        if (this.state === "edit") {
          lockReleased = await this.releaseLock(silent);
        }

        if (this.state === "create" || lockReleased) {
          doSetEditing();
          // will clear interval
          this.handleLockExpiry(0);
          this.setAttributes({
            uploadProgress: 0,
          });
          this.clearAllScopedToasts();
          this.setLoading(false);
          if (refresh) await this.fetchAdditionalInfo(silent);
          this.unsetDirtyFlag();
        }
        return "UNLOCKED_OK";
      }
    } finally {
      this.setLoading(false);
    }
  }

  // to be implemented by the classes that extend this abstract one
  updateFieldsState(): void {
    throw new Error("Abstract method; not implemented.");
  }

  // to be implemented by the classes that extend this abstract one
  setFieldsStateForBatchEditing(): void {
    throw new Error("Abstract method; not implemented.");
  }

  // to be implemented by the classes that extend this abstract one
  get usableInLoM(): boolean {
    throw new Error("Abstract method; not implemented.");
  }

  // to be implemented by the classes that extend this abtract one (and can be created inside a container)
  get beingCreatedInContainer(): boolean {
    return false;
  }

  // to be implemented by the classes that extend this abtract one (and can be created inside a container)
  get inContainerParams():
    | ContainerInContainerParams
    | SampleInContainerParams
    | null {
    return null;
  }

  /*
   * Set the `selected` status of this record and synchronise the selection
   * with the searchStore's activeResult's `locations`, if the activeResult is
   * a Container and this record is amongst its location's contents. This is so
   * that a selection made in list view is maintained which switching to image
   * or grid view.
   */
  toggleSelected(value: boolean | null) {
    value = value ?? !this.selected;
    this.selected = value;

    // Sync selection of location and content
    const {
      searchStore: { activeResult },
    } = getRootStore();

    // `instanceof ContainerModel` would add a cyclical dependency
    if (!activeResult || !("locations" in activeResult)) return;
    const locations = activeResult.locations as ReadonlyArray<Location>;

    /*
     * Each search has a list of results, amongst which the same record may
     * occur independently. Whilst they may have the same id they will not
     * point to the same memory reference. It is important that we
     * distinguish between these separate instances so as not to sync the
     * selection across different searches. Note the use of `this` rather
     * than comparing IDs.
     */
    const parentLocation = locations.find(
      ({ content }) => (content as Result | null) === this
    );

    // this check is necessary to avoid stack overflow
    if (parentLocation?.selected !== value) {
      parentLocation?.toggleSelected(value);
    }
  }

  async fetchAdditionalInfo(
    silent: boolean = false,
    queryParameters: URLSearchParams = new URLSearchParams()
  ): Promise<void> {
    if (this.fetchingAdditionalInfo || !this.id) {
      await this.fetchingAdditionalInfo;
      return;
    }
    const id = this.id;

    this.setLoading(true);
    try {
      this.fetchingAdditionalInfo = ApiService.query<object>(
        `${this.recordType}s/${id}`,
        new URLSearchParams(queryParameters)
      );
      const { data } = await this.fetchingAdditionalInfo;
      this.fetchingAdditionalInfo = null;
      runInAction(() => {
        this.infoLoaded = true;
      });
      this.populateFromJson(this.factory.newFactory(), data);
      await this.fetchImage("image");
      await this.fetchImage("thumbnail");
    } catch (error) {
      if (!silent) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: `Could not load full details of ${
              this.globalId ?? "UNKNOWN"
            }.`,
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          })
        );
      }
      console.error(
        `Error fetching additional info for ${this.globalId ?? "UNKNOWN"}`,
        error
      );
      throw new Error(
        `Error fetching additional info for ${this.globalId ?? "UNKNOWN"}`,
        { cause: error }
      );
    } finally {
      this.setLoading(false);
    }
  }

  async create(): Promise<void> {
    this.setLoading(true);
    const { uiStore, trackingStore, searchStore, peopleStore } = getRootStore();

    try {
      const params = {
        ...this.paramsForBackend,
        ...(this.beingCreatedInContainer ? this.inContainerParams : {}),
      };
      const { data } = await ApiService.post<{
        globalId: GlobalId;
        attachments: Array<Attachment>;
      }>(`${this.recordType}s?`, params, {
        onUploadProgress: (progressEvent) =>
          this.setAttributes({
            uploadProgress: progressEvent.total
              ? calculateUploadProgress(
                  progressEvent.loaded,
                  progressEvent.total
                )
              : 0,
          }),
      });
      const newRecord = this.factory.newFactory().newRecord(data);

      // now save attachments (and field attachments if any); can't be done until global ID is assigned
      runInAction(() => {
        this.globalId = data.globalId;
      });
      try {
        await this.saveAttachments(newRecord);
      } catch (error) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: "Could not save changes to attachments.",
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          })
        );
      }
      // copy over attachments as data from first POST doesn't have them
      newRecord.attachments = data.attachments;
      await this.setEditing(false);
      await searchStore.search.setActiveResult(newRecord);
      uiStore.addAlert(
        mkAlert({
          message: `${this.name} was successfully created.`,
          variant: "success",
        })
      );
      trackingStore.trackEvent(
        "InventoryRecordCreated",
        this.dataAttachedToRecordCreatedAnaylticsEvent
      );
      if (peopleStore.currentUser) await peopleStore.currentUser.getBench();
    } catch (error) {
      this.setAttributes({
        uploadProgress: noProgress,
      });
      Parsers.objectPath(["response"], error).do((response) => {
        try {
          const statusCode = Parsers.objectPath(["status"], response)
            .flatMap(Parsers.isNumber)
            .elseThrow();
          if (statusCode !== 400) throw new Error("Not a 400 status code");
          const validationErrors = Parsers.objectPath(
            ["data", "data", "validationErrors"],
            response
          )
            .flatMap(Parsers.isArray)
            .elseThrow();
          const newAlert = mkAlert({
            message: "Please correct the invalid fields and try again.",
            variant: "error",
            details: UtilResult.any(
              ...validationErrors.map((e) =>
                Parsers.isObject(e)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) =>
                    UtilResult.lift2<
                      string,
                      string,
                      { title: string; help: string; variant: "error" }
                    >((title, help) => ({
                      title,
                      help,
                      variant: "error",
                    }))(
                      Parsers.getValueWithKey("field")(obj).flatMap(
                        Parsers.isString
                      ),
                      Parsers.getValueWithKey("message")(obj).flatMap(
                        Parsers.isString
                      )
                    )
                  )
              )
            )
              .mapError(
                () => new Error("Could not parse any validation errors")
              )
              .elseThrow(),
            retryFunction: () => this.update().then(() => {}),
          });
          getRootStore().uiStore.addAlert(newAlert);
          getRootStore().searchStore.activeResult?.addScopedToast(newAlert);
        } catch {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: `Something went wrong and the ${this.recordType} was not saved.`,
              message: getErrorMessage(error, "Unknown reason."),
              variant: "error",
            })
          );
          if (error instanceof Error) {
            console.error(error.message);
          }
        }
      });
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  /*
   * This provides a mechanism for the child classes to attach additional data
   * this analytics event
   */
  get dataAttachedToRecordCreatedAnaylticsEvent(): {} {
    return {
      type: this.recordType,
    };
  }

  async update(refresh: boolean = true): Promise<void> {
    this.setLoading(true);

    try {
      const params = { ...this.paramsForBackend };
      if (!this.id) throw new Error("id is required.");
      const { data } = await ApiService.update<unknown>(
        `${this.recordType}s`,
        this.id,
        params,
        {
          onUploadProgress: (progressEvent: AxiosProgressEvent) =>
            this.setAttributes({
              uploadProgress: progressEvent.total
                ? calculateUploadProgress(
                    progressEvent.loaded,
                    progressEvent.total
                  )
                : 0,
            }),
        }
      );
      try {
        await this.saveAttachments();
      } catch (error) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: "Could not save changes to attachments.",
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          })
        );
      }
      this.populateFromJson(this.factory.newFactory(), data as object);
      await this.setEditing(false, refresh);
      getRootStore().searchStore.search.replaceResult(this);
      getRootStore().uiStore.addAlert(
        mkAlert({
          message: `${this.name} updated successfully.`,
          variant: "success",
        })
      );
    } catch (error) {
      this.setAttributes({
        uploadProgress: noProgress,
      });
      Parsers.objectPath(["response"], error).do((response) => {
        try {
          const statusCode = Parsers.objectPath(["status"], response)
            .flatMap(Parsers.isNumber)
            .elseThrow();
          if (statusCode !== 400) throw new Error("Not a 400 status code");
          const validationErrors = Parsers.objectPath(
            ["data", "data", "validationErrors"],
            response
          )
            .flatMap(Parsers.isArray)
            .elseThrow();
          const newAlert = mkAlert({
            message: "Please correct the invalid fields and try again.",
            variant: "error",
            details: UtilResult.any(
              ...validationErrors.map((e) =>
                Parsers.isObject(e)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) =>
                    UtilResult.lift2<
                      string,
                      string,
                      { title: string; help: string; variant: "error" }
                    >((title, help) => ({
                      title,
                      help,
                      variant: "error",
                    }))(
                      Parsers.getValueWithKey("field")(obj).flatMap(
                        Parsers.isString
                      ),
                      Parsers.getValueWithKey("message")(obj).flatMap(
                        Parsers.isString
                      )
                    )
                  )
              )
            )
              .mapError(
                () => new Error("Could not parse any validation errors")
              )
              .elseThrow(),
            retryFunction: () => this.update().then(() => {}),
          });
          getRootStore().uiStore.addAlert(newAlert);
          getRootStore().searchStore.activeResult?.addScopedToast(newAlert);
        } catch {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: `Something went wrong and the ${this.recordType} was not saved.`,
              message: getErrorMessage(error, "Unknown reason."),
              variant: "error",
            })
          );
          if (error instanceof Error) {
            console.error(error.message);
          }
        }
      });
      /*
       * Rethrow error so that super.update calls in child classes can execute
       * type-specific update logic only if update was successful
       */
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  /*
   * After changes to a record have been saved, any associated attachments must
   * also be saved via the API.
   */
  async saveAttachments(_newRecord?: InventoryRecord): Promise<void> {
    await this.submitAttachmentChanges();
  }

  isFieldVisible(field: string): boolean {
    if (this.editing || this.id === null || !this.hasOwnProperty(field)) {
      return this.currentlyVisibleFields.has(field);
    }
    return (
      this.currentlyVisibleFields.has(field) &&
      // @ts-expect-error Shouldn't be indexing
      this[field] !== "" &&
      // @ts-expect-error Shouldn't be indexing
      this[field] !== null &&
      // @ts-expect-error Shouldn't be indexing
      typeof this[field] !== "undefined"
    );
  }

  isFieldEditable(field: string): boolean {
    return (
      !this.loading && !this.deleted && this.currentlyEditableFields.has(field)
    );
  }

  setVisible(fields: Set<string>, value: boolean) {
    [...fields].forEach((field) => {
      if (value) {
        this.currentlyVisibleFields.add(field);
      } else {
        this.currentlyVisibleFields.delete(field);
      }
    });
  }

  setEditable(fields: Set<string>, value: boolean) {
    [...fields].forEach((field) => {
      if (value) {
        this.currentlyEditableFields.add(field);
      } else {
        this.currentlyEditableFields.delete(field);
      }
    });
  }

  /*
   * The HasEditableFields interface requires a method for disabling and
   * enabling the various fields.
   */
  setFieldEditable(fieldName: string, value: boolean): void {
    this.setEditable(new Set([fieldName]), value);
  }

  setEditableExtraFields(extraFields: Array<ExtraField>, value: boolean) {
    // can make an extraField not editable and control its state individually
    extraFields.forEach((ef) => {
      ef.editable = value;
    });
  }

  addExtraField(extraFieldParams: ExtraFieldAttrs) {
    this.setAttributesDirty({
      extraFields: [
        ...this.extraFields,
        new ExtraFieldModel(
          {
            ...extraFieldParams,
            newFieldRequest: true,
            initial: true,
          },
          this
        ),
      ],
    });
  }

  removeExtraField(id: number | null, index: number) {
    if (!this.id || !id) {
      this.extraFields.splice(index, 1);
    } else {
      const extraField = this.extraFields.find((ef) => ef.id === id);
      extraField?.setAttributesDirty({
        deleteFieldRequest: true,
        newFieldRequest: false,
      });
    }
  }

  updateExtraField(
    oldFieldName: string,
    updatedField: { name: string; type: string }
  ) {
    const field = this.extraFields.find((ef) => ef.name === oldFieldName);

    if (field) {
      const attrs = {
        ...updatedField,
        editing: false,
        initial: false,
        content: field.type !== updatedField.type ? "" : field.content,
      };
      const justNameAndType = pick("name", "type");
      const areEqual = sameKeysAndValues(
        justNameAndType(field),
        justNameAndType(updatedField)
      );
      if (areEqual) {
        field.setAttributes(attrs);
      } else {
        field.setAttributesDirty(attrs);
      }
    }
  }

  async addIdentifier(): Promise<void> {
    this.setLoading(true);
    try {
      if (
        await getRootStore().uiStore.confirm(
          "You are about to create an Identifier",
          <>
            An IGSN ID in <strong>Draft</strong> state will be created. No
            metadata will be made public at this stage.
          </>,
          "OK",
          "CANCEL"
        )
      ) {
        const globalId = this.globalId;
        if (!globalId) throw new Error("Global Id is required.");
        const response = await ApiService.post<IdentifierAttrs>(
          `/identifiers`,
          {
            parentGlobalId: globalId,
          }
        );
        const newIGSN = new IdentifierModel(
          response.data,
          globalId,
          ApiService
        );
        this.identifiers = this.identifiers.concat(newIGSN);
        getRootStore().searchStore.search.replaceResult(this);
        getRootStore().uiStore.addAlert(
          mkAlert({
            message: `Identifier ${response.data.doi} created.`,
            variant: "success",
          })
        );
      }
    } catch (error) {
      // in case of errors like 404 the server provides a specific response message that we want to display
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: `The Identifier could not be created.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      throw new Error(
        `An error occurred while minting the identifier: ${error}`
      );
    } finally {
      this.setLoading(false);
    }
  }

  async removeIdentifier(id: Id): Promise<void> {
    this.setLoading(true);
    try {
      if (
        await getRootStore().uiStore.confirm(
          "You are about to delete this Identifier",
          <>
            The IGSN ID will be deleted, and this item will no longer have an
            IGSN ID associated with it. Do you want to proceed?
          </>,
          "OK",
          "CANCEL"
        )
      ) {
        if (!id) throw new Error("DOI Id must be known.");
        const response = await ApiService.delete<unknown>(
          `/identifiers/${id}`,
          ""
        );
        if (response.data) {
          const index = this.identifiers.findIndex(
            (identifier) => identifier.id === id
          );
          this.identifiers.splice(index, 1);
          getRootStore().uiStore.addAlert(
            mkAlert({
              message: `Identifier draft deleted.`,
              variant: "success",
            })
          );
        }
      }
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: `The Identifier draft could not be deleted.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      throw new Error(
        `An error occurred while deleting the identifier draft: ${error}`
      );
    } finally {
      this.setLoading(false);
    }
  }

  /* attributes are updated on identifer. this action to be used separate from publishing */
  updateIdentifiers() {
    this.setAttributesDirty({ identifiers: this.identifiers });
  }

  fetchImage(
    name: "image" | "locationsImage" | "thumbnail"
  ): Promise<BlobUrl | null> {
    const link = this._links[name];
    if (!link) return Promise.resolve(null);
    return getRootStore()
      .imageStore.fetchImage(link)
      .then(
        action((blobUrl) => {
          // @ts-expect-error locationsImage is container-specific
          this[name] = blobUrl;
          return null;
        })
      );
  }

  setImage(
    imageName: "image" | "locationsImage",
    canvasId: string
  ): ({ dataURL, file }: { dataURL: string; file: Blob }) => Promise<void> {
    return async ({ dataURL, file }: { dataURL: string; file: Blob }) => {
      const scaledImage = await capImageAt1MB(file, dataURL, canvasId);
      this.setAttributesDirty({
        ...(imageName === "image"
          ? {
              newBase64Image: scaledImage,
              image: scaledImage,
            }
          : {}),
        ...(imageName === "locationsImage"
          ? {
              newBase64LocationsImage: scaledImage,
              locationsImage: scaledImage,
            }
          : {}),
      });
    };
  }

  addScopedToast(toast: Alert) {
    this.setAttributes({
      scopedToasts: [...this.scopedToasts, toast],
    });
  }

  clearAllScopedToasts() {
    [...this.scopedToasts].map((t) => getRootStore().uiStore.removeAlert(t));
    this.scopedToasts = [];
  }

  get cardTypeLabel(): string {
    return "";
  }

  get recordTypeLabel(): string {
    return "";
  }

  contextMenuDisabled(): string | null {
    return null;
  }

  get permalinkURL(): URLType | null {
    const permalinkType = this.recordType.toLowerCase();
    if (!this.id) return null;
    return `/inventory/${permalinkType}/${this.id}`;
  }

  get ownerLabel(): string | null {
    return this.owner === null ? null : this.owner.fullName;
  }

  get currentUserIsOwner(): boolean | null {
    if (this.isWorkbench) return true;
    const currentUser = getRootStore().peopleStore.currentUser;
    if (!currentUser || !this.owner) return null;
    return this.owner.username === currentUser.username;
  }

  get isNewItem(): boolean {
    return Boolean(!this.globalId);
  }

  get isWorkbench(): boolean {
    return globalIdPatterns.bench.test(this.globalId ?? "");
  }

  get isTemplate(): boolean {
    return globalIdPatterns.sampleTemplate.test(this.globalId ?? "");
  }

  get fieldNamesInUse(): Array<string> {
    return [
      ...["Name", "Description", "Preview Image", "Tags", "Attachments"],
      ...this.visibleExtraFields.map((ef) => ef.name),
    ];
  }

  // see Movable: if top parent is a bench (item is 'in' bench ie at any level)
  isInWorkbench(): boolean {
    return false;
  }

  // see Movable: if top parent is current user's bench (item is 'in' bench ie at any level)
  isInCurrentUsersWorkbench(): boolean {
    return false;
  }

  // see Movable: if only parent is a bench (item is 'on' bench ie at top-level)
  isOnWorkbench(): boolean {
    return false;
  }

  // see Movable: if only parent is current user's bench (item is 'on' bench ie at top-level)
  isOnCurrentUsersWorkbench(): boolean {
    return false;
  }

  isMovable(): boolean {
    return false;
  }

  hasParentContainers(): boolean {
    return false;
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    if (!this.owner) throw new Error("Owner is required");
    const owner = this.owner;
    const options: AdjustableTableRowOptions<string> = new Map();
    options.set("Owner", () => ({ renderOption: "owner", data: owner }));
    // note: there is a non-breaking space (U+00A0) between "Global" and "ID"
    options.set("Global ID", () => ({ renderOption: "globalId", data: this }));
    if (this.readAccessLevel !== "public") {
      options.set("Last Modified", () => ({
        renderOption: "node",
        data: isoToLocale(this.lastModified),
      }));
      options.set("Created", () => ({
        renderOption: "node",
        data: isoToLocale(this.created),
      }));
      options.set("Tags", () => ({
        renderOption: "tags",
        data: this.tags,
      }));
    } else {
      options.set("Last Modified", () => ({
        renderOption: "node",
        data: null,
      }));
      options.set("Created", () => ({ renderOption: "node", data: null }));
    }
    return options;
  }

  get recordLinkLabel(): string {
    if (!this.owner || !this.isWorkbench) return this.name;
    const owner = this.owner;
    try {
      /*
       * isCurrentUser on its own can throw. Throwing means there is only one
       * code branch, avoiding the need to duplicate the template string logic
       */
      if (!owner.isCurrentUser) throw new Error("Not current user");
      return "My Bench";
    } catch {
      return `${owner.fullName}'s Bench`;
    }
  }

  /*
   * When a record is navigated to by the user tapping its link, this boolean
   * flag determines whether the UI should display the record's details (true)
   * or whether the UI should show the search result listings (false).
   */
  get showRecordOnNavigate(): boolean {
    return !this.isWorkbench;
  }

  /*
   * After a record has been made active, its full details are fetched. If the
   * user lacks the necessary permissions this call will fail, resulting in
   * infoLoaded still being false and loading to finish, and thus this will
   * return true.
   */
  get noFullDetails(): boolean {
    return !this.infoLoaded && !this.loading;
  }

  get hasSubSamples(): boolean {
    return false;
  }

  async submitAttachmentChanges(): Promise<void> {
    if (!this.globalId) throw new Error("Global Id not known");
    const g = this.globalId;
    await Promise.all(this.attachments.map((a) => a.save(g)));
  }

  get iconName(): string {
    return "";
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    const currentUser = getRootStore().peopleStore.currentUser;
    if (!currentUser) throw new Error("Current user is not known.");
    return {
      parentGlobalId: `BE${currentUser.workbenchId}`,
    };
  }

  get children(): Array<InventoryRecord> {
    return [];
  }

  loadChildren(): void {}

  get recordDetails(): RecordDetails {
    return {
      description: this.description,
      tags: this.tags,
      modified: [this.lastModified, this.modifiedByFullName],
      owner: this.ownerLabel,
    };
  }

  get canNavigateToChildren(): boolean {
    return false;
  }

  get illustration(): React.ReactNode {
    throw new Error("Abstract method; not implemented.");
  }

  async cancel(): Promise<void> {
    await this.setEditing(false);
  }

  /*
   * The current value of the editable fields, as required by the interface
   * `HasEditableFields` and `HasUneditableFields`.
   */
  get fieldValues(): ResultEditableFields & ResultUneditableFields {
    return {
      name: this.name,
      description: this.description,
      tags: this.tags,
      image: this.image ?? this.thumbnail,
      newBase64Image: this.newBase64Image,
      barcodes: this.barcodes,
      owner: this.owner,
      sharingMode: this.sharingMode,
      sharedWith: this.sharedWith,
    };
  }

  get supportsBatchEditing(): boolean {
    throw new Error("Abstract computed property; not implemented.");
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): { [key in keyof ResultEditableFields]: string | null } & {
    [key in keyof ResultUneditableFields]: string | null;
  } {
    return {
      name: null,
      description: null,
      tags: null,
      image: null,
      newBase64Image: null,
      barcodes: null,
      owner: null,
      sharingMode: null,
      sharedWith: null,
    };
  }

  refreshAssociatedSearch() {
    // do nothing; there is no search associated with all Results
  }

  updateBecauseRecordsChanged(_recordIds: Set<GlobalId>) {
    // to be implemented by the classes that extend this abstract one
  }

  showTopLinkInBreadcrumbs(): boolean {
    return false;
  }

  get showBarcode(): boolean {
    return true;
  }

  get createOptions(): ReadonlyArray<CreateOption> {
    return [];
  }
}
