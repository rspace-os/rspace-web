import { isEqual, pick } from "es-toolkit";
import { action, computed, makeObservable, observable, runInAction } from "mobx";
import type React from "react";
import type { AxiosProgressEvent } from "@/common/axios";
import i18n from "@/modules/common/i18n";
import TransRichText from "@/modules/common/i18n/TransRichText";
import ApiService from "../../common/InvApiService";
import { decodeTagString, encodeTagString } from "../../components/Tags/ParseEncodedTagStrings";
import { allAreValid, IsInvalid, IsValid, type ValidationResult } from "../../components/ValidatingSubmitButton";
import type { SortProperty } from "../../Inventory/components/Tables/SortableProperty";
import { getErrorMessage } from "../../util/error";
import { capImageAt1MB } from "../../util/images";
import { Optional } from "../../util/optional";
import * as Parsers from "../../util/parsers";
import { noProgress, type Progress } from "../../util/progress";
import Result from "../../util/result";
import type { _LINK, BlobUrl, URL as URLType } from "../../util/types";
import { isoToLocale, match } from "../../util/Util";
import { type Alert, mkAlert } from "../contexts/Alert";
import type { Attachment } from "../definitions/Attachment";
import type { BarcodeRecord, PersistedBarcodeAttrs } from "../definitions/Barcode";
import { type GlobalId, globalIdPatterns, type Id } from "../definitions/BaseRecord";
import type { Location } from "../definitions/Container";
import type { HasEditableFields, HasUneditableFields } from "../definitions/Editable";
import type { ExtraField, ExtraFieldAttrs } from "../definitions/ExtraField";
import type { Factory } from "../definitions/Factory";
import type { SharedWithGroup } from "../definitions/Group";
import type { Identifier, IdentifierAttrs } from "../definitions/Identifier";
import type {
  Action,
  ApiRecordType,
  CreateOption,
  InventoryRecord,
  LockStatus,
  RecordType,
  SharingMode,
  State,
} from "../definitions/InventoryRecord";
import type { Person, PersonAttrs } from "../definitions/Person";
import type { ReadAccessLevel, RecordDetails, Thumbnail } from "../definitions/Record";
import type { CoreFetcherArgs } from "../definitions/Search";
import type { AdjustableTableRow, AdjustableTableRowOptions } from "../definitions/Tables";
import type { Tag } from "../definitions/Tag";
import getRootStore from "../stores/getRootStore";
import { type AttachmentJson, newExistingAttachment } from "./AttachmentModel";
import { GeneratedBarcode, PersistedBarcode } from "./Barcode";
import type { ContainerInContainerParams } from "./ContainerModel";
import ExtraFieldModel from "./ExtraFieldModel";
import IdentifierModel from "./IdentifierModel";
import type { SampleInContainerParams } from "./SampleModel";

export type InventoryBaseRecordEditableFields = {
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
  identifiers: Array<Identifier>;
};

export type InventoryBaseRecordUneditableFields = {
  owner: Person | null;
};

export const sortProperties: Array<SortProperty> = [
  { key: "name", label: "name", adjustColumn: false },
  { key: "type", label: "type", adjustColumn: false },
  { key: "globalId", label: "globalId", adjustColumn: true },
  { key: "creationDate", label: "created", adjustColumn: true },
  {
    key: "modificationDate",
    label: "lastModified",
    adjustColumn: true,
  },
];

export const isSortable = (propKey: string): boolean => sortProperties.map((p) => p.key).includes(propKey);

const calculateUploadProgress = (soFar: number, total: number): number => Math.floor((soFar / total) * 10) * 10;

type LockOwner = {
  firstName: string;
  lastName: string;
  username: string;
};

export class RecordLockedError extends Error {
  record: InventoryBaseRecord;
  lockOwner: LockOwner;

