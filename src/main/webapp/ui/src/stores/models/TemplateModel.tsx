import ApiService from "../../common/InvApiService";
import { type URL as URLType } from "../../util/types";
import { doNotAwait, sleep } from "../../util/Util";
import {
  handleDetailedErrors,
  handleDetailedSuccesses,
} from "../../util/alerts";
import {
  type Id,
  type GlobalId,
  inventoryRecordTypeLabels,
} from "../definitions/BaseRecord";
import { type RecordDetails } from "../definitions/Record";
import {
  type RecordType,
  type InventoryRecord,
  type LockStatus,
  type CreateOption,
} from "../definitions/InventoryRecord";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import { mkAlert } from "../contexts/Alert";
import { type CoreFetcherArgs } from "../definitions/Search";
import FieldModel, { type FieldModelAttrs } from "./FieldModel";
import { type Factory } from "../definitions/Factory";
import SampleModel, {
  SAMPLE_FIELDS,
  defaultVisibleSampleFields,
  defaultEditableSampleFields,
  type SampleAttrs,
} from "./SampleModel";
import Search from "./Search";
import {
  action,
  observable,
  computed,
  override,
  makeObservable,
  runInAction,
} from "mobx";
import React from "react";
import { type Template } from "../definitions/Template";
import TemplateIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/Template";
import docLinks from "../../assets/DocLinks";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import type { Field } from "../definitions/Field";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";
import { getErrorMessage } from "../../util/error";

const mainSearch = () => getRootStore().searchStore.search;

export type TemplateAttrs = Omit<SampleAttrs, "quantity"> & {
  id: Id; // can't be null, because created on the server first
  iconId: number | null;
  defaultUnitId: number | null;
  historicalVersion: boolean;
  version: number;
  quantity: null;
};

const DEFAULT_TEMPLATE: TemplateAttrs = {
  id: null,
  type: "SAMPLE_TEMPLATE",
  globalId: null,
  name: "",
  permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"],
  templateId: null,
  templateVersion: null,
  subSampleAlias: { alias: "subsample", plural: "subsamples" },
  subSamplesCount: 0,
  subSamples: [],
  quantity: null,
  storageTempMin: { numericValue: 15, unitId: 8 },
  storageTempMax: { numericValue: 30, unitId: 8 },
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
  defaultUnitId: 3,
  version: 0,
  historicalVersion: false,
  barcodes: [],
  identifiers: [],
  sharingMode: "OWNER_GROUPS",
  sharedWith: [],
  _links: [],
};

const FIELDS = new Set([...SAMPLE_FIELDS, "defaultUnitId"]);
const defaultVisibleFields = new Set([
  ...FIELDS,
  ...defaultVisibleSampleFields,
]);
const defaultEditableFields = new Set([...defaultEditableSampleFields]);

export default class TemplateModel extends SampleModel implements Template {
  // @ts-expect-error Initialised by populateFromJson
  defaultUnitId: number;
  // @ts-expect-error Initialised by populateFromJson
  version: number;
  latest: Template | null = null;
  // @ts-expect-error Initialised by populateFromJson
  historicalVersion: boolean;
  icon: string | null = null;

  constructor(
    factory: Factory,
    params: TemplateAttrs = { ...DEFAULT_TEMPLATE }
  ) {
    super(factory, params as unknown as SampleAttrs);
    makeObservable(this, {
      defaultUnitId: observable,
      version: observable,
      latest: observable,
      historicalVersion: observable,
      icon: observable,
      addField: action,
      removeCustomField: action,
      getLatest: action,
      updateSamplesToLatest: action,
      moveField: action,
      updateFieldsState: override,
      fetchAdditionalInfo: override,
      recordTypeLabel: override,
      paramsForBackend: override,
      setEditing: override,
      update: override,
      permalinkURL: override,
      hasSubSamples: override,
      iconName: override,
      recordType: override,
      showNewlyCreatedRecordSearchParams: override,
      children: override,
      recordDetails: override,
      supportsBatchEditing: override,
      enforceMandatoryFields: override,
      fieldNamesInUse: override,
      globalIdOfLatest: computed,
      validSubSampleAlias: computed,
    });

    if (this.recordType === "sampleTemplate")
      this.populateFromJson(factory, params, {});

    this.search = new Search({
      fetcherParams: {
        parentGlobalId: this.globalIdOfLatest,
        resultType: "SAMPLE",
      },
      uiConfig: {
        allowedSearchModules: new Set(["TYPE", "STATUS", "OWNER", "TAG"]),
        allowedTypeFilters: new Set(["SAMPLE"]),
        hideContentsOfChip: true,
      },
      factory: this.factory.newFactory(),
    });

    if (this.iconId) this.fetchIcon();
  }

