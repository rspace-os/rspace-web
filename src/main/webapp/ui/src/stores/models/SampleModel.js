// @flow

import ApiService from "../../common/InvApiService";
import {
  action,
  computed,
  observable,
  override,
  makeObservable,
  runInAction,
} from "mobx";
import { match } from "../../util/Util";
import FieldModel, { type FieldModelAttrs } from "./FieldModel";
import { type ExtraFieldAttrs } from "../definitions/ExtraField";
import RecordWithQuantity, {
  type Quantity,
  type RecordWithQuantityEditableFields,
  type RecordWithQuantityUneditableFields,
} from "./RecordWithQuantity";
import SubSampleModel, { type SubSampleAttrs } from "./SubSampleModel";
import getRootStore from "../stores/RootStore";
import { mkAlert } from "../contexts/Alert";
import Search from "./Search";
import {
  RESULT_FIELDS,
  defaultVisibleResultFields,
  defaultEditableResultFields,
} from "./Result";
import { type Factory } from "../definitions/Factory";
import ResultCollection, {
  type ResultCollectionEditableFields,
} from "./ResultCollection";
import RsSet from "../../util/set";
import { blobToBase64 } from "../../util/files";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import { type AttachmentJson } from "./AttachmentModel";
import { type CoreFetcherArgs } from "../definitions/Search";
import {
  type Id,
  type GlobalId,
  getSavedGlobalId,
} from "../definitions/BaseRecord";
import { type RecordDetails } from "../definitions/Record";
import {
  type InventoryRecord,
  type RecordType,
  type Action,
  type SharingMode,
  type CreateOption,
  inventoryRecordTypeLabels,
} from "../definitions/InventoryRecord";
import { type _LINK } from "../../common/ApiServiceBase";
import { type PersonAttrs } from "../definitions/Person";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";
import { type Template } from "../definitions/Template";
import { type Attachment } from "../definitions/Attachment";
import {
  type Sample,
  type Temperature,
  type Alias,
  type SampleSource,
} from "../definitions/Sample";
import { CELSIUS } from "../definitions/Units";
import { validateTemperature } from "../../util/conversions";
import SampleIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/Sample";
import React, { type Node } from "react";
import { type BarcodeAttrs } from "../definitions/Barcode";
import { type SharedWithGroup } from "../definitions/Group";
import type { IdentifierAttrs } from "../definitions/Identifier";
import type { Field } from "../definitions/Field";
import {
  IsInvalid,
  IsValid,
  allAreValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import * as ArrayUtils  from "../../util/ArrayUtils";

type SampleEditableFields = {
  ...RecordWithQuantityEditableFields,
  expiryDate: ?string,
  sampleSource: SampleSource,
  storageTempMin: ?Temperature,
  storageTempMax: ?Temperature,
  subSampleAlias: Alias,
  ...
};

type SampleUneditableFields = {
  ...RecordWithQuantityUneditableFields,
};

export type SubSampleTargetLocation = {
  containerId: Id,
  location: { id: Id },
};

export type SampleInContainerParams = {
  newSampleSubSampleTargetLocations: Array<SubSampleTargetLocation>,
};

export type SampleAttrs = {|
  id: Id,
  type: string,
  globalId: ?GlobalId,
  name: string,
  permittedActions: Array<Action>,
  templateId: Id,
  templateVersion: ?number,
  subSampleAlias: Alias,
  subSamplesCount: number | "",
  subSamples: Array<SubSampleAttrs>,
  quantity: Quantity,
  storageTempMin: ?Temperature,
  storageTempMax: ?Temperature,
  fields: Array<FieldModelAttrs>,
  extraFields: Array<ExtraFieldAttrs>,
  description: string,
  tags: ?string,
  sampleSource: string,
  expiryDate: ?string,
  iconId: ?number,
  owner: ?PersonAttrs,
  created: ?string,
  lastModified: ?string,
  modifiedByFullName: ?string,
  deleted: boolean,
  attachments: Array<AttachmentJson>,
  barcodes: Array<BarcodeAttrs>,
  identifiers: Array<IdentifierAttrs>,
  sharingMode: SharingMode,
  sharedWith: Array<SharedWithGroup>,
  _links: Array<_LINK>,
|};

const DEFAULT_SAMPLE: SampleAttrs = {
  id: null,
  type: "SAMPLE",
  globalId: null,
  name: "",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  templateId: null,
  templateVersion: null,
  subSampleAlias: { alias: "subsample", plural: "subsamples" },
  subSamplesCount: 0,
  subSamples: [],
  quantity: { numericValue: 1, unitId: 3 },
  storageTempMin: { numericValue: 15, unitId: CELSIUS },
  storageTempMax: { numericValue: 30, unitId: CELSIUS },
  fields: [],
  extraFields: [],
  description: "",
  tags: "",
  sampleSource: "LAB_CREATED",
  expiryDate: null,
  iconId: null,
  owner: null,
  created: null,
  deleted: false,
  lastModified: null,
  modifiedByFullName: null,
  attachments: [],
  barcodes: [],
  identifiers: [],
  sharingMode: "OWNER_GROUPS",
  sharedWith: [],
  _links: [],
};

const FIELDS: Set<string> = new Set([
  ...RESULT_FIELDS,
  "template",
  "fields",
  "quantity",
  "subSampleQuantity",
  "storageTempMin",
  "storageTempMax",
  "subSamplesCount",
  "subsamples",
  "expiryDate",
  "sampleSource",
  "subSampleAlias",
]);
export { FIELDS as SAMPLE_FIELDS };
const defaultVisibleFields: Set<string> = new Set([
  ...FIELDS,
  ...defaultVisibleResultFields,
]);
export { defaultVisibleFields as defaultVisibleSampleFields };
const defaultEditableFields: Set<string> = new Set([
  ...defaultEditableResultFields,
]);
export { defaultEditableFields as defaultEditableSampleFields };

export default class SampleModel
  extends RecordWithQuantity
  implements
    Sample,
    HasEditableFields<SampleEditableFields>,
    HasUneditableFields<SampleUneditableFields>
{
  subSamplesCount: number = 0;
  subSamples: Array<SubSampleModel> = [];
  newSampleSubSamplesCount: ?number = 1;
  newSampleSubSampleTargetLocations: ?Array<SubSampleTargetLocation> = null;
  storageTempMin: ?Temperature;
  storageTempMax: ?Temperature;
  fields: Array<Field> = [];
  expiryDate: SampleEditableFields["expiryDate"];
  template: ?Template;
  sampleSource: SampleEditableFields["sampleSource"];
  search: Search;
  subSampleAlias: Alias;
  templateId: Id;
  templateVersion: ?number;
  createOptionsParametersState: {|
    split: {| key: "split",  copies: number |},
    newSubsamplesCount: {| key: "newSubsamplesCount", count: number |},
    newSubsamplesQuantity: {| key: "newSubsamplesQuantity", quantity: number, quantityLabel: string |},
    name: {| key: "name", value: string |},
    fields: {|
      key: "fields",
      copyFieldContent: $ReadOnlyArray<{|
        id: Id,
        name: string,
        content: string,
        hasContent: boolean,
        selected: boolean,
      |}>,
    |},
  |};

  constructor(factory: Factory, params: SampleAttrs = { ...DEFAULT_SAMPLE }) {
    super(factory);
    makeObservable(this, {
      subSamplesCount: observable,
      subSamples: observable,
      newSampleSubSamplesCount: observable,
      newSampleSubSampleTargetLocations: observable,
      storageTempMin: observable,
      storageTempMax: observable,
      fields: observable,
      expiryDate: observable,
      template: observable,
      sampleSource: observable,
      search: observable,
      subSampleAlias: observable,
      templateId: observable,
      createOptionsParametersState: observable,
      overrideFields: action,
      saveFieldAttachments: action,
      overrideTemp: action,
      overrideName: action,
      overrideSource: action,
      setTemplate: action,
      updateToLatestTemplate: action,
      paramsForBackend: override,
      updateFieldsState: override,
      recordTypeLabel: override,
      cardTypeLabel: override,
      hasSubSamples: override,
      iconName: override,
      recordType: override,
      showNewlyCreatedRecordSearchParams: override,
      fetchAdditionalInfo: override,
      children: override,
      recordDetails: override,
      supportsBatchEditing: override,
      fieldNamesInUse: override,
      minTempValue: computed,
      maxTempValue: computed,
      tempUnitId: computed,
      hasSelectedSubsample: computed,
      enforceMandatoryFields: computed,
    });

    if (this.recordType === "sample") {
      this.populateFromJson(factory, params, DEFAULT_SAMPLE);
    }

    // searching with parentGlobalId of an item you have no permission to (public view) will just return an empty array for results
    this.search = new Search({
      fetcherParams: {
        parentGlobalId: this.globalId,
        resultType: "SUBSAMPLE",
      },
      uiConfig: {
        allowedSearchModules: new Set(["TYPE", "STATUS", "TAG"]),
        allowedTypeFilters: new Set(["SUBSAMPLE"]),
        hideContentsOfChip: true,
      },
      factory: this.factory.newFactory(),
    });
  }

  populateFromJson(factory: Factory, params: any, defaultParams: ?any = {}) {
    super.populateFromJson(factory, params, defaultParams);
    params = { ...defaultParams, ...params };
    this.subSamplesCount = params.subSamplesCount;
    this.subSamples = (params.subSamples ?? []).map((s) => {
      const newRecord = factory.newRecord({ ...s, sample: this });
      newRecord.populateFromJson(factory, { ...s, sample: this });
      return newRecord;
    });
    this.storageTempMin = params.storageTempMin;
    this.storageTempMax = params.storageTempMax;
    this.overrideFields(params.fields ?? []);
    this.expiryDate = params.expiryDate;
    this.template = params.template;
    this.sampleSource = params.sampleSource;
    this.subSampleAlias = params.subSampleAlias;
    this.templateId = params.templateId;
    this.templateVersion = params.templateVersion ?? 1;
      this.createOptionsParametersState = {
        split: { key: "split", copies: 2 },
        name: { key: "name", value: "" },
        fields: {
          key: "fields",
          copyFieldContent: [
            ...this.fields.map(f => ({
              id: f.id,
              name: f.name,
              content: f.renderContentAsString,
              hasContent: f.hasContent,
              selected: false
            })),
            ...this.extraFields.map(e => ({
              id: e.id,
              name: e.name,
              content: e.content,
              hasContent: e.hasContent,
              selected: false
            }))
          ],
        },
      newSubsamplesCount: { key: "newSubsamplesCount", count: 1 },
      newSubsamplesQuantity: { key: "newSubsamplesQuantity", quantity: 1, quantityLabel: this.quantityUnitLabel },
      };
  }

  get recordType(): RecordType {
    return "sample";
  }

  async fetchAdditionalInfo(silent: boolean = false) {
    if (this.fetchingAdditionalInfo) {
      await this.fetchingAdditionalInfo;
      return;
    }
    this.fetchingAdditionalInfo = new Promise((resolve, reject) => {
      super
        .fetchAdditionalInfo(silent)
        .then(() => {
          if (this.templateId) {
            const templateId = this.templateId;
            getRootStore()
              .searchStore.getTemplate(
                templateId,
                this.templateVersion,
                this.factory.newFactory()
              )
              .then((template) => {
                runInAction(() => {
                  this.template = template;
                });
                resolve();
              })
              .catch(reject);
          } else {
            resolve();
          }
        })
        .catch(reject);
    });
    await this.fetchingAdditionalInfo;
  }

  get minTempValue(): ?number {
    return this.storageTempMin ? this.storageTempMin.numericValue : null;
  }

  get maxTempValue(): ?number {
    return this.storageTempMax ? this.storageTempMax.numericValue : null;
  }

  get tempUnitId(): ?number {
    return this.storageTempMax ? this.storageTempMax.unitId : null;
  }

  /*
   * A plain object that can be encoded to JSON for submission to the backend
   * when API calls are made. It is vital that there are no cyclical memory
   * references in the object returned by this computed properties. See
   * ./__tests__/SampleModel/paramsForBackend.test.js for the tests that assert
   * that this object can be serialised; any changes should be reflected there.
   */
  get paramsForBackend(): any {
    const fields = this.fields.map((field) => field.paramsForBackend);

    // Calculate total quantity when creating new
    let quantity = this.quantity;
    if (this.id === null && this.newSampleSubSamplesCount) {
      quantity = {
        numericValue: this.quantityValue * this.newSampleSubSamplesCount,
        unitId: this.quantityUnitId,
      };
    }

    const params = { ...super.paramsForBackend };
    if (this.currentlyEditableFields.has("subSamplesCount"))
      params.newSampleSubSamplesCount = this.newSampleSubSamplesCount;
    if (this.currentlyEditableFields.has("subsamples"))
      params.subSamples = this.subSamples.map((s) => ({
        ...s.paramsForBackend,
      }));
    if (this.currentlyEditableFields.has("subSampleQuantity"))
      params.quantity = quantity;
    if (this.currentlyEditableFields.has("storageTempMin"))
      params.storageTempMin = this.storageTempMin;
    if (this.currentlyEditableFields.has("storageTempMax"))
      params.storageTempMax = this.storageTempMax;
    if (this.currentlyEditableFields.has("fields")) params.fields = fields;
    if (this.currentlyEditableFields.has("expiryDate"))
      params.expiryDate = this.expiryDate;
    if (this.currentlyEditableFields.has("sampleSource"))
      params.sampleSource = this.sampleSource;
    if (this.currentlyEditableFields.has("quantity"))
      params.quantity = quantity;
    if (this.currentlyEditableFields.has("template") && this.template)
      params.templateId = this.template.id;
    return params;
  }

  overrideFields(fields: Array<Field> | Array<FieldModelAttrs>) {
    this.setAttributes({
      fields: fields.map((f) => {
        if (f instanceof FieldModel) {
          f.owner = this;
          return f;
        }
        // $FlowFixMe[cannot-spread-interface]
        return new FieldModel({ ...f }, this);
      }),
    });
  }

  async saveAttachments(newRecord?: InventoryRecord): Promise<void> {
    if (newRecord) {
      if (!(newRecord instanceof SampleModel))
        throw new TypeError("Expecting SampleModel");
      const sample: Sample = newRecord;
      if (!sample.globalId) throw new TypeError("Global ID is required");
      const globalId = sample.globalId;
      await Promise.all([
        super.saveAttachments(),
        this.saveFieldAttachments(globalId, sample.fields),
      ]);
    } else {
      await Promise.all([super.saveAttachments(), this.saveFieldAttachments()]);
    }
  }

  /*
   * Whenever a sample's modifications are saved, any modified attachments must
   * be submitted to the API individually.
   */
  async saveFieldAttachments(
    newResultGlobalId?: GlobalId,
    newFields?: Array<Field>
  ): Promise<void> {
    const findGlobalIdOfField = (attachment: Attachment): ?GlobalId => {
      const field = this.fields.find((f) => f.attachment === attachment);
      if (!field) throw new Error("Could not find field");
      if (!newResultGlobalId) return field.globalId;
      return newFields?.find((f) => f.name === field.name)?.globalId;
    };

    const fieldAttachments: Array<Attachment> = ArrayUtils.filterNull(this.fields
      .filter((f) => Boolean(f.attachment))
      // handle removal of correct field attachment
      .map((f) =>
        f.attachment?.removed ? f.originalAttachment : f.attachment
      ));

    await Promise.all(fieldAttachments.map(attachment => {
      const g = findGlobalIdOfField(attachment);
      if (!g) return Promise.reject(new Error("Could not find Global Id for a field"));
      return attachment.save(g);
    }));
  }

  updateFieldsState() {
    this.currentlyVisibleFields = defaultVisibleFields;
    this.currentlyEditableFields = defaultEditableFields;

    switch (this.state) {
      case "edit":
        this.setEditable(FIELDS, true);
        this.setEditable(
          new Set(["template", "quantity", "subSamplesCount"]),
          false
        );
        this.setVisible(new Set(["quantity", "template", "subsamples"]), false);
        this.setEditableExtraFields(this.extraFields, true);
        break;
      case "preview":
        this.setEditable(FIELDS, false);
        this.setVisible(FIELDS, true);
        break;
      case "create":
        this.setEditable(FIELDS, true);
        this.setEditable(new Set(["quantity"]), false);
        this.setVisible(new Set(["subsamples"]), false);
    }
  }

  /*
   * When batch editing, we want all fields to begin in a disabled state, with
   * the user choosing to enable the fields that they wish to edit. By default,
   * when a record is in edit mode, most of the fields are enabled, however.
   * Therefore, this method provides a simple way to set all of the fields back
   * to not being editable after batch editing has been enabled.
   */
  setFieldsStateForBatchEditing() {
    this.setEditable(FIELDS, false);
  }

  overrideTemp(
    min: ?{ numericValue: number, unitId: number },
    max: ?{ numericValue: number, unitId: number }
  ) {
    if (!min || !max) return;

    this.setAttributes({
      storageTempMin: min,
      storageTempMax: max,
    });
  }

  overrideName(tempName: string) {
    // only override if not set by the user
    if (tempName && (!this.name || !getRootStore().uiStore.dirty)) {
      this.setAttributes({
        name: tempName,
      });
    }
  }

  overrideSource(tempSource: string) {
    if (tempSource) {
      this.setAttributes({
        sampleSource: tempSource,
      });
    }
  }

  async setTemplate(template: Template): Promise<void> {
    await template.fetchAdditionalInfo();

    this.setAttributes({
      template,
      templateId: template.id,
      templateVersion: template.version,
    });

    if (!this.id) {
      // Make sure we don't trigger dirty: true with the following overrides

      // override these only if the user has not set them
      this.overrideName(template.name);

      // override these regardless of if the user has set them
      this.overrideTemp(template.storageTempMin, template.storageTempMax);
      this.overrideFields(template.fields);
      this.overrideSource(template.sampleSource);
      this.setAttributes({
        quantity: {
          numericValue: this.quantity?.numericValue,
          unitId: parseInt(template.defaultUnitId),
        },
        subSampleAlias: template.subSampleAlias,
        expiryDate: template.expiryDate,
        description: template.description,
        tags: template.tags,
      });
    }
  }

  async sampleCreationParams(includeContentForFields: Set<Id>): Promise<{}> {
    const newBase64Image = this.image
      ? await fetch(this.image)
          .then((x) => x.blob())
          .then(blobToBase64)
      : null;
    /*
     * Note that not all data is copied over. This is intentional.
     * Tags in particular are not copied over because a highly specific tag on
     * a sample is unlikely to be useful on a template as in all probability it
     * would not be shared amongst all of the samples that would then be
     * created from such a template. The reverse, however, is not true and tags
     * on templates are copied over to the samples that are created from them,
     * maintaining the ability to create samples with pre-populated tags in the
     * uncommon case where that is useful.
     */
    return {
      id: this.id,
      globalId: this.globalId,
      storageTempMin: this.storageTempMin,
      storageTempMax: this.storageTempMax,
      newBase64Image,
      fields: [
        ...this.fields.map((f) => f.paramsForBackend),
        ...this.extraFields.map(({ name, type, content, id }) => ({
          name,
          type,
          content: includeContentForFields.has(id) ? content : "",
          definition: null,
        })),
      ],
    };
  }

  validateQuantity(): ValidationResult {
    return Parsers.isNotBottom(this.quantity)
      .flatMap(({ numericValue }) => Parsers.isNumber(numericValue))
      .mapError(() => new Error("Quantity is invalid."))
      .flatMap((value) =>
        value < 0 ? IsInvalid("Quantity must be a positive value.") : IsValid()
      );
  }

  validate(): ValidationResult {
    const validateNewSubSamplesCount = () => {
      const newSampleSubSamplesCount = this.newSampleSubSamplesCount;
      if (
        newSampleSubSamplesCount === null ||
        typeof newSampleSubSamplesCount === "undefined"
      )
        return IsInvalid("Number of subsamples is invalid.");
      if (newSampleSubSamplesCount <= 0)
        return IsInvalid("Number of subsamples must be a positive value.");
      if (newSampleSubSamplesCount > 100)
        return IsInvalid("Number of subsamples cannot exceed 100.");
      return IsValid();
    };

    const validateFields = () => {
      return allAreValid(this.fields.map((f) => f.validate()));
    };

    const validateExpiryDate = () => {
      return Result.first(
        !this.expiryDate ? Result.Ok(null) : Result.Error<null>([]),
        Parsers.isNotBottom(this.expiryDate)
          .flatMap(Parsers.parseDate)
          .mapError(() => new Error("Invalid expiry date."))
          .map(() => null)
      );
    };

    return allAreValid([
      super.validate(),
      validateNewSubSamplesCount(),
      validateFields(),
      this.validateQuantity(),
      validateTemperature(this.storageTempMin).mapError(
        () => new Error("Minimum temperature is invalid.")
      ),
      validateTemperature(this.storageTempMax).mapError(
        () => new Error("Maximum temperature is invalid.")
      ),
      validateExpiryDate(),
    ]);
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.sample;
  }

  get cardTypeLabel(): string {
    return this.recordTypeLabel;
  }

  async updateToLatestTemplate(): Promise<void> {
    if (!this.id) throw new Error("Does not have an id.");
    try {
      await ApiService.post<{||}, void>(
        `samples/${this.id}/actions/updateToLatestTemplateVersion`,
        {}
      );
      getRootStore().uiStore.addAlert(
        mkAlert({
          message: "Sample updated to latest template successfully.",
          variant: "success",
        })
      );
      await this.fetchAdditionalInfo();
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: "Updating sample to latest template failed.",
          message:
            error.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
        })
      );
      console.error("Could not update template to latest", error);
    }
  }

  get hasSelectedSubsample(): boolean {
    return Boolean(this.search.selectedResults[0]);
  }

  contextMenuDisabled(): ?string {
    const searchShowsSelection = new Set(["LIST", "GRID", "IMAGE"]).has(
      this.search.searchView
    );
    return (
      super.contextMenuDisabled() ??
      (this.hasSelectedSubsample && searchShowsSelection
        ? "Cannot modify this sample whilst its subsamples are selected."
        : null)
    );
  }

  get fieldNamesInUse(): Array<string> {
    return [
      ...super.fieldNamesInUse,
      ...this.fields.filter((f) => f.name).map((f) => f.name),
      ...[
        "Sample Template",
        "Expiry Date",
        "Source",
        "Storage Temperature",
        "Total Quantity",
        "Subsamples",
      ],
    ];
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    const options = super.adjustableTableOptions();
    if (this.readAccessLevel !== "public") {
      options.set("Expiry Date", () => ({
        renderOption: "node",
        data: this.expiryDate ?? "NONE",
      }));
      options.set("Subsamples Count", () => ({
        renderOption: "node",
        data: this.subSamplesCount,
      }));
    } else {
      options.set("Expiry Date", () => ({ renderOption: "node", data: null }));
      options.set("Subsamples Count", () => ({
        renderOption: "node",
        data: null,
      }));
    }
    return options;
  }

  get hasSubSamples(): boolean {
    return true;
  }

  get iconName(): string {
    return "sample";
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return {
      resultType: "SAMPLE",
    };
  }

  get children(): Array<InventoryRecord> {
    // $FlowExpectedError[incompatible-return] I think this is a bug in flow
    return this.subSamples;
  }

  loadChildren(): void {
    void this.fetchAdditionalInfo();
  }

  get illustration(): Node {
    return <SampleIllustration />;
  }

  get recordDetails(): RecordDetails {
    return Object.assign(
      { ...super.recordDetails },
      {
        quantity: this.quantityLabel,
      }
    );
  }

  /*
   * The current value of the editable fields, as required by the interface
   * `HasEditableFields` and `HasUneditableFields`.
   */
  get fieldValues(): any {
    return {
      ...super.fieldValues,
      expiryDate: this.expiryDate,
      sampleSource: this.sampleSource,
      storageTempMin: this.storageTempMin,
      storageTempMax: this.storageTempMax,
      subSampleAlias: this.subSampleAlias,
    };
  }

  get supportsBatchEditing(): boolean {
    return true;
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof SampleEditableFields]: ?string} & {[key in keyof SampleUneditableFields]: ?string} {
    return {
      ...super.noValueLabel,
      sampleSource: null,
      expiryDate: null,
      storageTempMin: "Unspecified",
      storageTempMax: "Unspecified",
      subSampleAlias: null,
    };
  }

  refreshAssociatedSearch() {
    if (this.id !== null) {
      void this.search.fetcher.performInitialSearch();
    }
  }

  /*
   * When some subsamples have been deleted, sample should be refetched as its
   * quantity will have changed.
   */
  updateBecauseRecordsChanged(recordIds: Set<GlobalId>) {
    if (this.subSamples.some((ss) => recordIds.has(getSavedGlobalId(ss)))) {
      void this.fetchAdditionalInfo();
    }
  }

  /*
   * For the sample to be valid, all fields that are mandatory must
   * have content
   */
  get enforceMandatoryFields(): boolean {
    return true;
  }

  get usableInLoM(): boolean {
    return true;
  }

  get beingCreatedInContainer(): boolean {
    return Array.isArray(this.newSampleSubSampleTargetLocations);
  }

  get inContainerParams(): SampleInContainerParams {
    if (!this.newSampleSubSamplesCount)
      throw new Error("Subsamples count must be known.");
    if (
      !(
        this.newSampleSubSampleTargetLocations &&
        this.newSampleSubSampleTargetLocations.length > 0
      )
    )
      throw new Error("Target locations must be known.");
    const firstLocation = this.newSampleSubSampleTargetLocations[0];
    return {
      newSampleSubSampleTargetLocations:
        this.newSampleSubSamplesCount > 1
          ? Array(this.newSampleSubSamplesCount).fill(firstLocation)
          : this.newSampleSubSampleTargetLocations,
    };
  }

  get createOptions(): $ReadOnlyArray<CreateOption> {
    return [
      {
        label: "Subsamples, by creating new ones",
        explanation: "Additional subsamples will be created with the specified quantity.",
        parameters: [{
          label: "Number of new subsamples",
          explanation: "Between 1 and 100.",
          state: this.createOptionsParametersState.newSubsamplesCount,
          validState: () => true,
        }, {
          label: "Quantity per subsample",
          explanation: "The starting quantity for each new subsample. The sample's total quantity will increase after creation of the new subsamples.",
          state: this.createOptionsParametersState.newSubsamplesQuantity,
          validState: () => true,
        }],
        onReset: () => {
          this.createOptionsParametersState.newSubsamplesCount.count = 1;
          this.createOptionsParametersState.newSubsamplesQuantity.quantity = 1;
        },
        onSubmit: () => {
          if (!this.quantity) throw new Error("Don't know what the sample's current quantity is");
          const unitId = this.quantity.unitId;
          return getRootStore().searchStore.search.createNewSubsamples({
            sample: this,
            numberOfNewSubsamples: this.createOptionsParametersState.newSubsamplesCount.count,
            quantityPerSubsample: {
              numericValue: this.createOptionsParametersState.newSubsamplesQuantity.quantity,
              unitId,
            },
          });
        },
      },
      {
        label: "Subsamples, by splitting the existing subsample",
        explanation: this.subSamples.length === 1 ? "Subsamples will be created by dividing the existing subsample quantity amongst them." : "Cannot split a sample with more than one subsample; open the create dialog from a subsample instead.",
        disabled: this.subSamples.length > 1,
        parameters: [{
          label: "Number of new subsamples",
          explanation: "The total number of subsamples wanted, including the source (between 2 and 100)",
          state: this.createOptionsParametersState.split,
          validState: () => this.createOptionsParametersState.split.copies >= 2 && this.createOptionsParametersState.split.copies <= 100,
        }],
        onReset: () => {
          this.createOptionsParametersState.split.copies = 2;
        },
        onSubmit: () => {
          if (this.subSamples.length !== 1) throw new Error("Can only split samples when there is one subsample");
          return getRootStore().searchStore.search.splitRecord(
            this.createOptionsParametersState.split.copies,
            this.subSamples[0],
          );
        },
      },
      {
        label: "Template",
        explanation: "Create a template from this sample, to easily create similar samples.",
        parameters: [{
          label: "Name",
          explanation: "A name for the new template. At least two characters.",
          state: this.createOptionsParametersState.name,
          validState: () => this.createOptionsParametersState.name.value.length > 2,
        },{
          label: "Field default values",
          explanation: "All of the sample fields will be included in the template. Select which fields should also retain their current value as a default field value.",
          state: this.createOptionsParametersState.fields,
          validState: () => true,
        }],
        onReset: () => {
          this.createOptionsParametersState.name.value = "";
          this.createOptionsParametersState.fields.copyFieldContent = [
              ...this.fields.map(f => ({
                id: f.id,
                name: f.name,
                content: f.renderContentAsString,
                hasContent: f.hasContent,
                selected: false
              })),
              ...this.extraFields.map(e => ({
                id: e.id,
                name: e.name,
                content: e.content,
                hasContent: e.hasContent,
                selected: false
              }))
            ];
        },
        onSubmit: () => {
          return getRootStore().searchStore.search.createTemplateFromSample(
            this.createOptionsParametersState.name.value,
            this,
            new RsSet(this.createOptionsParametersState.fields.copyFieldContent.filter(({ selected }) => selected).map(({ id }) => id)),
          );
        },
      }
    ];
  }
}

