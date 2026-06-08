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
import React from "react";
import BiotechIcon from "@mui/icons-material/Biotech";
import { action, computed, makeObservable, observable, override } from "mobx";
import { type Instrument } from "../definitions/Instrument";
import getRootStore from "../stores/RootStore";
import { type CoreFetcherArgs } from "../definitions/Search";

type InstrumentEditableFields = HasLocationEditableFields &
  InventoryBaseRecordEditableFields;

type InstrumentUneditableFields = HasLocationUneditableFields &
  InventoryBaseRecordUneditableFields;

export type InstrumentAttrs = {
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

  constructor(factory: Factory, params: InstrumentAttrs) {
    super(factory, params);
    makeObservable(this, {
      parentContainers: observable,
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
      createOptions: override,
    });

    if (this.recordType === "instrument")
      this.populateFromJson(factory, params, {});
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
    return (
      <BiotechIcon sx={{ fontSize: "4rem", color: "text.secondary" }} />
    );
  }

  get showNewlyCreatedRecordSearchParams(): CoreFetcherArgs {
    return { resultType: "INSTRUMENT" };
  }

  get recordDetails(): RecordDetails {
    return Object.assign({ ...super.recordDetails }, {
      location: this,
    });
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
    return [];
  }
}
