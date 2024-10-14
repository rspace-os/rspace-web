// @flow

import { mkAlert } from "../contexts/Alert";
import { type _LINK } from "../../common/ApiServiceBase";
import ApiService from "../../common/InvApiService";
import { classMixin } from "../../util/Util";
import RsSet from "../../util/set";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";
import { type Id, type GlobalId } from "../definitions/BaseRecord";
import { type RecordDetails } from "../definitions/Record";
import {
  type RecordType,
  type Action,
  type InventoryRecord,
  type CreateOption,
  inventoryRecordTypeLabels,
} from "../definitions/InventoryRecord";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/RootStore";
import { type AttachmentJson } from "./AttachmentModel";
import ContainerModel, { type ContainerAttrs } from "./ContainerModel";
import { type ExtraFieldAttrs } from "../definitions/ExtraField";
import RecordWithQuantity, {
  type RecordWithQuantityEditableFields,
  type RecordWithQuantityUneditableFields,
  type Quantity,
  getValue,
  getUnitId,
} from "./RecordWithQuantity";
import { Movable } from "./Movable";
import { type PersonId, type PersonAttrs } from "../definitions/Person";
import { type Factory } from "../definitions/Factory";
import ResultCollection, {
  type ResultCollectionEditableFields,
} from "./ResultCollection";
import SampleModel, { type SampleAttrs } from "./SampleModel";
import {
  action,
  computed,
  observable,
  override,
  makeObservable,
  runInAction,
} from "mobx";
import { type Alias, type Sample } from "../definitions/Sample";
import {
  RESULT_FIELDS,
  defaultVisibleResultFields,
  defaultEditableResultFields,
} from "./Result";
import React, { type Node } from "react";
import SubSampleIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/SubSample";
import { type SubSample } from "../definitions/SubSample";
import { type BarcodeAttrs } from "../definitions/Barcode";
import type { IdentifierAttrs } from "../definitions/Identifier";
import { pick } from "../../util/unsafeUtils";
import {
  IsInvalid,
  IsValid,
  type ValidationResult,
} from "../../components/ValidatingSubmitButton";

type SubSampleEditableFields = {
  ...RecordWithQuantityEditableFields,
};

type SubSampleUneditableFields = {
  ...RecordWithQuantityUneditableFields,
  sample: Sample,
  location: InventoryRecord,
  ...
};

export type Note = {|
  id?: number,
  createdBy: {
    firstName: string,
    lastName: string,
    id: PersonId,
  },
  created: string,
  content: string,
|};

export type SubSampleAttrs = {|
  id: Id, // can't be null, because created on the server first
  type: string,
  globalId: ?GlobalId,
  name?: string,
  quantity: Quantity,
  extraFields?: Array<ExtraFieldAttrs>,
  description?: string,
  permittedActions: Array<Action>,
  tags: ?string,
  notes?: Array<Note>,
  iconId?: string,
  newBase64Image?: string,
  image?: string,
  parentContainers: Array<ContainerAttrs>,
  lastNonWorkbenchParent: ?string,
  lastMoveDate: ?Date,
  sample?: SampleAttrs | SampleModel,
  owner: ?PersonAttrs,
  created: ?string,
  lastModified: ?string,
  modifiedByFullName: ?string,
  deleted: boolean,
  attachments: Array<AttachmentJson>,
  barcodes: Array<BarcodeAttrs>,
  identifiers: Array<IdentifierAttrs>,
  _links: Array<_LINK>,
|};

const FIELDS = new Set([...RESULT_FIELDS, "quantity", "notes"]);
const defaultVisibleFields = new Set([
  ...FIELDS,
  ...defaultVisibleResultFields,
]);
const defaultEditableFields = new Set([
  ...defaultEditableResultFields,
  "notes",
]);

