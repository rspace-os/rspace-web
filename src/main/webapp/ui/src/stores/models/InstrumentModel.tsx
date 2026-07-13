import { action, makeObservable, observable, override, runInAction } from "mobx";
import type React from "react";
import i18n from "@/modules/common/i18n";
import type { _LINK } from "@/util/types";
import InstrumentHeader from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/InstrumentHeader";
import RsSet from "../../util/set";
import type { BarcodeAttrs } from "../definitions/Barcode";
import { type GlobalId, type Id, inventoryRecordTypeLabels } from "../definitions/BaseRecord";
import type { Container } from "../definitions/Container";
import type { HasEditableFields, HasUneditableFields } from "../definitions/Editable";
import type { ExtraFieldAttrs } from "../definitions/ExtraField";
import type { Factory } from "../definitions/Factory";
import type { Field } from "../definitions/Field";
import type { HasLocationEditableFields, HasLocationUneditableFields } from "../definitions/HasLocation";
import type { IdentifierAttrs } from "../definitions/Identifier";
import type { Instrument } from "../definitions/Instrument";
import type { Action, CreateOption, RecordType } from "../definitions/InventoryRecord";
import type { PersonAttrs } from "../definitions/Person";
import type { RecordDetails } from "../definitions/Record";
import type { CoreFetcherArgs } from "../definitions/Search";
import type { AdjustableTableRowOptions } from "../definitions/Tables";
import getRootStore from "../stores/getRootStore";
import type { AttachmentJson } from "./AttachmentModel";
import type { ContainerAttrs } from "./ContainerModel";
import ExtraFieldModel from "./ExtraFieldModel";
import FieldModel, { type FieldModelAttrs } from "./FieldModel";
import { HasLocationMixin } from "./HasLocation";
import type InstrumentTemplateModel from "./InstrumentTemplateModel";
import InventoryBaseRecord, {
  defaultVisibleResultFields,
  type InventoryBaseRecordEditableFields,
  type InventoryBaseRecordUneditableFields,
  RESULT_FIELDS,
} from "./InventoryBaseRecord";

type InstrumentEditableFields = HasLocationEditableFields & InventoryBaseRecordEditableFields;

type InstrumentUneditableFields = HasLocationUneditableFields & InventoryBaseRecordUneditableFields;

export type InstrumentAttrs = {
  id: Id;
  type: string;
  globalId: GlobalId | null;
  name?: string;
  templateId?: Id | null;
  description?: string;
  permittedActions: Array<Action>;
  tags: string | null;
  iconId?: string;
  newBase64Image?: string;
  image?: string;
  parentContainers: Array<ContainerAttrs>;
  parentLocation: Location | null;
  lastMoveDate: string | null;
  lastNonWorkbenchParent: ContainerAttrs | null;
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
  templateVersion?: number | null;
  version?: number | null;
  historicalVersion?: boolean;
  _links: Array<_LINK>;
} & Record<string, unknown>;

const FIELDS = new Set([...RESULT_FIELDS].concat(["fields", "template"]));
const defaultVisibleFields = new Set([...FIELDS, ...defaultVisibleResultFields]);
const defaultEditableFields = new Set<string>();