  constructor(record: InventoryBaseRecord, lockOwner: LockOwner) {
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
  globalId: GlobalId | null;
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
  version?: number | null;
  historicalVersion?: boolean;
};

/**
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
 * instantiate those classes instead. It is a shame that we can't have
 * TypeScript enforce this for us with the "abstract" keyword but doing so would
 * deprive us of the ability to use mixins because mixin factory functions
 * cannot return abstract classes.
 */
export default class InventoryBaseRecord
  implements
    InventoryRecord,
    AdjustableTableRow<string>,
    HasEditableFields<InventoryBaseRecordEditableFields>,
    HasUneditableFields<InventoryBaseRecordUneditableFields>
{
  loading: boolean = false;
  editing: boolean = false;
  id: Id | null = null;
  globalId: GlobalId | null = null;
  // @ts-expect-error type is initialised by populateFromJson
  type: ApiRecordType;
  name: InventoryBaseRecordEditableFields["name"] = "";
  description: InventoryBaseRecordEditableFields["description"] = "";
  selected: boolean = false; // whether its checkbox is selected
  infoLoaded: boolean = false; // whether the full information is fetched
  extraFields: Array<ExtraField> = [];
  // @ts-expect-error currentlyVisibleFields is initialised by populateFromJson
  currentlyVisibleFields: Set<string>;
  // @ts-expect-error currentlyEditableFields is initialised by populateFromJson
  currentlyEditableFields: Set<string>;
  image: InventoryBaseRecordEditableFields["image"] = null;
  thumbnail: Thumbnail = null;
  tags: InventoryBaseRecordEditableFields["tags"] = [];
  // @ts-expect-error created is initialised by populateFromJson
  created: string;
  // @ts-expect-error lastModified is initialised by populateFromJson
  lastModified: string;
  // @ts-expect-error modifiedByFullName is initialised by populateFromJson
  modifiedByFullName: string;
  // @ts-expect-error owner is initialised by populateFromJson
  owner: Person | null;
  deleted: boolean = false;
  // @ts-expect-error _links is initialised by populateFromJson
  _links: { [_: string]: URLType };
  // @ts-expect-error uploadProgress is initialised by populateFromJson
  uploadProgress: Progress;
  // @ts-expect-error lastEditInput is initialised by populateFromJson
  lastEditInput: Date;
  // @ts-expect-error lockExpiry is initialised by populateFromJson
  lockExpiry: Date;
  lockExpired: boolean = false;
  expiryCheckInterval: NodeJS.Timeout | undefined;
  // @ts-expect-error permittedActions is initialised by populateFromJson
  permittedActions: Set<Action>;
  newBase64Image: InventoryBaseRecordEditableFields["newBase64Image"] = null;
  attachments: Array<Attachment> = [];
  identifiers: Array<Identifier> = [];
  iconId: number | null = null;
  barcodes: Array<BarcodeRecord> = [];
  factory: Factory;
  fetchingAdditionalInfo: Promise<{ data: object }> | null = null;
  // @ts-expect-error sharingMode is initialised by populateFromJson
  sharingMode: SharingMode;
  // @ts-expect-error sharingWith is initialised by populateFromJson
  sharedWith: Array<SharedWithGroup> | null;
  /** The user-facing version of the record, bumped on every content edit. */
  version: number | null = null;
  /**
   * True when this instance models a historical version of the record rather
   * than its live state. Historical records are read-only: the context menu
   * is disabled, the permalink carries the version, and full details are
   * fetched from the versions API.
   */
  historicalVersion: boolean = false;

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

  constructor(factory: Factory, _params: object) {
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
      version: observable,
      historicalVersion: observable,
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

  populateFromJson(factory: Factory, passedParams: object, defaultParams: object = {}): void {
    const params = { ...defaultParams, ...passedParams } as ResultAttrs;
    this.id = params.id;
    this.globalId = params.globalId;
    /*
     * version/historicalVersion are assigned before anything that reads
     * permalinkURL (attachments, barcodes) so that all consumers see a
     * consistent value.
     */
    this.version = params.version ?? null;
    this.historicalVersion = params.historicalVersion ?? false;
    this.type = params.type;
    this.name = params.name;
    this.description = params.description;
    this.extraFields = (params.extraFields ?? []).map((efParams) => new ExtraFieldModel(efParams, this));
    this.tags =
      params.tags === null || params.tags === ""
        ? []
        : params.tags.map((tag) => ({
            value: decodeTagString(tag.value),
            uri: tag.uri === "" ? Optional.empty() : Optional.present(decodeTagString(tag.uri)),
            vocabulary:
              tag.ontologyName === "" ? Optional.empty() : Optional.present(decodeTagString(tag.ontologyName)),
            version:
              tag.ontologyVersion === "" ? Optional.empty() : Optional.present(decodeTagString(tag.ontologyVersion)),
          }));
    this.lastModified = params.lastModified;
    this.created = params.created;
    this.modifiedByFullName = params.modifiedByFullName;
    this.owner = params.owner ? factory.newPerson(params.owner) : null;
    this.deleted = params.deleted;
    this.permittedActions = new Set(params.permittedActions);
    this.attachments = (params.attachments ?? []).map((a) =>
      newExistingAttachment(a, this.permalinkURL, () => this.setAttributesDirty({})),
    );
    this.iconId = params.iconId;
    this.sharingMode = params.sharingMode;
    if ("sharedWith" in params) {
      this.sharedWith = params.sharedWith;
    } else {
      this.sharedWith = null;
    }
    this.processLinks(params._links);

    if (this.id !== null && this.permalinkURL && this.readAccessLevel !== "public") {
      this.barcodes = [
        new GeneratedBarcode({
          /*
           * A barcode is a durable, physical artefact: it must always resolve
           * to the live record, never a version-pinned view.
           */
          data: `${window.location.origin}/inventory/${this.recordType.toLowerCase()}/${this.id}`,
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
    this._links = Object.fromEntries(_links.map(({ link, rel }) => [rel, link]));
  }

  setLoading(value: boolean): void {
    this.loading = value;
  }

  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
  setFieldsDirty(newFieldValues: {}) {
    this.setAttributesDirty(newFieldValues);
  }

  setDirtyFlag() {
    getRootStore().uiStore.setPageNavigationConfirmation(true);
    getRootStore().uiStore.setDirty(async () => {
      if (this.id && this.state === "edit") await this.setEditing(false);
      else this.unsetDirtyFlag();
    });
  }

  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
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

  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
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
    const apiTypeFor = (t: ExtraField["type"]): "text" | "number" | "link" => {
      if (t === "Text") return "text";
      if (t === "Number") return "number";
      return "link";
    };
    const extraFields = this.extraFields.map((ef) => {
      const base: {
        name: string;
        content: string;
        id: ExtraField["id"];
        type: "text" | "number" | "link";
        newFieldRequest: boolean;
        deleteFieldRequest: boolean;
        link?: {
          relationType: string;
          targetGlobalId: string;
          versionPin: number | null;
        };
      } = {
        name: ef.name,
        content: ef.content,
        id: ef.id,
        type: apiTypeFor(ef.type),
        newFieldRequest: ef.newFieldRequest,
        deleteFieldRequest: ef.deleteFieldRequest,
      };
      if (ef.type === "Link" && ef.link) {
        base.link = {
          relationType: ef.link.relationType,
          targetGlobalId: ef.link.targetGlobalId,
          versionPin: ef.link.versionPin ?? null,
        };
      }
      return base;
    });

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
    if (this.currentlyEditableFields.has("description")) params.description = this.description;
    if (this.currentlyEditableFields.has("extraFields")) params.extraFields = extraFields;
    if (this.currentlyEditableFields.has("tags")) {
      params.tags = this.tags.map((tag) => ({
        value: encodeTagString(tag.value),
        uri: tag.uri.map(encodeTagString).orElse(null),
        ontologyName: tag.vocabulary.map(encodeTagString).orElse(null),
        ontologyVersion: tag.version.map(encodeTagString).orElse(null),
      }));
    }

    if (this.currentlyEditableFields.has("image")) params.newBase64Image = this.newBase64Image;
    if (this.currentlyEditableFields.has("barcodes"))
      params.barcodes = this.barcodes
        .filter((barcode): barcode is PersistedBarcode => barcode instanceof PersistedBarcode)
        .map((b) => b.paramsForBackend);
    if (this.currentlyEditableFields.has("identifiers")) params.identifiers = this.identifiers.map((i) => i.toJson());
    if (this.currentlyEditableFields.has("sharingMode")) params.sharingMode = this.sharingMode;
    if (this.currentlyEditableFields.has("sharedWith")) params.sharedWith = this.sharedWith;
    return params;
  }

  validate(): ValidationResult {
    const validateName = () => {
      if (!this.isFieldEditable("name")) return IsValid();
      if (this.name.length < 1) return IsInvalid(i18n.t("inventory:baseRecord.validation.nameRequired"));
      if (this.name.length < 2) return IsInvalid(i18n.t("inventory:baseRecord.validation.nameSingleCharacter"));
      if (this.name.length > 255) return IsInvalid(i18n.t("inventory:baseRecord.validation.nameTooLong"));
      if (this.name.trim().length < 1) return IsInvalid(i18n.t("inventory:baseRecord.validation.nameWhitespace"));
      return IsValid();
    };

    const validateDescription = () => {
      if (!this.isFieldEditable("description")) return IsValid();
      if (typeof this.description === "undefined" || this.description === null) return IsValid();
      if (this.description.length <= 250) return IsValid();
      return IsInvalid(i18n.t("inventory:baseRecord.validation.descriptionTooLong"));
    };

    const validateTags = () => {
      if (!this.isFieldEditable("tags")) return IsValid();
      if (typeof this.tags === "undefined" || this.tags === null) return IsValid();
      if (this.tags.join(",").length > 255) return IsInvalid(i18n.t("inventory:baseRecord.validation.tooManyTags"));
      return IsValid();
    };

    const validateExtraFields = () => {
      if (new Set(this.fieldNamesInUse).size !== this.fieldNamesInUse.length)
        return IsInvalid(i18n.t("inventory:baseRecord.validation.distinctFieldNames"));
      return allAreValid(this.extraFields.map((e) => e.isValid));
    };

    return allAreValid([validateName(), validateDescription(), validateTags(), validateExtraFields()]);
  }

  get submittable(): ValidationResult {
    return this.validate().flatMap(() => {
      if (this.lockExpired) return IsInvalid(i18n.t("inventory:baseRecord.validation.editLockExpired"));
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
          i18n.t("inventory:baseRecord.editSessionExpiring.title"),
          i18n.t("inventory:baseRecord.editSessionExpiring.body", { recordType: this.recordTypeLabel.toLowerCase() }),
          i18n.t("inventory:baseRecord.editSessionExpiring.continue"),
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
          setTimeout(
            () => {
              uiStore.recentBatchEditExpiryCheck = null;
            },
            4 * 60 * 1000,
          );
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
        i18n.t("inventory:baseRecord.editSessionExpired.title"),
        <TransRichText
          i18nKey="inventory:baseRecord.editSessionExpired.body"
          values={{ recordType: this.recordTypeLabel.toLowerCase() }}
        />,
        i18n.t("common:actions.ok"),
        "",
      );
    }
  }

  handleLockExpiry(remainingSeconds: number) {
    if (remainingSeconds) {
      this.lockExpiry = new Date(this.lastEditInput.getTime() + remainingSeconds * 1000);
      void this.expiryCheck();
      this.expiryCheckInterval = setInterval(() => void this.expiryCheck(), 5000);
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
      const response = await ApiService.delete<void>(`editLocks/${this.globalId}`, "");
      return response.status === 200;
    } catch (error) {
      if (!silent) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: i18n.t("inventory:baseRecord.alerts.releaseLockFailed", {
              globalId: this.globalId ?? i18n.t("common:values.unknown"),
            }),
            message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
            variant: "error",
          }),
        );
      }
      console.error(`Error relinquishing control of ${this.globalId ?? "UNKNOWN"}`, error);
      throw new Error(`Error relinquishing control of ${this.globalId ?? "UNKNOWN"}`, { cause: error });
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
            title: i18n.t("inventory:baseRecord.alerts.lockCheckFailed", { name: this.name }),
            message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
            variant: "error",
          }),
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

  async setEditing(value: boolean, refresh?: boolean | null, silent?: boolean | null): Promise<LockStatus> {
    /*
     * Defence-in-depth: a historical version must never enter edit mode, as
     * saving would overwrite the live record with stale snapshot values. The
     * UI affordances are all disabled separately, but any path that slips
     * through is refused here.
     */
    if (value && this.historicalVersion) {
      console.warn("Refusing to edit a historical version of a record.");
      return "CANNOT_LOCK";
    }
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
        const { status, lockOwner, remainingTimeInSeconds } = await this.checkLock(silent);
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
                  title: i18n.t("inventory:baseRecord.unsavedChanges.title"),
                  message: i18n.t("inventory:baseRecord.unsavedChanges.message", {
                    recordType: this.recordTypeLabel.toLowerCase(),
                  }),
                  variant: "warning",
                  isInfinite: true,
                }),
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
        // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
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
  get inContainerParams(): ContainerInContainerParams | SampleInContainerParams | null {
    return null;
  }