  populateFromJson(
    factory: Factory,
    params: object,
    defaultParams: object = {}
  ) {
    super.populateFromJson(factory, params, defaultParams);
    params = { ...defaultParams, ...params };
    // @ts-expect-error We assume that params has this property
    this.defaultUnitId = params.defaultUnitId ?? 3;
    // @ts-expect-error We assume that params has this property
    this.version = params.version;
    // @ts-expect-error We assume that params has this property
    this.historicalVersion = params.historicalVersion;
  }

  get recordType(): RecordType {
    return "sampleTemplate";
  }

  updateFieldsState() {
    this.currentlyVisibleFields = defaultVisibleFields;
    this.currentlyEditableFields = defaultEditableFields;

    switch (this.state) {
      case "edit":
        this.setEditable(FIELDS, true);
        this.setEditable(new Set(["template"]), false);
        this.setVisible(new Set(["template"]), false);
        this.setEditableExtraFields(this.extraFields, true);
        break;
      case "preview":
        this.setEditable(FIELDS, false);
        this.setVisible(FIELDS, true);
        break;
      case "create":
        this.setEditable(FIELDS, true);
    }
  }

  async fetchAdditionalInfo(): Promise<void> {
    if (!this.id) {
      /*
       * We silently return because `this` will be discarded shortly after we
       * have set `editing` to false and cleared the dirty flag (the caller),
       * so there is no need to fetch the latest state.
       */
      return;
    }
    if (this.fetchingAdditionalInfo) {
      await this.fetchingAdditionalInfo;
      return;
    }
    const id = this.id;
    this.setLoading(true);
    try {
      this.fetchingAdditionalInfo = ApiService.get<object>(
        "sampleTemplates",
        this.version ? `${id}/versions/${this.version}` : `${id}`
      );
      const { data } = await this.fetchingAdditionalInfo;
      this.fetchingAdditionalInfo = null;
      this.populateFromJson(this.factory.newFactory(), data);
      runInAction(() => {
        this.infoLoaded = true;
      });
      return;
    } catch (error) {
      console.error(error);
      throw error;
    } finally {
      this.setLoading(false);
    }
  }

  fetchIcon(): void {
    if (!this.id) throw new Error("id is required.");
    const id = this.id;
    if (!this.iconId) throw new Error("iconid is required");
    const iconId = this.iconId;

    void getRootStore()
      .imageStore.fetchImage(`/api/v1/forms/${id}/icon/${iconId}`)
      .then(
        action((x) => {
          this.icon = x;
        })
      );
  }

  addField(fieldParams: FieldModelAttrs) {
    this.setAttributesDirty({
      fields: [...this.fields, new FieldModel(fieldParams, this)],
    });
  }

  removeCustomField(id: Id, index: number, deleteFromSamples: boolean = false) {
    if (!this.id || !id) {
      this.fields.splice(index, 1);
    } else {
      const field = this.fields.find((f) => f.id === id);
      field?.setAttributesDirty({
        deleteFieldRequest: true,
        deleteFieldOnSampleUpdate: deleteFromSamples,
      });
    }
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.sampleTemplate;
  }

  get globalIdOfLatest(): string | null {
    if (!this.id) return null;
    return `IT${this.id}`;
  }

  getLatest(): void {
    const id = this.id;
    if (!id) throw new Error("id is required.");
    if (!this.latest) {
      if (this.historicalVersion) {
        void getRootStore()
          .searchStore.getTemplate(id, null, this.factory.newFactory())
          .then(
            action((latest) => {
              this.latest = latest;
            })
          );
      } else {
        this.latest = this;
      }
    }
  }

  /*
   * A plain object that can be encoded to JSON for submission to the backend
   * when API calls are made. It is vital that there are no cyclical memory
   * references in the object returned by this computed properties. See
   * ./__tests__/TemplateModel/paramsForBackend.test.js for the tests that assert
   * that this object can be serialised; any changes should be reflected there.
   */
  get paramsForBackend(): Record<string, unknown> {
    const params = super.paramsForBackend;
    if (this.currentlyEditableFields.has("defaultUnitId"))
      params.defaultUnitId = this.defaultUnitId;
    if (this.currentlyEditableFields.has("subSampleAlias"))
      params.subSampleAlias = this.subSampleAlias;
    return params;
  }

