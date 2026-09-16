// See DevDocs/adr/0007-operation-wizard-decisions.md for this module's design.
import * as v from "valibot";
import { type OperationFunctionName, operationFunctions } from "./operationFunctions";
import type { AmountMode } from "./types";

const InputSchema = v.object({
  key: v.string(),
  type: v.picklist(["text", "integer", "quantity", "temperature"]),
  labelKey: v.string(),
  required: v.optional(v.boolean()),
  min: v.optional(v.number()),
  // valibot silently drops any field not declared here, so an omitted bound vanishes without a parse error.
  max: v.optional(v.number()),
  maxCelsius: v.optional(v.number()),
  minCelsius: v.optional(v.number()),
  default: v.optional(v.union([v.string(), v.number()])),
});

const LinkSpecSchema = v.object({
  relationType: v.string(),
  fieldNameKey: v.string(),
});

const TextFieldSpecSchema = v.object({
  nameKey: v.string(),
  contentFrom: v.string(),
});

// Subsample custom fields support only text and number (no native date type), so a date value is
// stored as a text field holding an ISO date.
const OriginFieldSpecSchema = v.object({
  nameKey: v.string(),
  contentFrom: v.string(),
  type: v.optional(v.picklist(["text", "number"])),
});

const ArgSourceSchema = v.union([
  v.object({ parentSampleField: v.string() }),
  v.object({ constant: v.union([v.string(), v.number()]) }),
  v.object({ input: v.string() }),
]);

// Evaluated in array order, so a later entry may read an earlier one's `into` via an `input` arg.
// Validated against the function registry at load (see assertComputedValuesValid).
const ComputedSchema = v.object({
  fn: v.string(),
  into: v.string(),
  args: v.record(v.string(), ArgSourceSchema),
});

const EffectSchema = v.object({
  // Omitted by a terminal operation that creates nothing (noOutput); every producing operation sets them.
  nameFrom: v.optional(v.string()),
  countFrom: v.optional(v.string()),
  eachAmountFrom: v.optional(v.string()),
  amountTakenFrom: v.optional(v.string()),
  // When true, empty every origin (the amount taken is its own full current quantity). Mutually
  // exclusive with amountTakenFrom in practice.
  emptiesOrigin: v.optional(v.boolean()),
  originFields: v.optional(v.array(OriginFieldSpecSchema)),
  processNameFrom: v.optional(v.string()),
  storageTempFrom: v.optional(v.string()),
  computed: v.optional(v.array(ComputedSchema)),
  links: v.array(LinkSpecSchema),
  textFields: v.optional(v.array(TextFieldSpecSchema)),
});

const ConfirmSummaryFieldSchema = v.picklist([
  "process",
  "template",
  "subsamples",
  "amountTaken",
  "storageTemp",
  "linkBack",
  "documentation",
  "originEmptied",
  "originFields",
]);

// steps is optional; when omitted the wizard falls back to its own default sequence.
const StepSchema = v.picklist(["details", "template", "amounts", "documentation", "confirm"]);

const OperationSchema = v.object({
  key: v.string(),
  labelKey: v.string(),
  descriptionKey: v.optional(v.string()),
  // Must match an icon statically imported by the picker's registry (needed for tree-shaking); an
  // unmatched key renders no icon.
  iconKey: v.optional(v.string()),
  requiresMultiple: v.optional(v.boolean()),
  takeAmountPerSubsample: v.optional(v.boolean()),
  defaultAmountMode: v.optional(v.picklist(["same", "all", "perSubsample"])),
  noOutput: v.optional(v.boolean()),
  documentationStep: v.boolean(),
  steps: v.optional(v.array(StepSchema)),
  inputs: v.array(InputSchema),
  effect: EffectSchema,
  confirmSummary: v.optional(v.array(ConfirmSummaryFieldSchema)),
});

export type OperationInputConfig = v.InferOutput<typeof InputSchema>;
export type ComputedArgSource = v.InferOutput<typeof ArgSourceSchema>;
export type ConfirmSummaryField = v.InferOutput<typeof ConfirmSummaryFieldSchema>;
export type InventoryOperation = v.InferOutput<typeof OperationSchema>;

