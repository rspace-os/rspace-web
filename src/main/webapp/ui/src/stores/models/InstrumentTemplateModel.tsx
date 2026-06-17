import { type _LINK } from "../../util/types";
import {
  type Id,
  type GlobalId,
  inventoryRecordTypeLabels,
} from "../definitions/BaseRecord";
import {
  type RecordType,
  type Action,
  type CreateOption,
} from "../definitions/InventoryRecord";
import { type AdjustableTableRowOptions } from "../definitions/Tables";
import { type ExtraFieldAttrs } from "../definitions/ExtraField";
import { type PersonAttrs } from "../definitions/Person";
import { type Factory } from "../definitions/Factory";
import { type AttachmentJson } from "./AttachmentModel";
import { type BarcodeAttrs } from "../definitions/Barcode";
import type { IdentifierAttrs } from "../definitions/Identifier";
import InventoryBaseRecord, {
  RESULT_FIELDS,
  defaultVisibleResultFields,
  defaultEditableResultFields,
  type InventoryBaseRecordEditableFields,
  type InventoryBaseRecordUneditableFields,
} from "./InventoryBaseRecord";
import {
  type HasEditableFields,
  type HasUneditableFields,
} from "../definitions/Editable";
import { type InstrumentTemplate } from "../definitions/InstrumentTemplate";
import React from "react";
import TemplateIllustration from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/Template";
import { action, computed, makeObservable, observable, override } from "mobx";
import getRootStore from "../stores/RootStore";
import { type CoreFetcherArgs } from "../definitions/Search";

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
  _links: Array<_LINK>;
} & Record<string, unknown>;

const FIELDS = new Set([...RESULT_FIELDS]);
const defaultVisibleFields = new Set([...FIELDS, ...defaultVisibleResultFields]);
const defaultEditableFields = new Set([...defaultEditableResultFields]);

export default class InstrumentTemplateModel
  extends InventoryBaseRecord
  implements
    InstrumentTemplate,
    HasEditableFields<InstrumentTemplateEditableFields>,
    HasUneditableFields<InstrumentTemplateUneditableFields>
{
  constructor(factory: Factory, params: InstrumentTemplateAttrs) {
    super(factory, params);
    makeObservable(this, {
      paramsForBackend: override,
      updateFieldsState: override,
      cardTypeLabel: override,
      recordTypeLabel: override,
      iconName: override,
      recordType: override,
      fetchAdditionalInfo: override,
      recordDetails: override,
      supportsBatchEditing: override,
      showNewlyCreatedRecordSearchParams: override,
    });

    if (this.recordType === "instrumentTemplate")
      this.populateFromJson(factory, params, {});
  }

  get recordType(): RecordType {
    return "instrumentTemplate";
  }

  get cardTypeLabel(): string {
    return "Instrument Template";
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

  get paramsForBackend(): Record<string, unknown> {
    return { ...super.paramsForBackend };
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
        break;
    }
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

  get fieldValues(): InstrumentTemplateEditableFields &
    InstrumentTemplateUneditableFields {
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

  get usableInLoM(): boolean {
    return false;
  }

  get createOptions(): ReadonlyArray<CreateOption> {
    return [
      {
        label: "Instrument",
        explanation:
          "Create a new instrument based on this template.",
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
