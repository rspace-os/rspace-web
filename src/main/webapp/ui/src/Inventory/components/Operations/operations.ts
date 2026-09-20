// See DevDocs/adr/0011-inventory-operation-wizard.md for this module's design.
import type { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { faSun } from "@fortawesome/free-regular-svg-icons/faSun";
import { faArrowsRotate } from "@fortawesome/free-solid-svg-icons/faArrowsRotate";
import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { faEyeDropper } from "@fortawesome/free-solid-svg-icons/faEyeDropper";
import { faFlask } from "@fortawesome/free-solid-svg-icons/faFlask";
import { faSnowflake } from "@fortawesome/free-solid-svg-icons/faSnowflake";
import { faTrash } from "@fortawesome/free-solid-svg-icons/faTrash";
import type { ParseKeys } from "i18next";
import type { OperationFunctionName } from "./operationFunctions";
import type { AmountMode } from "./types";

export type InventoryKey = ParseKeys<"inventory">;

export type OperationInput = {
  key: string;
  type: "text" | "integer" | "quantity" | "temperature";
  labelKey: InventoryKey;
  required?: boolean;
  min?: number;
  max?: number;
  maxCelsius?: number;
  minCelsius?: number;
  default?: string | number;
};

export type ComputedArgSource = { parentSampleField: InventoryKey } | { constant: string | number };

export type ConfirmSummaryField =
  | "process"
  | "template"
  | "subsamples"
  | "amountTaken"
  | "storageTemp"
  | "linkBack"
  | "documentation"
  | "originEmptied"
  | "originFields";

type LinkSpec = { relationType: string; fieldNameKey: InventoryKey };
type OriginFieldSpec = { nameKey: InventoryKey; contentFrom: string };

type Computed = {
  fn: OperationFunctionName;
  into: string;
  args: Record<string, ComputedArgSource>;
};

type Effect = {
  nameFrom?: string;
  countFrom?: string;
  eachAmountFrom?: string;
  amountTakenFrom?: string;
  emptiesOrigin?: boolean;
  originFields?: ReadonlyArray<OriginFieldSpec>;
  processNameFrom?: string;
  storageTempFrom?: string;
  computed?: ReadonlyArray<Computed>;
  links: ReadonlyArray<LinkSpec>;
};

export type InventoryOperation = {
  key: string;
  labelKey: InventoryKey;
  descriptionKey?: InventoryKey;
  icon: IconDefinition;
  requiresMultiple?: boolean;
  defaultAmountMode?: AmountMode;
  noOutput?: boolean;
  steps?: ReadonlyArray<"details" | "template" | "amounts" | "documentation" | "confirm">;
  inputs: ReadonlyArray<OperationInput>;
  effect: Effect;
  confirmSummary: ReadonlyArray<ConfirmSummaryField>;
};

const sampleName = {
  key: "sampleName",
  type: "text",
  labelKey: "operations.fields.sampleName",
  required: true,
} as const satisfies OperationInput;

// The backend's own bound (ApiInventoryOperationRequests.Creating), repeated here so the wizard
// stops the user at the field rather than at Perform. The two are hand-kept in step; a drift
// shows as a 400.
const count = {
  key: "count",
  type: "integer",
  labelKey: "operations.fields.count",
  min: 1,
  max: 100,
  default: 1,
} as const satisfies OperationInput;

const eachAmount = {
  key: "eachAmount",
  type: "quantity",
  labelKey: "operations.fields.eachAmount",
  required: true,
} as const satisfies OperationInput;

const amountTaken = (labelKey: InventoryKey) =>
  ({
    key: "amountTaken",
    type: "quantity",
    labelKey,
  }) as const satisfies OperationInput;

/**
 * The seven operations, mirroring the Java classes in
 * {@code com.researchspace.service.inventory.operations}. Adding one means a class there, a typed
 * request body, an entry here and its i18n keys.
 */
export const operations: ReadonlyArray<InventoryOperation> = [
  {
    key: "aliquot",
    labelKey: "operations.aliquot.label",
    descriptionKey: "operations.aliquot.description",
    icon: faEyeDropper,
    inputs: [sampleName, count, eachAmount, amountTaken("operations.fields.amountTaken")],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      links: [{ relationType: "IsPartOf", fieldNameKey: "operations.aliquot.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "amountTaken", "linkBack", "documentation"],
  },
  {
    key: "passage",
    labelKey: "operations.passage.label",
    descriptionKey: "operations.passage.description",
    icon: faArrowsRotate,
    inputs: [sampleName, count, eachAmount],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      computed: [
        {
          fn: "increment",
          into: "passageNumber",
          args: {
            current: { parentSampleField: "operations.passage.numberField" },
            start: { constant: 1 },
          },
        },
      ],
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.passage.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "linkBack", "documentation"],
  },
  {
    key: "pool",
    labelKey: "operations.pool.label",
    descriptionKey: "operations.pool.description",
    icon: faFlask,
    requiresMultiple: true,
    defaultAmountMode: "all",
    inputs: [sampleName, count, eachAmount, amountTaken("operations.fields.amountTakenEach")],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      links: [{ relationType: "HasPart", fieldNameKey: "operations.pool.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "amountTaken", "linkBack", "documentation"],
  },
  {
    key: "derive",
    labelKey: "operations.derive.label",
    descriptionKey: "operations.derive.description",
    icon: faCodeBranch,
    inputs: [
      {
        key: "processName",
        type: "text",
        labelKey: "operations.fields.processName",
        required: true,
      },
      sampleName,
      count,
      eachAmount,
      amountTaken("operations.fields.amountTaken"),
    ],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      processNameFrom: "processName",
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.derive.linkFieldName" }],
    },
    confirmSummary: ["process", "template", "subsamples", "amountTaken", "linkBack", "documentation"],
  },
  {
    key: "cryopreserve",
    labelKey: "operations.cryopreserve.label",
    descriptionKey: "operations.cryopreserve.description",
    icon: faSnowflake,
    inputs: [
      sampleName,
      count,
      eachAmount,
      amountTaken("operations.fields.amountTaken"),
      { key: "cryomedium", type: "text", labelKey: "operations.fields.cryomedium" },
      {
        key: "storageTemp",
        type: "temperature",
        labelKey: "operations.fields.storageTemp",
        required: true,
        maxCelsius: -18,
      },
    ],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      storageTempFrom: "storageTemp",
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.cryopreserve.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "amountTaken", "storageTemp", "linkBack", "documentation"],
  },
  {
    key: "revive",
    labelKey: "operations.revive.label",
    descriptionKey: "operations.revive.description",
    icon: faSun,
    inputs: [
      sampleName,
      count,
      eachAmount,
      amountTaken("operations.fields.amountTaken"),
      {
        key: "storageTemp",
        type: "temperature",
        labelKey: "operations.fields.storageTemp",
        minCelsius: 4,
        maxCelsius: 120,
        default: 4,
      },
    ],
    effect: {
      nameFrom: "sampleName",
      countFrom: "count",
      eachAmountFrom: "eachAmount",
      amountTakenFrom: "amountTaken",
      storageTempFrom: "storageTemp",
      links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.revive.linkFieldName" }],
    },
    confirmSummary: ["template", "subsamples", "amountTaken", "storageTemp", "linkBack", "documentation"],
  },
  {
    key: "destroy",
    labelKey: "operations.destroy.label",
    descriptionKey: "operations.destroy.description",
    icon: faTrash,
    noOutput: true,
    steps: ["confirm"],
    inputs: [],
    effect: {
      emptiesOrigin: true,
      computed: [{ fn: "today", into: "disposedDate", args: {} }],
      links: [],
      originFields: [{ nameKey: "operations.destroy.disposedField", contentFrom: "disposedDate" }],
    },
    confirmSummary: ["originEmptied", "originFields"],
  },
];

export function amountKeysFor(operation: InventoryOperation): ReadonlySet<string> {
  const { countFrom, eachAmountFrom, amountTakenFrom } = operation.effect;
  return new Set([countFrom, eachAmountFrom, amountTakenFrom].filter((k): k is string => Boolean(k)));
}

export function usesAmountModes(operation: InventoryOperation): boolean {
  return Boolean(operation.requiresMultiple) && operation.effect.amountTakenFrom !== undefined;
}

export function resolveDefaultAmountMode(operation: InventoryOperation): AmountMode {
  return usesAmountModes(operation) ? (operation.defaultAmountMode ?? "same") : "same";
}

export type OperationAvailability = { enabled: boolean; reasonKey?: InventoryKey };

/**
 * The backend rejects a request with more than 100 origins (the Pool body's own @Size), so a
 * larger selection must not be able to launch a multi-origin operation.
 */
export const MAX_ORIGINS = 100;

export function operationAvailability(
  operation: InventoryOperation,
  selectionCount: number,
  allSameCategory: boolean,
): OperationAvailability {
  if (operation.requiresMultiple) {
    if (selectionCount < 2) return { enabled: false, reasonKey: "operations.picker.needsMultiple" };
    if (selectionCount > MAX_ORIGINS) return { enabled: false, reasonKey: "operations.picker.tooManySelected" };
    if (!allSameCategory) return { enabled: false, reasonKey: "operations.picker.sameCategory" };
    return { enabled: true };
  }
  if (selectionCount !== 1) return { enabled: false, reasonKey: "operations.picker.singleOnly" };
  return { enabled: true };
}

export function resolveProcessName(operation: InventoryOperation, values: Record<string, unknown>): string {
  const from = operation.effect.processNameFrom;
  if (!from) return operation.key;
  const raw = values[from];
  return typeof raw === "string" ? raw.trim() : "";
}