// Throws on any authoring mistake in the config, so a bad config fails at load rather than mid-wizard.
export function parseOperationsConfig(raw: unknown): Array<InventoryOperation> {
  const operations = v.parse(v.array(OperationSchema), raw);
  assertComputedValuesValid(operations);
  assertEffectReferencesValid(operations);
  return operations;
}

// Throws at config load, not at submit, so an authoring mistake fails fast.
function assertComputedValuesValid(ops: Array<InventoryOperation>): void {
  for (const op of ops) {
    for (const computed of op.effect.computed ?? []) {
      const def = operationFunctions[computed.fn as OperationFunctionName];
      if (!def) {
        throw new Error(
          `operations_config.json: operation "${op.key}" references unknown operation function "${computed.fn}"`,
        );
      }
      const bound = Object.keys(computed.args);
      for (const name of bound) {
        if (!def.params.includes(name)) {
          throw new Error(
            `operations_config.json: operation function "${computed.fn}" has no parameter "${name}" (operation "${op.key}")`,
          );
        }
      }
      for (const param of def.params) {
        if (!bound.includes(param)) {
          throw new Error(
            `operations_config.json: operation function "${computed.fn}" requires argument "${param}" (operation "${op.key}")`,
          );
        }
      }
    }
  }
}

// Every effect source key must resolve to a declared input or a computed `into`; otherwise a config
// typo silently produces empty content or a malformed quantity at submit instead of failing at load.
export function assertEffectReferencesValid(ops: Array<InventoryOperation>): void {
  for (const op of ops) {
    const available = new Set<string>(op.inputs.map((i) => i.key));
    for (const computed of op.effect.computed ?? []) available.add(computed.into);

    const check = (ref: string | undefined, where: string) => {
      if (ref !== undefined && !available.has(ref)) {
        throw new Error(`operations_config.json: operation "${op.key}" ${where} references unknown input "${ref}"`);
      }
    };

    const { effect } = op;
    check(effect.nameFrom, "effect.nameFrom");
    check(effect.countFrom, "effect.countFrom");
    check(effect.eachAmountFrom, "effect.eachAmountFrom");
    check(effect.amountTakenFrom, "effect.amountTakenFrom");
    check(effect.storageTempFrom, "effect.storageTempFrom");
    check(effect.processNameFrom, "effect.processNameFrom");
    for (const field of effect.textFields ?? []) check(field.contentFrom, "textField.contentFrom");
    for (const field of effect.originFields ?? []) check(field.contentFrom, "originField.contentFrom");
    // A computed argument sourced from another input ({ input: "key" }) must name a real input or a
    // computed `into`; a constant or parentSampleField arg has nothing to resolve against here.
    for (const computed of effect.computed ?? []) {
      for (const [param, source] of Object.entries(computed.args)) {
        if ("input" in source) check(source.input, `computed "${computed.fn}" arg "${param}"`);
      }
    }
  }
}

// Derived once so the wizard and the details step share exactly the same set of amount-step keys;
// computing it twice risks the two disagreeing about which step an input belongs to.
export function amountKeysFor(operation: InventoryOperation): ReadonlySet<string> {
  const { countFrom, eachAmountFrom, amountTakenFrom } = operation.effect;
  return new Set([countFrom, eachAmountFrom, amountTakenFrom].filter((k): k is string => Boolean(k)));
}

// `takeAmountPerSubsample` defaults to true for a multi-origin operation that takes an amount, so an
// unset config value still enables the per-origin modes; single-origin operations are always false.
export function usesAmountModes(operation: InventoryOperation): boolean {
  return (
    Boolean(operation.requiresMultiple) &&
    operation.effect.amountTakenFrom !== undefined &&
    (operation.takeAmountPerSubsample ?? true)
  );
}

// Always "same" for an operation that does not use amount modes, so a stray config value can never
// empty a single-origin operation's origin.
export function resolveDefaultAmountMode(operation: InventoryOperation): AmountMode {
  return usesAmountModes(operation) ? (operation.defaultAmountMode ?? "same") : "same";
}

export type OperationAvailability = { enabled: boolean; reasonKey?: string };

/**
 * The backend rejects a request with more than 100 origins (InventoryOperationPostValidator's
 * MAX_ORIGINS), so a larger selection must not be able to launch a multi-origin operation: it
 * would complete the whole wizard flow and only fail at Perform.
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
