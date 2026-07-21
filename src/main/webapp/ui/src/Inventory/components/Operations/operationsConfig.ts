/**
 * Loads and validates the declarative operation definitions. Operations are data, not code: adding
 * one is a new entry in operations_config.json, with no change here or in the backend (see adr/0001).
 * The valibot schema is the single source of truth for the config shape; the InventoryOperation type
 * is inferred from it, and the JSON is validated at module load so an authoring mistake fails fast.
 */
import * as v from "valibot";
import { type OperationFunctionName, operationFunctions } from "./operationFunctions";
import rawConfig from "./operations_config.json";

const InputSchema = v.object({
  key: v.string(),
  type: v.picklist(["text", "integer", "quantity", "temperature"]),
  labelKey: v.string(),
  required: v.optional(v.boolean()),
  min: v.optional(v.number()),
  // Upper bound (in Celsius) for a "temperature" input, e.g. cryopreserve must be stored at or below
  // this. Configurable per operation; when absent the temperature is unconstrained.
  maxCelsius: v.optional(v.number()),
  // Lower bound (in Celsius) for a "temperature" input, e.g. revive must be stored at or above this.
  // Configurable per operation; when absent there is no lower bound.
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

// A custom field the operation adds to each of its origin subsamples (as opposed to textFields, which
// go on the created sample). Its content comes from a named input - typically a computed value such as
// Destroy's "disposed" date. Inventory subsample custom fields support only text and number (no native
// date type; see ApiExtraField), so a date is stored as a text field holding an ISO date. Defaults to
// text when `type` is omitted.
const OriginFieldSpecSchema = v.object({
  nameKey: v.string(),
  contentFrom: v.string(),
  type: v.optional(v.picklist(["text", "number"])),
});

// An argument handed to an Operation function (adr/0006), sourced one of three ways: the content of a
// named field on the origin's parent sample (resolved in the user's locale via the i18n key), a
// literal constant, or the current value of another wizard input. The wizard resolves these at submit.
const ArgSourceSchema = v.union([
  v.object({ parentSampleField: v.string() }),
  v.object({ constant: v.union([v.string(), v.number()]) }),
  v.object({ input: v.string() }),
]);

// A computed value: apply the named Operation function to its bound args and write the single return
// value into input `into`, which the effect wiring (e.g. textFields) then consumes. Evaluated in array
// order, so a later entry may read an earlier one's `into` via an `input` arg. The function must exist
// in the registry and its params must match `args` (checked at load; see assertComputedValuesValid).
const ComputedSchema = v.object({
  fn: v.string(),
  into: v.string(),
  args: v.record(v.string(), ArgSourceSchema),
});

const EffectSchema = v.object({
  // The new-sample fields (name / subsample count / each amount) are omitted by a terminal operation
  // that creates nothing (noOutput, e.g. Destroy); every producing operation sets them.
  nameFrom: v.optional(v.string()),
  countFrom: v.optional(v.string()),
  eachAmountFrom: v.optional(v.string()),
  amountTakenFrom: v.optional(v.string()),
  // When true, empty every origin: the amount taken is the origin's own full current quantity, so its
  // volume ends at zero (Destroy). Mutually exclusive with amountTakenFrom in practice.
  emptiesOrigin: v.optional(v.boolean()),
  // Custom fields added to each origin subsample itself (not the created sample), e.g. Destroy's
  // "disposed" date. Content comes from a named input, usually a computed value (see OriginFieldSpec).
  originFields: v.optional(v.array(OriginFieldSpecSchema)),
  // input key holding the process name; when set, the operation's "remember" defaults are scoped
  // per process name (see processNames.ts) and the field becomes an autocomplete of saved names.
  processNameFrom: v.optional(v.string()),
  storageTempFrom: v.optional(v.string()),
  // Values computed at submit by applying an Operation function (adr/0006) to configured arguments,
  // each written into a named input the effect wiring then consumes (e.g. Passage's passage number).
  computed: v.optional(v.array(ComputedSchema)),
  links: v.array(LinkSpecSchema),
  textFields: v.optional(v.array(TextFieldSpecSchema)),
});

// The fields the confirmation summary can show, in the order configured per operation. Each maps to
// a renderer in OperationConfirmation; a field whose value is absent (e.g. documentation when none
// was linked) is skipped. Editing an operation's confirmSummary changes its summary, no code change.
const ConfirmSummaryFieldSchema = v.picklist([
  "process",
  "template",
  "subsamples",
  "amountTaken",
  "storageTemp",
  "linkBack",
  "documentation",
  // Terminal operations (noOutput, e.g. Destroy): the origin's volume is emptied, and the custom
  // field(s) added to the origin (e.g. the disposal date).
  "originEmptied",
  "originFields",
]);

// The wizard steps, in order. An operation may declare a `steps` subset (e.g. Destroy skips template
// and amounts); when omitted the wizard uses its default sequence (see OperationWizard).
const StepSchema = v.picklist(["details", "template", "amounts", "documentation", "confirm"]);

const OperationSchema = v.object({
  key: v.string(),
  labelKey: v.string(),
  descriptionKey: v.optional(v.string()),
  // Whether the operation consumes multiple origin subsamples (Pool); omitted/false = single-origin.
  // Drives which selection sizes enable it in the picker (see operationAvailability and adr/0007).
  requiresMultiple: v.optional(v.boolean()),
  // When true the operation produces no new sample/subsamples (it only acts on its origins, e.g.
  // Destroy). The wizard omits the new-sample effect wiring and the backend creates nothing.
  noOutput: v.optional(v.boolean()),
  documentationStep: v.boolean(),
  // The wizard steps to show, in order. Optional; when omitted the wizard uses its default sequence.
  // A terminal operation (Destroy) sets ["confirm"] to skip details, template and amounts.
  steps: v.optional(v.array(StepSchema)),
  inputs: v.array(InputSchema),
  effect: EffectSchema,
  // Which rows the confirmation summary shows, in order. Optional; when omitted a default set is used.
  confirmSummary: v.optional(v.array(ConfirmSummaryFieldSchema)),
});

export type OperationInputConfig = v.InferOutput<typeof InputSchema>;
export type OperationLinkSpec = v.InferOutput<typeof LinkSpecSchema>;
export type OperationOriginFieldSpec = v.InferOutput<typeof OriginFieldSpecSchema>;
export type OperationStep = v.InferOutput<typeof StepSchema>;
export type ComputedArgSource = v.InferOutput<typeof ArgSourceSchema>;
export type ComputedValueSpec = v.InferOutput<typeof ComputedSchema>;
export type ConfirmSummaryField = v.InferOutput<typeof ConfirmSummaryFieldSchema>;
export type InventoryOperation = v.InferOutput<typeof OperationSchema>;

export const operations: Array<InventoryOperation> = v.parse(v.array(OperationSchema), rawConfig);

/**
 * Fail fast, like the valibot parse above: every computed value must name an Operation function that
 * exists in the registry and bind exactly that function's declared parameters. An authoring mistake
 * throws at module load rather than at submit. See adr/0006.
 */
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

assertComputedValuesValid(operations);

/**
 * Fail fast (like the parse and the computed-values check above): every effect source key -
 * nameFrom, the amount/temperature sources, each text/origin field's contentFrom, and each computed
 * `{ input }` argument - must name either a declared input or a computed `into`, otherwise a config
 * typo silently produces empty content or a malformed quantity at submit instead of failing when the
 * config loads. See adr/0001.
 */
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

assertEffectReferencesValid(operations);

export type OperationAvailability = { enabled: boolean; reasonKey?: string };

/**
 * Whether an operation is enabled for the current subsample selection, and if not, the i18n key
 * explaining why (shown greyed-out in the picker; see adr/0007). A multi-origin operation (Pool)
 * needs two or more subsamples that share a measurement category; a single-origin operation needs
 * exactly one. Every operation is always shown - only its enabled state and reason change.
 */
export function operationAvailability(
  operation: InventoryOperation,
  selectionCount: number,
  allSameCategory: boolean,
): OperationAvailability {
  if (operation.requiresMultiple) {
    if (selectionCount < 2) return { enabled: false, reasonKey: "operations.picker.needsMultiple" };
    if (!allSameCategory) return { enabled: false, reasonKey: "operations.picker.sameCategory" };
    return { enabled: true };
  }
  if (selectionCount !== 1) return { enabled: false, reasonKey: "operations.picker.singleOnly" };
  return { enabled: true };
}

/**
 * Every operation has a process name (adr/0004). An operation that declares a process-name input
 * (Derive) resolves to the user's trimmed entry (which may be empty until they type one); one that
 * does not (Cryopreserve) resolves to a fixed name, its own operation key. The process name is the
 * single key for remembered values and the seed for the derived sample name.
 */
export function resolveProcessName(operation: InventoryOperation, values: Record<string, unknown>): string {
  const from = operation.effect.processNameFrom;
  if (!from) return operation.key;
  const raw = values[from];
  return typeof raw === "string" ? raw.trim() : "";
}
