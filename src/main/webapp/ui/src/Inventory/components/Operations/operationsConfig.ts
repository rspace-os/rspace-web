/**
 * Loads and validates the declarative operation definitions. Operations are data, not code: adding
 * one is a new entry in operations_config.json, with no change here or in the backend (see adr/0001).
 * The valibot schema is the single source of truth for the config shape; the InventoryOperation type
 * is inferred from it, and the JSON is validated at module load so an authoring mistake fails fast.
 */
import * as v from "valibot";
import rawConfig from "./operations_config.json";

const InputSchema = v.object({
  key: v.string(),
  type: v.picklist(["text", "integer", "quantity", "temperature"]),
  labelKey: v.string(),
  required: v.optional(v.boolean()),
  min: v.optional(v.number()),
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

const EffectSchema = v.object({
  nameFrom: v.string(),
  countFrom: v.string(),
  eachAmountFrom: v.string(),
  amountTakenFrom: v.optional(v.string()),
  // input key holding the process name; when set, the operation's "remember" defaults are scoped
  // per process name (see processNames.ts) and the field becomes an autocomplete of saved names.
  processNameFrom: v.optional(v.string()),
  storageTempFrom: v.optional(v.string()),
  links: v.array(LinkSpecSchema),
  textFields: v.optional(v.array(TextFieldSpecSchema)),
});

const OperationSchema = v.object({
  key: v.string(),
  labelKey: v.string(),
  descriptionKey: v.optional(v.string()),
  minSelected: v.number(),
  maxSelected: v.number(),
  documentationStep: v.boolean(),
  inputs: v.array(InputSchema),
  effect: EffectSchema,
});

export type OperationInputConfig = v.InferOutput<typeof InputSchema>;
export type OperationLinkSpec = v.InferOutput<typeof LinkSpecSchema>;
export type InventoryOperation = v.InferOutput<typeof OperationSchema>;

export const operations: Array<InventoryOperation> = v.parse(v.array(OperationSchema), rawConfig);

/** Operations applicable to a selection of the given size (all are subsample-only; see the wizard). */
export function operationsForSelectionSize(count: number): Array<InventoryOperation> {
  return operations.filter((o) => count >= o.minSelected && count <= o.maxSelected);
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