  async setEditing(
    value: boolean,
    refresh?: boolean,
    goToLatest?: boolean
  ): Promise<LockStatus> {
    refresh = refresh ?? true;
    goToLatest = goToLatest ?? true;
    if (value && goToLatest) {
      const latest = await this.setActiveResultToLatest();
      /*
       * Move this code to the macrotask queue of the event loop (promises
       * reside on the microtask queue) so that TinyMCE has a change to finish
       * rendering before we pull the rug from under it's feet and ask it to
       * render again.
       */
      await sleep(0);
      return latest.setEditing(true, refresh, false);
    }
    return super.setEditing(value, refresh);
  }

  // eslint-disable-next-line no-use-before-define
  async setActiveResultToLatest(): Promise<TemplateModel> {
    const id = this.id;
    if (!id) throw new Error("id is required.");
    // this.getLatest wont work because `this` thinks it is the latest
    const latest = await getRootStore().searchStore.getTemplate(
      id,
      null,
      this.factory.newFactory()
    );
    await mainSearch().setActiveResult(latest); // should not error because active result can't be modified after update
    mainSearch().replaceResult(latest);
    return latest;
  }

  async update(): Promise<void> {
    const oldVersion = this.version;
    await super.update(false);
    /*
     * Note, that because of the data returned by the PUT endpoint and
     * super.update's call to populateFromJson, after this point
     * this.version === latest.version.
     */
    const latest = await this.setActiveResultToLatest();

    /*
     * Ask to update existing samples to latest template if there are any.
     *
     * `this.search.fetcher.results.length` will be 0 as SamplesList is
     * in the middle of refreshing the search results to display in the table
     * at the bottom of the page. Unfortunately, that means we have to
     * independently make the same network request to guarantee that the data
     * is available.
     */
    if (this.id) {
      await this.search.fetcher.performInitialSearch(null);
      // User can only update samples they own
      const samplesToBeUpdated = this.search.fetcher.results.filter(
        // default to true so we don't miss any that could be the current user's
        (r) => r.owner?.isCurrentUser ?? true
      );
      if (this.version !== oldVersion && samplesToBeUpdated.length > 0) {
        const newToast = mkAlert({
          message: "Update existing samples?",
          variant: "notice",
          isInfinite: true,
          actionLabel: "yes",
          onActionClick: doNotAwait(() => this.updateSamplesToLatest()),
        });
        latest.addScopedToast(newToast);
        getRootStore().uiStore.addAlert(newToast);
      }
    }
  }