export default class SubSampleModel
  extends RecordWithQuantity
  implements
    SubSample,
    HasEditableFields<SubSampleEditableFields>,
    HasUneditableFields<SubSampleUneditableFields>
{
  notes: Array<Note> = [];
  sample: SampleModel;
  parentContainers: Array<ContainerModel>;
  parentLocation: ?Location;
  allParentContainers: ?() => Array<ContainerModel>;
  rootParentContainer: ?ContainerModel;
  immediateParentContainer: ?ContainerModel;
  lastNonWorkbenchParent: ?string;
  lastMoveDate: ?Date;
  createOptionsParametersState: {|
    split: {| key: "split",  copies: number |}
  |};

  constructor(factory: Factory, params: SubSampleAttrs) {
    super(factory);
    makeObservable(this, {
      notes: observable,
      sample: observable,
      parentLocation: observable,
      parentContainers: observable,
      allParentContainers: observable,
      rootParentContainer: observable,
      immediateParentContainer: observable,
      lastNonWorkbenchParent: observable,
      lastMoveDate: observable,
      createOptionsParametersState: observable,
      createNote: action,
      paramsForBackend: override,
      updateFieldsState: override,
      cardTypeLabel: override,
      recordTypeLabel: override,
      iconName: override,
      recordType: override,
      fetchAdditionalInfo: override,
      recordDetails: override,
      supportsBatchEditing: override,
      fieldNamesInUse: override,
      alias: computed,
    });

    if (this.recordType === "subSample")
      this.populateFromJson(factory, params, {});

    this.createOptionsParametersState = { split: { key: "split", copies: 2 }};
  }

  populateFromJson(
    factory: Factory,
    params: any,
    defaultParams: ?any = {}
  ): void {
    super.populateFromJson(factory, params, defaultParams);
    params = { ...defaultParams, ...params };
    this.notes = params.notes ?? [];
    this.parentLocation = params.parentLocation;
    this.parentContainers = params.parentContainers ?? [];
    this.lastNonWorkbenchParent = params.lastNonWorkbenchParent;
    this.lastMoveDate = params.lastMoveDate;
    // $FlowExpectedError[prop-missing] Defined on the mixin
    this.initializeMovableMixin(factory);

    /*
     * When a SampleModel is instantiating its SubSamples it passes `this`, an
     * instance of SampleModel. When instantiating from direct API calls,
     * params.sample is simply a JSON object.
     * In 'public view' case, sample is not null, but returned with limited info.
     */
    if (params.sample instanceof SampleModel) {
      this.sample = params.sample;
    } else {
      const newSample = factory.newRecord(params.sample);
      if (newSample instanceof SampleModel) {
        this.sample = newSample;
      } else {
        throw new Error("Could not instantiate valid SampleModel");
      }
    }
  }

  get recordType(): RecordType {
    return "subSample";
  }

  get alias(): Alias {
    return this.sample.subSampleAlias;
  }

  /*
   * A plain object that can be encoded to JSON for submission to the backend
   * when API calls are made. It is vital that there are no cyclical memory
   * references in the object returned by this computed properties. See
   * ./__tests__/SubSampleModel/paramsForBackend.test.js for the tests that assert
   * that this object can be serialised; any changes should be reflected there.
   */
  get paramsForBackend(): any {
    const params = { ...super.paramsForBackend };
    if (this.currentlyEditableFields.has("quantity"))
      params.quantity = this.quantity;
    return params;
  }

  updateFieldsState() {
    this.currentlyVisibleFields = defaultVisibleFields;
    this.currentlyEditableFields = defaultEditableFields;

    switch (this.state) {
      case "edit":
        this.setEditable(FIELDS, true);
        this.setEditableExtraFields(this.extraFields, true);
        break;
      case "preview":
        this.setEditable(FIELDS, false);
        this.setEditable(new Set(["notes"]), true);
        break;
      case "create":
        this.setEditable(FIELDS, true);
      // subsamples do not have a create state
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

  async createNote(params: {| content: string |}): Promise<void> {
    if (this.state === "edit") {
      const newNote: Note = {
        ...params,
        createdBy: pick(
          "firstName",
          "lastName",
          "id"
        )(getRootStore().peopleStore.currentUser),
        created: new Date().toISOString(),
      };
      this.setAttributesDirty({
        notes: [...this.notes, newNote],
      });
      return;
    }

    if (!this.id) throw new Error("id is required.");
    try {
      const { data } = await ApiService.post<
        typeof params,
        { notes: Array<Note> }
      >(`subSamples/${this.id}/notes`, params);

      runInAction(() => {
        this.notes = data.notes;
      });
      this.unsetDirtyFlag();

      getRootStore().uiStore.addAlert(
        mkAlert({
          message: "Note successfully created.",
          variant: "success",
        })
      );
    } catch (error) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          title: "Creating note failed.",
          message:
            error.response?.data.message ?? error.message ?? "Unknown reason.",
          variant: "error",
          duration: 8000,
        })
      );
      console.error("Could not create note.", error);
    }
  }

  get cardTypeLabel(): string {
    return "Subsample";
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.subsample;
  }

  get fieldNamesInUse(): Array<string> {
    return [...super.fieldNamesInUse, ...["Quantity", "Sample", "Notes"]];
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    const options = new Map([
      ...super.adjustableTableOptions(),
      // $FlowExpectedError[prop-missing] Defined on the mixin
      ...this.adjustableTableOptions_movable(),
    ]);
    if (this.readAccessLevel === "public") {
      options.set("Sample", () => ({ renderOption: "node", data: null }));
    } else {
      options.set("Sample", () => ({
        renderOption: "name",
        data: this.sample,
      }));
    }
    return options;
  }

  async update(refresh: boolean = true): Promise<void> {
    if (!this.id) throw new Error("id is required.");
    const id = this.id;
    this.setLoading(true);

    const [{ status: updateStatus, value: updateValue }, ...noteResults] =
      await Promise.allSettled([
        super.update(refresh),
        ...this.notes
          .filter((n) => !n.id)
          .map((n) => ApiService.post<void, void>(`subSamples/${id}/notes`, n)),
      ]);

    if (updateStatus === "fulfilled" && updateValue) {
      getRootStore().trackingStore.trackEvent("InventoryRecordEdited", {
        type: this.recordType,
      });
    }

    if (noteResults.some(({ status }) => status === "rejected")) {
      getRootStore().uiStore.addAlert(
        mkAlert({
          message: "Could not save the new note(s).",
          variant: "error",
          details: noteResults
            .filter((n) => n.status === "rejected")
            .map(
              ({
                reason: {
                  // $FlowExpectedError[incompatible-use] rejected promises have a reason property
                  config: { data: jsonData },
                  // $FlowExpectedError[incompatible-use] rejected promises have a reason property
                  response: {
                    data: {
                      errors: [error],
                    },
                  },
                },
              }) => ({
                title: JSON.parse(jsonData).content,
                help: error,
                variant: "error",
              })
            ),
        })
      );
    }
  }

  async fetchAdditionalInfo(silent: boolean = false) {
    await super.fetchAdditionalInfo(silent);
    getRootStore().trackingStore.trackEvent("InventoryRecordAccessed", {
      type: this.recordType,
    });
  }

  get iconName(): string {
    return "subsample";
  }

  get illustration(): Node {
    return <SubSampleIllustration />;
  }

  get recordDetails(): RecordDetails {
    return Object.assign(
      { ...super.recordDetails },
      {
        quantity: this.quantityLabel,
        sample: this.sample,
        location: this,
      }
    );
  }

  /*
   * The current value of the editable fields, as required by the interface
   * `HasEditableFields` and `HasUneditableFields`.
   */
  get fieldValues(): SubSampleEditableFields & SubSampleUneditableFields {
    return {
      ...super.fieldValues,
      sample: this.sample,
      location: this,
    };
  }

  get supportsBatchEditing(): boolean {
    return true;
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof SubSampleEditableFields]: ?string} & {[key in keyof SubSampleUneditableFields]: ?string} {
    return {
      ...super.noValueLabel,
      quantity: null,
      sample: null,
      location: null,
    };
  }

  validate(): ValidationResult {
    return super.validate().flatMap(() => {
      if (this.quantity?.numericValue !== "") return IsValid();
      return IsInvalid("Quantity must be set");
    });
  }

  get usableInLoM(): boolean {
    return true;
  }

  get createOptions(): $ReadOnlyArray<CreateOption> {
    return [
      {
        label: "Subsample, by splitting",
        explanation: "New subsamples will be created by diving the quantity of this subsample equally amongst them.",
        parameters: [{
          label: "Number of new subsamples",
          explanation: "The total number of subsamples wanted, including the source (Between 2 and 100)",
          state: this.createOptionsParametersState.split,
          validState: () => this.createOptionsParametersState.split.copies >= 2 && this.createOptionsParametersState.split.copies <= 100,
        }],
        onReset: () => {
          this.createOptionsParametersState.split.copies = 2;
        },
        onSubmit: () => {
          return getRootStore().searchStore.search.splitRecord(
            this.createOptionsParametersState.split.copies,
            this
          );
        },
      }
    ];
  }
}

