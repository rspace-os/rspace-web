import { type _LINK } from "../../util/types";
import {
  type Id,
  type GlobalId,
  inventoryRecordTypeLabels,
} from "../definitions/BaseRecord";
import { type RecordDetails } from "../definitions/Record";
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
import { HasLocationMixin } from "./HasLocation";
import {
  type HasLocationEditableFields,
  type HasLocationUneditableFields,
} from "../definitions/HasLocation";
import { type ContainerAttrs } from "./ContainerModel";
import { type Container } from "../definitions/Container";
import React from "react";
import InstrumentHeader from "../../assets/graphics/RecordTypeGraphics/HeaderIllustrations/InstrumentHeader";
import { action, computed, makeObservable, observable, override } from "mobx";
import { type Instrument } from "../definitions/Instrument";
import getRootStore from "../stores/RootStore";
import { type CoreFetcherArgs } from "../definitions/Search";
import RsSet from "../../util/set";

type InstrumentEditableFields = HasLocationEditableFields &
  InventoryBaseRecordEditableFields;

type InstrumentUneditableFields = HasLocationUneditableFields &
  InventoryBaseRecordUneditableFields;

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
  _links: Array<_LINK>;
} & Record<string, unknown>;

const FIELDS = new Set([...RESULT_FIELDS]);
const defaultVisibleFields = new Set([...FIELDS, ...defaultVisibleResultFields]);
const defaultEditableFields = new Set([...defaultEditableResultFields]);

export default class InstrumentModel
  extends HasLocationMixin(InventoryBaseRecord)
  implements
    Instrument,
    HasEditableFields<InstrumentEditableFields>,
    HasUneditableFields<InstrumentUneditableFields>
{
  // @ts-expect-error parentContainers is initialised by populateFromJson
  parentContainers: Array<import("./ContainerModel").default>;

  templateId: Id | null = null;

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
    });

    if (this.recordType === "instrument") {
      this.populateFromJson(factory, params, {});
      this.templateId = params.templateId ?? null;
      this.createOptionsParametersState = {
        name: observable({ key: "name" as const, value: "" }),
        fields: observable({
          key: "fields" as const,
          copyFieldContent: this.extraFields.map((e) => ({
            id: e.id,
            name: e.name,
            content: e.content,
            hasContent: e.hasContent,
            selected: false,
          })),
        }),
      };
    }
  }

  get recordType(): RecordType {
    return "instrument";
  }

  get cardTypeLabel(): string {
    return "Instrument";
  }

  get recordTypeLabel(): string {
    return inventoryRecordTypeLabels.instrument;
  }

  get iconName(): string {
    return "instrument";
  }

  get illustration(): React.ReactNode {
    return <InstrumentHeader/>;
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return { resultType: "INSTRUMENT" };
  }

  get recordDetails(): RecordDetails {
    return Object.assign({ ...super.recordDetails }, {
      location: this,
    });
  }

  populateFromJson(
    factory: Factory,
    passedParams: object,
    defaultParams: object = {},
  ): void {
    super.populateFromJson(factory, passedParams, defaultParams);
    const params = { ...defaultParams, ...passedParams } as InstrumentAttrs;
    const [firstParent] = params.parentContainers ?? [];
    this.immediateParentContainer = firstParent
      ? (factory.newRecord(
          firstParent as Record<string, unknown> & { globalId: GlobalId },
        ) as Container)
      : null;
  }

  get paramsForBackend(): Record<string, unknown> {
    const params = { ...super.paramsForBackend };
    if (this.templateId) {
      params.templateId = this.templateId;
      /*
       * When creating a new instrument from a template the backend populates
       * extra fields from the template automatically. Sending those same fields
       * in extraFields as well causes a "duplicated field name" error. Only
       * user-added fields (newFieldRequest: true) should be included; the
       * template fields will be created by the backend via templateId.
       */
      if (!this.id && Array.isArray(params.extraFields)) {
        params.extraFields = (
          params.extraFields as Array<{ newFieldRequest: boolean }>
        ).filter((ef) => ef.newFieldRequest === true);
      }
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
    getRootStore().trackingStore.trackEvent("InventoryRecordAccessed", {
      type: this.recordType,
    });
  }

  get supportsBatchEditing(): boolean {
    return false;
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
        label: "Instrument Template",
        explanation:
          "Create an instrument template from this instrument, to easily create similar instruments.",
        parameters: [
          {
            label: "Name",
            explanation: "A name for the new template. At least two characters.",
            state: this.createOptionsParametersState.name,
            validState: () =>
              this.createOptionsParametersState.name.value.length >= 2,
          },
          {
            label: "Field default values",
            explanation:
              "All of the instrument's custom fields will be included in the template. Select which fields should also retain their current value as a default field value.",
            state: this.createOptionsParametersState.fields,
            validState: () => true,
          },
        ],
        onReset: () => {
          this.createOptionsParametersState.name.value = "";
          this.createOptionsParametersState.fields.copyFieldContent = [
            ...this.extraFields.map((e) => ({
              id: e.id,
              name: e.name,
              content: e.content,
              hasContent: e.hasContent,
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