export default class InstrumentModel
  extends HasLocationMixin(InventoryBaseRecord)
  implements Instrument, HasEditableFields<InstrumentEditableFields>, HasUneditableFields<InstrumentUneditableFields>
{
  // @ts-expect-error parentContainers is initialised by populateFromJson
  parentContainers: Array<import("./ContainerModel").default>;

  fields: Array<Field> = [];

  template: InstrumentTemplateModel | null = null;

  templateId: Id | null = null;

  templateVersion: number | null = null;

  // @ts-expect-error createOptionsParametersState is initialised by populateFromJson
  createOptionsParametersState: {
    name: { key: "name"; value: string };
    fields: {
      key: "fields";
      copyFieldContent: ReadonlyArray<{
        id: Id;
        name: string;
        content: string;
        hasContent: boolean;
        selected: boolean;
      }>;
    };
  };

  constructor(factory: Factory, params: InstrumentAttrs) {
    super(factory, params);
    makeObservable(this, {
      parentContainers: observable,
      immediateParentContainer: observable,
      createOptionsParametersState: observable,
      fields: observable,
      template: observable,
      templateId: observable,
      templateVersion: observable,
      setTemplate: action,
      overrideFields: action,
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

    if (this.recordType === "instrument") {
      this.populateFromJson(factory, params, {});
      this.templateId = params.templateId ?? null;
      this.templateVersion = params.templateVersion ?? null;
    }
  }

  get recordType(): RecordType {
    return "instrument";
  }

  get cardTypeLabel(): string {
    return inventoryRecordTypeLabels.instrument;
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.instrument;
  }

  get iconName(): string {
    return "instrument";
  }

  get illustration(): React.ReactNode {
    return <InstrumentHeader />;
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return { resultType: "INSTRUMENT" };
  }

  get recordDetails(): RecordDetails {
    return Object.assign(
      { ...super.recordDetails },
      {
        location: this,
      },
    );
  }

  populateFromJson(factory: Factory, passedParams: object, defaultParams: object = {}): void {
    super.populateFromJson(factory, passedParams, defaultParams);
    const params = { ...defaultParams, ...passedParams } as InstrumentAttrs;
    const [firstParent] = params.parentContainers ?? [];
    this.immediateParentContainer = firstParent
      ? (factory.newRecord(firstParent as Record<string, unknown> & { globalId: GlobalId }) as Container)
      : null;
    const fieldAttrs = (params.fields ?? []) as Array<FieldModelAttrs>;
    this.fields = fieldAttrs.map((f) => new FieldModel(f, this));
    if (typeof params.templateId !== "undefined") {
      this.templateId = params.templateId ?? null;
    }
    if (typeof params.templateVersion !== "undefined") {
      this.templateVersion = params.templateVersion ?? null;
    }
    this.createOptionsParametersState = {
      name: observable({ key: "name" as const, value: "" }),
      fields: observable({
        key: "fields" as const,
        copyFieldContent: [
          ...this.fields.map((f) => ({
            id: (f as FieldModel).id,
            name: f.name,
            content: f.renderContentAsString,
            hasContent: f.hasContent,
            selected: false,
          })),
          ...this.extraFields.map((ef) => ({
            id: ef.id,
            name: ef.name,
            content: ef.content,
            hasContent: ef.hasContent,
            selected: false,
          })),
        ],
      }),
    };
  }

  get paramsForBackend(): Record<string, unknown> {
    const params = { ...super.paramsForBackend };
    if (this.templateId) {
      params.templateId = this.templateId;
    }
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

  async fetchAdditionalInfo(silent: boolean = false): Promise<void> {
    await super.fetchAdditionalInfo(silent);
    if (this.templateId) {
      const templateId = this.templateId;
      const template = await getRootStore().searchStore.getInstrumentTemplate(
        templateId,
        this.templateVersion,
        this.factory.newFactory(),
      );
      runInAction(() => {
        this.template = template;
      });
    }
    getRootStore().trackingStore.trackEvent("InventoryRecordAccessed", {
      type: this.recordType,
    });
  }

  get supportsBatchEditing(): boolean {
    return false;
  }

  get fieldNamesInUse(): Array<string> {
    return [...super.fieldNamesInUse, ...this.fields.filter((f) => f.name).map((f) => f.name)];
  }

  overrideFields(fields: Array<Field>): void {
    this.setAttributes({
      fields: fields.map((f) => {
        const fm = f as FieldModel;
        return new FieldModel(
          {
            name: fm.name,
            type: fm.type,
            content:
              fm.content instanceof Date
                ? fm.content.toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                    hour12: false,
                  })
                : fm.content != null
                  ? String(fm.content)
                  : null,
            selectedOptions: [...(fm.selectedOptions ?? [])],
            definition: fm.options.length > 0 ? { options: fm.options.map((o) => o.value) } : null,
            columnIndex: fm.columnIndex,
            attachment: null,
            mandatory: fm.mandatory,
          },
          this,
        );
      }),
    });
  }

  async setTemplate(template: InstrumentTemplateModel | null): Promise<void> {
    if (template) await template.fetchAdditionalInfo();

    this.setAttributes({
      template,
      templateId: template?.id ?? null,
      templateVersion: template?.version ?? null,
    });

    if (!template) {
      this.setAttributes({ fields: [] });
      return;
    }

    if (!this.id) {
      this.overrideFields(template.fields);
      const userAddedFields = this.extraFields.filter((ef) => !ef.fromTemplate);
      const templateFields = template.extraFields
        .filter((ef) => !ef.deleteFieldRequest)
        .map((ef) => {
          const field = new ExtraFieldModel(
            {
              id: null,
              globalId: null,
              parentGlobalId: null,
              name: ef.name,
              type: ef.type.toLowerCase() as "text" | "number" | "link",
              content: ef.content ?? "",
              lastModified: null,
              newFieldRequest: true,
            },
            this,
          );
          field.fromTemplate = true;
          return field;
        });
      this.setAttributes({ extraFields: [...userAddedFields, ...templateFields] });
      if (!this.name) this.setAttributes({ name: template.name });
      if (!this.description) this.setAttributes({ description: template.description });
      this.setAttributes({
        tags: [...template.tags],
        image: template.image,
        newBase64Image: template.newBase64Image,
      });
    }
  }

  get fieldValues(): InstrumentEditableFields & InstrumentUneditableFields {
    return { ...super.fieldValues };
  }

  get noValueLabel(): {
    [key in keyof InstrumentEditableFields]: string | null;
  } & {
    [key in keyof InstrumentUneditableFields]: string | null;
  } {
    return { ...super.noValueLabel };
  }

  adjustableTableOptions(): AdjustableTableRowOptions<string> {
    return new Map([...super.adjustableTableOptions()]);
  }

  get usableInLoM(): boolean {
    return true;
  }

  get createOptions(): ReadonlyArray<CreateOption> {
    return [
      {
        label: i18n.t("inventory:instrument.createOptions.template.label"),
        explanation: i18n.t("inventory:instrument.createOptions.template.explanation"),
        parameters: [
          {
            label: i18n.t("inventory:createOptions.common.name"),
            explanation: i18n.t("inventory:createOptions.common.templateNameExplanation"),
            state: this.createOptionsParametersState.name,
            validState: () => this.createOptionsParametersState.name.value.length >= 2,
          },
          {
            label: i18n.t("inventory:createOptions.common.fieldDefaultValues"),
            explanation: i18n.t("inventory:instrument.createOptions.template.fieldDefaultsExplanation"),
            state: this.createOptionsParametersState.fields,
            validState: () => true,
          },
        ],
        onReset: () => {
          this.createOptionsParametersState.name.value = "";
          this.createOptionsParametersState.fields.copyFieldContent = [
            ...this.fields.map((f) => ({
              id: (f as FieldModel).id,
              name: f.name,
              content: f.renderContentAsString,
              hasContent: f.hasContent,
              selected: false,
            })),
            ...this.extraFields.map((ef) => ({
              id: ef.id,
              name: ef.name,
              content: ef.content,
              hasContent: ef.hasContent,
              selected: false,
            })),
          ];
        },
        onSubmit: () => {
          return getRootStore().searchStore.search.createInstrumentTemplateFromInstrument(
            this.createOptionsParametersState.name.value,
            this,
            new RsSet(
              this.createOptionsParametersState.fields.copyFieldContent
                .filter(({ selected }) => selected)
                .map(({ id }) => id),
            ),
          );
        },
      },
    ];
  }
}
