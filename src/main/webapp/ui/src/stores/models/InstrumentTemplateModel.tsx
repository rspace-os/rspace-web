import { action, makeObservable, observable, override } from "mobx";
import type React from "react";
import i18n from "@/modules/common/i18n";
import type { _LINK } from "@/util/types";
import TemplateIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/Template";
import type { BarcodeAttrs } from "../definitions/Barcode";
import { type GlobalId, type Id, inventoryRecordTypeLabels } from "../definitions/BaseRecord";
import type { HasEditableFields, HasUneditableFields } from "../definitions/Editable";
import type { ExtraFieldAttrs } from "../definitions/ExtraField";
import type { Factory } from "../definitions/Factory";
import type { Field } from "../definitions/Field";
import type { IdentifierAttrs } from "../definitions/Identifier";
import type { InstrumentTemplate } from "../definitions/InstrumentTemplate";
import type { Action, CreateOption, RecordType } from "../definitions/InventoryRecord";
import type { PersonAttrs } from "../definitions/Person";
import type { CoreFetcherArgs } from "../definitions/Search";
import type { AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/getRootStore";
import type { AttachmentJson } from "./AttachmentModel";
import FieldModel, { type FieldModelAttrs } from "./FieldModel";
import InventoryBaseRecord, {
  defaultVisibleResultFields,
  type InventoryBaseRecordEditableFields,
  type InventoryBaseRecordUneditableFields,
  RESULT_FIELDS,
} from "./InventoryBaseRecord";
import Search from "./Search";

type InstrumentTemplateEditableFields = InventoryBaseRecordEditableFields;

type InstrumentTemplateUneditableFields = InventoryBaseRecordUneditableFields;

export type InstrumentTemplateAttrs = {
  id: Id;
  type: string;
  globalId: GlobalId | null;
  name?: string;
  description?: string;
  permittedActions: Array<Action>;
  tags: string | null;
  version?: number;
  iconId?: string;
  newBase64Image?: string;
  image?: string;
  owner: PersonAttrs | null;
  created: string | null;
  lastModified: string | null;
  modifiedByFullName: string | null;
  deleted: boolean;
  attachments: Array<AttachmentJson>;
  barcodes: Array<BarcodeAttrs>;
  identifiers: Array<IdentifierAttrs>;
  extraFields?: Array<ExtraFieldAttrs>;
  fields?: Array<FieldModelAttrs>;
  _links: Array<_LINK>;
} & Record<string, unknown>;

const FIELDS = new Set([...RESULT_FIELDS].concat(["fields"]));
const defaultVisibleFields = new Set([...FIELDS, ...defaultVisibleResultFields]);
const defaultEditableFields = new Set<string>();

export default class InstrumentTemplateModel
  extends InventoryBaseRecord
  implements
    InstrumentTemplate,
    HasEditableFields<InstrumentTemplateEditableFields>,
    HasUneditableFields<InstrumentTemplateUneditableFields>
{
  fields: Array<Field> = [];
  search: Search;

  declare version: number;

  constructor(factory: Factory, params: InstrumentTemplateAttrs) {
    super(factory, params);
    makeObservable(this, {
      fields: observable,
      search: observable,
      addField: action,
      removeCustomField: action,
      moveField: action,
      paramsForBackend: override,
      populateFromJson: override,
      updateFieldsState: override,
      cardTypeLabel: override,
      recordTypeLabel: override,
      iconName: override,
      recordType: override,
      fetchAdditionalInfo: override,
      recordDetails: override,
      supportsBatchEditing: override,
      showNewlyCreatedRecordSearchParams: override,
      fieldNamesInUse: override,
    });

    if (this.recordType === "instrumentTemplate") this.populateFromJson(factory, params, {});

    this.search = new Search({
      fetcherParams: {
        parentGlobalId: this.globalId,
        resultType: "INSTRUMENT",
      },
      uiConfig: {
        allowedSearchModules: new Set(["TYPE", "STATUS", "OWNER", "TAG"]),
        allowedTypeFilters: new Set(["INSTRUMENT"]),
        hideContentsOfChip: true,
      },
      factory: this.factory.newFactory(),
    });
  }

  get recordType(): RecordType {
    return "instrumentTemplate";
  }

  get cardTypeLabel(): string {
    return inventoryRecordTypeLabels.instrumentTemplate;
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.instrumentTemplate;
  }

  get iconName(): string {
    return "instrumentTemplate";
  }

  get illustration(): React.ReactNode {
    return <TemplateIllustration />;
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return { resultType: "INSTRUMENT_TEMPLATE" };
  }

  populateFromJson(factory: Factory, passedParams: object, defaultParams: object = {}): void {
    super.populateFromJson(factory, passedParams, defaultParams);
    const params = { ...defaultParams, ...passedParams } as InstrumentTemplateAttrs;
    const fieldAttrs = (params.fields ?? []) as Array<FieldModelAttrs>;
    this.fields = fieldAttrs.map((f) => new FieldModel(f, this));
    this.version = params.version ?? 1;
  }

  addField(fieldParams: FieldModelAttrs): void {
    this.setAttributesDirty({
      fields: [...this.fields, new FieldModel(fieldParams, this)],
    });
  }

  removeCustomField(id: Id, index: number): void {
    if (!this.id || !id) {
      this.fields.splice(index, 1);
    } else {
      const field = this.fields.find((f) => f.id === id);
      field?.setAttributesDirty({ deleteFieldRequest: true });
    }
  }

  moveField(field: Field, newIndex: number): void {
    if (newIndex < -1 || newIndex >= this.fields.length) throw new Error(`Invalid new index: ${newIndex}`);
    if (newIndex === -1) newIndex = this.fields.length - 1;
    const oldIndex = this.fields.indexOf(field);
    if (oldIndex === -1) throw new Error("Could not find field in this instrument template.");
    const without = [...this.fields.slice(0, oldIndex), ...this.fields.slice(oldIndex + 1)];
    const withAgain = [...without.slice(0, newIndex), field, ...without.slice(newIndex)];
    withAgain.forEach((f, i) => {
      (f as FieldModel).columnIndex = i + 1;
    });
    this.setAttributesDirty({ fields: withAgain });
  }

  get paramsForBackend(): Record<string, unknown> {
    const params = { ...super.paramsForBackend };
    if (this.currentlyEditableFields.has("fields")) {
      params.fields = this.fields.map((f) => (f as FieldModel).paramsForBackend);
    }
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
        break;
      case "create":
        this.setEditable(FIELDS, true);
        this.setEditableExtraFields(this.extraFields, true);
        break;
    }
  }

  get fieldNamesInUse(): Array<string> {
    return [...super.fieldNamesInUse, ...this.fields.filter((f) => f.name).map((f) => f.name)];
  }

  async fetchAdditionalInfo(silent: boolean = false): Promise<void> {
    await super.fetchAdditionalInfo(silent);
    getRootStore().trackingStore.trackEvent("InventoryRecordAccessed", {
      type: this.recordType,
    });
  }

  get supportsBatchEditing(): boolean {
    return false;
  }

  get fieldValues(): InstrumentTemplateEditableFields & InstrumentTemplateUneditableFields {
    return { ...super.fieldValues };
  }

  get noValueLabel(): {
    [key in keyof InstrumentTemplateEditableFields]: string | null;
  } & {
    [key in keyof InstrumentTemplateUneditableFields]: string | null;
  } {
    return { ...super.noValueLabel };
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    return new Map([...super.adjustableTableOptions()]);
  }

  refreshAssociatedSearch() {
    if (this.id !== null) {
      void this.search.fetcher.performInitialSearch(null);
    }
  }

  get usableInLoM(): boolean {
    return false;
  }

  get createOptions(): ReadonlyArray<CreateOption> {
    return [
      {
        label: i18n.t("inventory:instrumentTemplate.createOptions.instrument.label"),
        explanation: i18n.t("inventory:instrumentTemplate.createOptions.instrument.explanation"),
        onReset: () => {},
        onSubmit: async () => {
          void getRootStore().searchStore.createNewInstrument({
            templateId: this.id,
          });
        },
      },
    ];
  }
}