  async updateSamplesToLatest(): Promise<void> {
    if (!this.id) throw new Error("id is required.");
    const id = this.id;

    if (
      !(await getRootStore().uiStore.confirm(
        <>
          Update all samples to latest template version?
          <HelpLinkIcon
            link={docLinks.updateAllSamplesOfTemplate}
            title="Info on updating samples to latest template version."
            size="small"
          />
        </>,
        <>
          All of your samples created from this template will be updated to pick
          up the structural changes that have been made to the template since
          the sample was created or last updated, such as the addition, deletion
          and reordering of fields, and the change to available options in
          choice and radio fields.&nbsp;
          <strong>This action cannot be undone.</strong>
        </>,
        "Update all"
      ))
    )
      return;
    try {
      const { data } = await ApiService.post<{
        errorCount: number;
        results: Array<{
          error: { errors: Array<string> };
          record: Record<string, unknown> & { globalId: GlobalId };
        }>;
      }>(
        `sampleTemplates/${id.toString()}/actions/updateSamplesToLatestTemplateVersion`,
        {}
      );
      handleDetailedErrors(
        data.errorCount,
        data.results.map((response) => ({ response })),
        "update",
        () => this.updateSamplesToLatest(),
        ""
      );
      const factory = this.factory.newFactory();
      handleDetailedSuccesses(
        data.results
          .filter((r) => !r.error)
          .map((r) => {
            const newRecord = factory.newRecord(r.record);
            newRecord.populateFromJson(factory, r.record, null);
            return newRecord;
          }),
        "updated"
      );
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: "Updating samples to latest template version failed.",
          message: getErrorMessage(error, "Unknown reason"),
          variant: "error",
        })
      );
      console.error(
        "Could not update samples to latest template version.",
        error
      );
    }
  }

  contextMenuDisabled(): string | null {
    return (
      super.contextMenuDisabled() ??
      (this.historicalVersion
        ? "Cannot modify a historical version of a template."
        : null)
    );
  }

  get permalinkURL(): URLType | null {
    if (!this.id) return null;
    const id = this.id;

    if (this.historicalVersion)
      return `/inventory/${this.recordType.toLowerCase()}/${id}?version=${
        this.version
      }`;
    return `/inventory/${this.recordType.toLowerCase()}/${id}`;
  }

  get fieldNamesInUse(): Array<string> {
    return [
      ...super.fieldNamesInUse,
      ...["Subsample Alias", "Quantity Units", "Fields", "Samples"],
    ];
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    const options = super.adjustableTableOptions();
    options.delete("Subsamples Count");
    options.delete("Quantity");
    options.set("Version", () => ({
      renderOption: "node",
      data: this.version,
    }));
    return options;
  }

  get hasSubSamples(): boolean {
    return false;
  }

  moveField(field: Field, newIndex: number): void {
    if (newIndex < -1 || newIndex >= this.fields.length)
      throw new Error(`Invalid new index: ${newIndex}`);
    if (newIndex === -1) newIndex = this.fields.length - 1;

    const oldIndex = this.fields.indexOf(field);
    if (oldIndex === -1)
      throw new Error(
        `Could not find field with id '${
          field.id ?? "NEW FIELD"
        }' in this sample.`
      );

    const without = [
      ...this.fields.slice(0, oldIndex),
      ...this.fields.slice(oldIndex + 1),
    ];
    const withAgain = [
      ...without.slice(0, newIndex),
      field,
      ...without.slice(newIndex),
    ];
    withAgain.forEach((f, i) => {
      f.columnIndex = i + 1;
    });
    this.setAttributesDirty({ fields: withAgain });
  }

  get iconName(): string {
    return "template";
  }

  get illustration(): React.ReactNode {
    return <TemplateIllustration />;
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return {
      resultType: "TEMPLATE",
    };
  }

  get children(): Array<InventoryRecord> {
    /*
     * In Tree view, we want Templates to be a flat list. It certainly
     * shouldn't inherit from SampleModel and display it's subSamples (of which
     * Templates have a single dummy one that the user should not be aware of).
     */
    return [];
  }

  get recordDetails(): RecordDetails {
    const details = super.recordDetails;
    delete details.quantity;
    details.version = this.version;
    return details;
  }

  get validSubSampleAlias(): boolean {
    return Object.values(this.subSampleAlias).every(
      (v) =>
        typeof v === "string" &&
        v !== "custom" &&
        v !== "customs" &&
        v.length > 1 &&
        v.length <= 30
    );
  }

  validateQuantity(): ValidationResult {
    /*
     * Whilst Templates have a quantity becuase they extend Samples, which
     * implement HasQuantity, the quantity is not exposed to the user and
     * should not be asserted when checking whether the Template is in a valid
     * state.
     */
    return IsValid();
  }

  validate(): ValidationResult {
    return super.validate().flatMap(() => {
      if (this.validSubSampleAlias) return IsValid();
      return IsInvalid("Must have a valid alias");
    });
  }

  get supportsBatchEditing(): boolean {
    return false;
  }

  //eslint-disable-next-line no-unused-vars
  updateBecauseRecordsChanged(_recordIds: Set<GlobalId>) {
    /*
     * Whilst Sample has subsamples who when changed effect the Sample's
     * properties, Templates have no such data.
     */
  }

  /*
   * Providing a default value for a field that is mandatory is not
   * required for the template to be valid.
   */
  get enforceMandatoryFields(): boolean {
    return false;
  }

  get showBarcode(): boolean {
    return false;
  }

  get usableInLoM(): boolean {
    return false;
  }

  get createOptions(): ReadonlyArray<CreateOption> {
    return [
      {
        label: "Sample",
        explanation:
          "Tapping create will open the new sample form, with this template pre-populated.",
        onReset: () => {
          // nothing to reset
        },
        onSubmit: async () => {
          const newSample: SampleModel =
            await getRootStore().searchStore.createNewSample();
          await newSample.setTemplate(this);
        },
      },
    ];
  }
}