// copying mixin methods, add type any for flow
classMixin(SubSampleModel, Movable);

type BatchSubSampleEditableFields = ResultCollectionEditableFields &
  $Diff<SubSampleEditableFields, {| name: mixed |}>;

/*
 * This is a wrapper class around a set of SubSamples, making it easier to
 * perform batch operations e.g. editing.
 */
export class SubSampleCollection
  extends ResultCollection<SubSampleModel>
  implements HasEditableFields<BatchSubSampleEditableFields>
{
  constructor(subsamples: RsSet<SubSampleModel>) {
    super(subsamples);
    makeObservable(this, {
      quantityCategory: computed,
      sameQuantityUnits: computed,
      setFieldsDirty: override,
      fieldValues: override,
    });
  }

  get sameQuantityUnits(): boolean {
    return new RsSet(this.records.map((r) => getUnitId(r.quantity))).size === 1;
  }

  get fieldValues(): BatchSubSampleEditableFields {
    const currentQuanities = new RsSet(
      this.records.map((r) => getValue(r.quantity))
    );

    return {
      ...super.fieldValues,
      quantity:
        currentQuanities.size === 1
          ? this.records.first.quantity
          : this.isFieldEditable("quantity")
          ? {
              numericValue: 0,
              unitId: this.records.first.quantity?.unitId ?? 3,
            }
          : null,
    };
  }

  //eslint-disable-next-line no-unused-vars
  get noValueLabel(): {[key in keyof BatchSubSampleEditableFields]: ?string} {
    const currentQuanities = new RsSet(
      this.records.map((r) => getValue(r.quantity))
    );
    return {
      ...super.noValueLabel,
      quantity: currentQuanities.size === 1 ? null : "Varies",
    };
  }

  get quantityCategory(): string {
    return this.records.first.quantityCategory;
  }

  setFieldsDirty(newFieldValues: any): void {
    super.setFieldsDirty(newFieldValues);
  }

  setFieldEditable(fieldName: string, value: boolean): void {
    for (const subsample of this.records) {
      subsample.setFieldEditable(fieldName, value);
    }
  }
}
