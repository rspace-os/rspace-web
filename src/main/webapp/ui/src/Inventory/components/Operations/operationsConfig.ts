// See DevDocs/adr/0007-operation-wizard-decisions.md for this module's design.
import type { ParseKeys } from "i18next";
import type { OperationFunctionName } from "./operationFunctions";
import type { AmountMode } from "./types";

/** An `inventory:` catalog key, so a mistyped label fails at compile time. */
export type InventoryKey = ParseKeys<"inventory">;

export type OperationInputConfig = {
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

export type ComputedArgSource = { parentSampleField: InventoryKey } | { constant: string | number } | { input: string };

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
// Subsample custom fields support only text and number (no native date type), so a date value is
// stored as a text field holding an ISO date.
type OriginFieldSpec = { nameKey: InventoryKey; contentFrom: string; type?: "text" | "number" };

// Evaluated in array order, so a later entry may read an earlier one's `into` via an `input` arg.
type Computed = {
  // The registry's own key type: the load-time check that used to catch a typo is gone, so this
  // is what stops one reaching applyComputedValues as an undefined lookup at render.
  fn: OperationFunctionName;
  into: string;
  args: Record<string, ComputedArgSource>;
};

type Effect = {
  // Omitted by a terminal operation that creates nothing (noOutput); every producing operation
  // sets them.
  nameFrom?: string;
  countFrom?: string;
  eachAmountFrom?: string;
  amountTakenFrom?: string;
  // When true, empty every origin. Mutually exclusive with amountTakenFrom.
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
  // Must match an icon statically imported by the picker's registry (needed for tree-shaking); an
  // unmatched key renders no icon.
  iconKey?: string;
  requiresMultiple?: boolean;
  defaultAmountMode?: AmountMode;
  noOutput?: boolean;
  documentationStep: boolean;
  // Optional; when omitted the wizard falls back to its own default sequence.
  steps?: ReadonlyArray<"details" | "template" | "amounts" | "documentation" | "confirm">;
  inputs: ReadonlyArray<OperationInputConfig>;
  effect: Effect;
  confirmSummary?: ReadonlyArray<ConfirmSummaryField>;
};

// Frozen: each of these is aliased into six of the seven operations, so a mutation of one
// operation's input config would otherwise reach all of them.
const sampleName = {
  key: "sampleName",
  type: "text",
  labelKey: "operations.fields.sampleName",
  required: true,
} as const satisfies OperationInputConfig;

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
} as const satisfies OperationInputConfig;

const eachAmount = {
  key: "eachAmount",
  type: "quantity",
  labelKey: "operations.fields.eachAmount",
  required: true,
} as const satisfies OperationInputConfig;

const amountTaken = (labelKey: InventoryKey) =>
  ({
    key: "amountTaken",
    type: "quantity",
    labelKey,
  }) as const satisfies OperationInputConfig;

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
    iconKey: "eye-dropper",
    documentationStep: true,
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
    iconKey: "arrows-rotate",
    documentationStep: true,
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
    iconKey: "flask",
    requiresMultiple: true,
    defaultAmountMode: "all",
    documentationStep: true,
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
    iconKey: "code-branch",
    documentationStep: true,
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
    iconKey: "snowflake",
    documentationStep: true,
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
    iconKey: "sun",
    documentationStep: true,
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
    iconKey: "trash",
    noOutput: true,
    documentationStep: false,
    steps: ["confirm"],
    inputs: [],
    effect: {
      emptiesOrigin: true,
      computed: [{ fn: "today", into: "disposedDate", args: {} }],
      links: [],
      originFields: [
        {
          nameKey: "operations.destroy.disposedField",
          contentFrom: "disposedDate",
          type: "text",
        },
      ],
    },
    confirmSummary: ["originEmptied", "originFields"],
  },
];

// Derived once so the wizard and the details step share exactly the same set of amount-step keys;
// computing it twice risks the two disagreeing about which step an input belongs to.
export function amountKeysFor(operation: InventoryOperation): ReadonlySet<string> {
  const { countFrom, eachAmountFrom, amountTakenFrom } = operation.effect;
  return new Set([countFrom, eachAmountFrom, amountTakenFrom].filter((k): k is string => Boolean(k)));
}

// The per-origin amount modes belong to a multi-origin operation that takes an amount; a
// single-origin operation always uses the one shared "same" mode.
export function usesAmountModes(operation: InventoryOperation): boolean {
  return Boolean(operation.requiresMultiple) && operation.effect.amountTakenFrom !== undefined;
}

// Always "same" for an operation that does not use amount modes, so a stray value can never
// empty a single-origin operation's origin.
export function resolveDefaultAmountMode(operation: InventoryOperation): AmountMode {
  return usesAmountModes(operation) ? (operation.defaultAmountMode ?? "same") : "same";
}

export type OperationAvailability = { enabled: boolean; reasonKey?: InventoryKey };

/**
 * The backend rejects a request with more than 100 origins (the Pool body's own @Size), so a
 * larger selection must not be able to launch a multi-origin operation: it would complete the
 * whole wizard flow and only fail at Perform.
 */
export const MAX_ORIGINS = 100;

// Every operation is always shown in the picker; this only decides its enabled state and reason
// (shown greyed-out), never whether it appears.
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