type BatchSampleEditableFields = ResultCollectionEditableFields &
  $Diff<SampleEditableFields, {| name: mixed |}>;

/*
 * This is a wrapper class around a set of Samples, making it easier to perform
 * batch operations e.g. editing.
 */
export class SampleCollection
  extends ResultCollection<SampleModel>
  implements HasEditableFields<BatchSampleEditableFields>
{
  constructor(samples: RsSet<SampleModel>) {
    super(samples);
    makeObservable(this, {
      setFieldsDirty: override,
      fieldValues: override,
    });
  }

  get allSameTemperatures(): boolean {
    const allTemperaturesUnspecified =
      this.records.every((s) => !s.storageTempMin) &&
      this.records.every((s) => !s.storageTempMax);
    const setOfTemperatures = new RsSet(
      this.records.map((s) => ({
        min: s.storageTempMin,
        max: s.storageTempMax,
      }))
    );
    return (
      !allTemperaturesUnspecified &&
      setOfTemperatures.map(({ min }) => min?.numericValue).size === 1 &&
      setOfTemperatures.map(({ min }) => min?.unitId).size === 1 &&
      setOfTemperatures.map(({ max }) => max?.numericValue).size === 1 &&
      setOfTemperatures.map(({ max }) => max?.unitId).size === 1
    );
  }

  get fieldValues(): BatchSampleEditableFields {
    const currentSources = new RsSet(this.records.map((r) => r.sampleSource));
    const currentExpiryDates = new RsSet(this.records.map((r) => r.expiryDate));
    const allSameTemperatures = this.allSameTemperatures;

    /*
     * Note that these two sets may contain multiple objects modelling the same
     * temperature range as sets define quality using `===`.
     */
    const currentStorageTemperatureMin = new RsSet(
      this.records.map((r) => r.storageTempMin)
    );
    const currentStorageTemperatureMax = new RsSet(
      this.records.map((r) => r.storageTempMax)
    );

    return {
      ...super.fieldValues,
      sampleSource: currentSources.first ?? "",
      expiryDate: currentExpiryDates.first ?? null,
      storageTempMin: allSameTemperatures
        ? currentStorageTemperatureMin.first
        : null,
      storageTempMax: allSameTemperatures
        ? currentStorageTemperatureMax.first
        : null,

      // Not supported when batch editing
      quantity: null,
      subSampleAlias: this.records.first.subSampleAlias,
    };
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof BatchSampleEditableFields]: ?string} {
    const currentSources = new RsSet(this.records.map((r) => r.sampleSource));
    const currentExpiryDates = new RsSet(this.records.map((r) => r.expiryDate));
    const allTemperaturesUnspecified =
      this.records.every((s) => !s.storageTempMin) &&
      this.records.every((s) => !s.storageTempMax);
    const allSameTemperatures = this.allSameTemperatures;

    const temperatureLabel = match<void, ?string>([
      [() => !allSameTemperatures, "Varies"],
      [() => allTemperaturesUnspecified, "Unspecified"],
      [() => true, null],
    ])();
    return {
      ...super.noValueLabel,
      sampleSource: currentSources.size === 1 ? null : "Varies",
      expiryDate: currentExpiryDates.size === 1 ? null : "Varies",
      quantity: null,
      storageTempMin: temperatureLabel,
      storageTempMax: temperatureLabel,
      subSampleAlias: null,
    };
  }

  setFieldsDirty(newFieldValues: any): void {
    super.setFieldsDirty(newFieldValues);
  }

  setFieldEditable(fieldName: string, value: boolean): void {
    for (const sample of this.records) {
      sample.setFieldEditable(fieldName, value);

      /*
       * If the temperatures are different or some are unspecified then when
       * the user starts editing, set all to ambient.
       */
      if (fieldName === "storageTempMin" && !this.fieldValues.storageTempMin) {
        sample.setFieldsDirty({
          storageTempMin: { numericValue: 15, unitId: CELSIUS },
        });
      }
      if (fieldName === "storageTempMax" && !this.fieldValues.storageTempMax) {
        sample.setFieldsDirty({
          storageTempMax: { numericValue: 30, unitId: CELSIUS },
        });
      }
    }
  }
}