  /*
   * Set the `selected` status of this record and synchronise the selection
   * with the searchStore's activeResult's `locations`, if the activeResult is
   * a Container and this record is amongst its location's contents. This is so
   * that a selection made in list view is maintained which switching to image
   * or grid view.
   */
  toggleSelected(value?: boolean | null) {
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
    const parentLocation = locations.find(({ content }) => (content as InventoryBaseRecord | null) === this);

    // this check is necessary to avoid stack overflow
    if (parentLocation?.selected !== value) {
      parentLocation?.toggleSelected(value);
    }
  }

  async fetchAdditionalInfo(
    silent: boolean = false,
    queryParameters: URLSearchParams = new URLSearchParams(),
  ): Promise<void> {
    if (this.fetchingAdditionalInfo || !this.id) {
      if (this.fetchingAdditionalInfo === null) return;
      await this.fetchingAdditionalInfo;
      return;
    }
    const id = this.id;

    this.setLoading(true);
    try {
      /*
       * A historical record must be re-fetched from the versions API:
       * fetching the plain record would silently overwrite the snapshot
       * with the latest state.
       */
      const endpoint =
        this.historicalVersion && this.version !== null
          ? `${this.recordType}s/${id}/versions/${this.version}`
          : `${this.recordType}s/${id}`;
      this.fetchingAdditionalInfo = ApiService.query<object>(endpoint, new URLSearchParams(queryParameters));
      const { data } = await this.fetchingAdditionalInfo;
      this.fetchingAdditionalInfo = null;
      runInAction(() => {
        this.infoLoaded = true;
      });
      this.populateFromJson(this.factory.newFactory(), data);
      await this.fetchImage("image");
      await this.fetchImage("thumbnail");
      return;
    } catch (error) {
      if (!silent) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: i18n.t("inventory:baseRecord.alerts.loadDetailsFailed", {
              globalId: this.globalId ?? "UNKNOWN",
            }),
            message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
            variant: "error",
          }),
        );
      }
      console.error(`Error fetching additional info for ${this.globalId ?? "UNKNOWN"}`, error);
      throw new Error(`Error fetching additional info for ${this.globalId ?? "UNKNOWN"}`, { cause: error });
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
              ? calculateUploadProgress(progressEvent.loaded, progressEvent.total)
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
            title: i18n.t("inventory:baseRecord.alerts.attachmentsSaveFailed"),
            message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
            variant: "error",
          }),
        );
      }
      // copy over attachments as data from first POST doesn't have them
      newRecord.attachments = data.attachments;
      await this.setEditing(false);
      await searchStore.search.setActiveResult(newRecord);
      uiStore.addAlert(
        mkAlert({
          message: i18n.t("inventory:baseRecord.alerts.created", { name: this.name }),
          variant: "success",
        }),
      );
      trackingStore.trackEvent("InventoryRecordCreated", this.dataAttachedToRecordCreatedAnaylticsEvent);
      if (this.recordType === "instrument" || this.recordType === "instrumentTemplate") {
        const type = this.recordType === "instrument" ? "instrument" : "instrument_template";
        trackingStore.trackEvent(`user:create:${type}:inventory`);
      }
      if (peopleStore.currentUser) await peopleStore.currentUser.getBench();
    } catch (error) {
      this.setAttributes({
        uploadProgress: noProgress,
      });
      Parsers.objectPath(["response"], error).do((response) => {
        try {
          const statusCode = Parsers.objectPath(["status"], response).flatMap(Parsers.isNumber).elseThrow();
          if (statusCode !== 400) throw new Error("Not a 400 status code");
          const validationErrors = Parsers.objectPath(["data", "data", "validationErrors"], response)
            .flatMap(Parsers.isArray)
            .elseThrow();
          const newAlert = mkAlert({
            message: i18n.t("inventory:baseRecord.alerts.invalidFields"),
            variant: "error",
            details: Result.any(
              ...validationErrors.map((e) =>
                Parsers.isObject(e)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) =>
                    Result.lift2<string, string, { title: string; help: string; variant: "error" }>((title, help) => ({
                      title,
                      help,
                      variant: "error",
                    }))(
                      Parsers.getValueWithKey("field")(obj).flatMap(Parsers.isString),
                      Parsers.getValueWithKey("message")(obj).flatMap(Parsers.isString),
                    ),
                  ),
              ),
            )
              .mapError(() => new Error("Could not parse any validation errors"))
              .elseThrow(),
            retryFunction: () => this.update().then(() => {}),
          });
          getRootStore().uiStore.addAlert(newAlert);
          getRootStore().searchStore.activeResult?.addScopedToast(newAlert);
        } catch {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: i18n.t("inventory:baseRecord.alerts.saveFailed", { recordType: this.recordType }),
              message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
              variant: "error",
            }),
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
  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
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
      const { data } = await ApiService.update<unknown>(`${this.recordType}s`, this.id, params, {
        onUploadProgress: (progressEvent: AxiosProgressEvent) =>
          this.setAttributes({
            uploadProgress: progressEvent.total
              ? calculateUploadProgress(progressEvent.loaded, progressEvent.total)
              : 0,
          }),
      });
      try {
        await this.saveAttachments();
      } catch (error) {
        getRootStore().uiStore.addAlert(
          mkAlert({
            title: i18n.t("inventory:baseRecord.alerts.attachmentsSaveFailed"),
            message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
            variant: "error",
          }),
        );
      }
      this.populateFromJson(this.factory.newFactory(), data as object);
      await this.setEditing(false, refresh);
      getRootStore().searchStore.search.replaceResult(this);
      getRootStore().uiStore.addAlert(
        mkAlert({
          message: i18n.t("inventory:baseRecord.alerts.updated", { name: this.name }),
          variant: "success",
        }),
      );
      if (this.recordType === "instrument" || this.recordType === "instrumentTemplate") {
        const type = this.recordType === "instrument" ? "instrument" : "instrument_template";
        getRootStore().trackingStore.trackEvent(`user:edit:${type}:inventory`);
      }
    } catch (error) {
      this.setAttributes({
        uploadProgress: noProgress,
      });
      Parsers.objectPath(["response"], error).do((response) => {
        try {
          const statusCode = Parsers.objectPath(["status"], response).flatMap(Parsers.isNumber).elseThrow();
          if (statusCode !== 400) throw new Error("Not a 400 status code");
          const validationErrors = Parsers.objectPath(["data", "data", "validationErrors"], response)
            .flatMap(Parsers.isArray)
            .elseThrow();
          const newAlert = mkAlert({
            message: i18n.t("inventory:baseRecord.alerts.invalidFields"),
            variant: "error",
            details: Result.any(
              ...validationErrors.map((e) =>
                Parsers.isObject(e)
                  .flatMap(Parsers.isNotNull)
                  .flatMap((obj) =>
                    Result.lift2<string, string, { title: string; help: string; variant: "error" }>((title, help) => ({
                      title,
                      help,
                      variant: "error",
                    }))(
                      Parsers.getValueWithKey("field")(obj).flatMap(Parsers.isString),
                      Parsers.getValueWithKey("message")(obj).flatMap(Parsers.isString),
                    ),
                  ),
              ),
            )
              .mapError(() => new Error("Could not parse any validation errors"))
              .elseThrow(),
            retryFunction: () => this.update().then(() => {}),
          });
          getRootStore().uiStore.addAlert(newAlert);
          getRootStore().searchStore.activeResult?.addScopedToast(newAlert);
        } catch {
          getRootStore().uiStore.addAlert(
            mkAlert({
              title: i18n.t("inventory:baseRecord.alerts.saveFailed", { recordType: this.recordType }),
              message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
              variant: "error",
            }),
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
    if (this.editing || this.id === null || !Object.hasOwn(this, field)) {
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
      !this.loading &&
      !this.deleted &&
      // a historical version is read-only by construction
      !this.historicalVersion &&
      this.currentlyEditableFields.has(field)
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
          this,
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
    updatedField: {
      name: string;
      type: string;
      link?: {
        relationType: string;
        targetGlobalId: string;
        versionPin: number | null;
      };
    },
  ) {
    const field = this.extraFields.find((ef) => ef.name === oldFieldName);

    if (field) {
      const typeChanged = field.type !== updatedField.type;
      const attrs: Record<string, unknown> = {
        name: updatedField.name,
        type: updatedField.type,
        editing: false,
        initial: false,
        content: typeChanged ? "" : field.content,
      };
      if (updatedField.type === "Link") {
        // Only overwrite the link when the caller actually supplies one. Omitting
        // it (a rename-only edit, or discardChanges restoring name/type) must
        // preserve the existing link rather than wipe it, since setAttributes
        // merges via Object.assign.
        if (updatedField.link !== undefined) {
          attrs.link = updatedField.link;
        }
      } else if (typeChanged) {
        attrs.link = null;
      }
      const existingLink = field.link;
      const incomingLink = updatedField.link;
      const linkUnchanged =
        updatedField.type !== "Link" ||
        incomingLink === undefined ||
        (existingLink !== null &&
          existingLink.relationType === incomingLink.relationType &&
          existingLink.targetGlobalId === incomingLink.targetGlobalId &&
          (existingLink.versionPin ?? null) === (incomingLink.versionPin ?? null));
      const areEqual = isEqual(pick(field, ["name", "type"]), pick(updatedField, ["name", "type"])) && linkUnchanged;
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
          i18n.t("inventory:identifierConfirm.create.title"),
          <TransRichText i18nKey="inventory:identifierConfirm.create.body" />,
          i18n.t("common:actions.ok"),
          i18n.t("common:actions.cancel"),
        )
      ) {
        const globalId = this.globalId;
        if (!globalId) throw new Error("Global Id is required.");
        const response = await ApiService.post<IdentifierAttrs>(`/identifiers`, {
          parentGlobalId: globalId,
        });
        const newIGSN = new IdentifierModel(response.data, globalId, ApiService);
        this.identifiers = this.identifiers.concat(newIGSN);
        getRootStore().searchStore.search.replaceResult(this);
        getRootStore().uiStore.addAlert(
          mkAlert({
            message: i18n.t("inventory:identifiers.alerts.created", { doi: response.data.doi }),
            variant: "success",
          }),
        );
      }
    } catch (error) {
      // in case of errors like 404 the server provides a specific response message that we want to display
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:identifiers.alerts.createFailed"),
          message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
          variant: "error",
        }),
      );
      throw new Error(`An error occurred while minting the identifier: ${error}`);
    } finally {
      this.setLoading(false);
    }
  }

  async removeIdentifier(id: Id): Promise<void> {
    this.setLoading(true);
    try {
      if (
        await getRootStore().uiStore.confirm(
          i18n.t("inventory:identifierConfirm.delete.title"),
          i18n.t("inventory:identifierConfirm.delete.body"),
          i18n.t("common:actions.ok"),
          i18n.t("common:actions.cancel"),
        )
      ) {
        if (!id) throw new Error("DOI Id must be known.");
        const response = await ApiService.delete<unknown>(`/identifiers/${id}`, "");
        if (response.data) {
          const index = this.identifiers.findIndex((identifier) => identifier.id === id);
          this.identifiers.splice(index, 1);
          getRootStore().uiStore.addAlert(
            mkAlert({
              message: i18n.t("inventory:identifiers.alerts.draftDeleted"),
              variant: "success",
            }),
          );
        }
      }
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:identifiers.alerts.draftDeleteFailed"),
          message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
          variant: "error",
        }),
      );
      throw new Error(`An error occurred while deleting the identifier draft: ${error}`);
    } finally {
      this.setLoading(false);
    }
  }

  /* attributes are updated on identifer. this action to be used separate from publishing */
  updateIdentifiers() {
    this.setAttributesDirty({ identifiers: this.identifiers });
  }

  fetchImage(name: "image" | "locationsImage" | "thumbnail"): Promise<BlobUrl | null> {
    const link = this._links[name];
    if (!link) return Promise.resolve(null);
    return getRootStore()
      .imageStore.fetchImage(link)
      .then(
        action((blobUrl) => {
          // @ts-expect-error locationsImage is container-specific
          this[name] = blobUrl;
          return null;
        }),
      );
  }

  setImage(
    imageName: "image" | "locationsImage",
  ): ({ dataURL, file }: { dataURL: string; file: Blob }) => Promise<void> {
    return async ({ dataURL, file }: { dataURL: string; file: Blob }) => {
      const scaledImage = await capImageAt1MB(file, dataURL);
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
    return this.historicalVersion
      ? i18n.t("inventory:contextMenu.historicalVersion", {
          recordType: this.recordTypeLabel.toLowerCase() || i18n.t("common:recordTypes.record.lower"),
        })
      : null;
  }

  get permalinkURL(): URLType | null {
    const permalinkType = this.recordType.toLowerCase();
    if (!this.id) return null;
    if (this.historicalVersion && this.version !== null)
      return `/inventory/${permalinkType}/${this.id}?version=${this.version}`;
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

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    if (!this.owner) throw new Error("Owner is required");
    const owner = this.owner;
    const options: AdjustableTableRowOptions<string> = new Map();
    options.set("owner", () => ({ renderOption: "owner", data: owner }));
    options.set("globalId", () => ({ renderOption: "globalId", data: this }));
    if (this.readAccessLevel !== "public") {
      options.set("lastModified", () => ({
        renderOption: "node",
        data: isoToLocale(this.lastModified),
      }));
      options.set("created", () => ({
        renderOption: "node",
        data: isoToLocale(this.created),
      }));
      options.set("tags", () => ({
        renderOption: "tags",
        data: this.tags,
      }));
    } else {
      options.set("lastModified", () => ({
        renderOption: "node",
        data: null,
      }));
      options.set("created", () => ({ renderOption: "node", data: null }));
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
      return i18n.t("inventory:moveToTarget.myBench");
    } catch {
      return i18n.t("inventory:moveToTarget.ownerBench", { owner: owner.fullName });
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
  get fieldValues(): InventoryBaseRecordEditableFields & InventoryBaseRecordUneditableFields {
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
      identifiers: this.identifiers,
    };
  }

  get supportsBatchEditing(): boolean {
    throw new Error("Abstract computed property; not implemented.");
  }

  get noValueLabel(): {
    [key in keyof InventoryBaseRecordEditableFields]: string | null;
  } & {
    [key in keyof InventoryBaseRecordUneditableFields]: string | null;
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
      identifiers: null,
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
