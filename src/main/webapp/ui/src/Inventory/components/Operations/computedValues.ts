/**
 * Resolves an operation's `effect.computed` values at submit (adr/0006): for each computed value, it
 * sources every argument, applies the named Operation function, and writes the single result into the
 * `into` input. Pure and operation-agnostic - the wizard supplies the parent-sample fields and the
 * current values; the computation lives in the operation function registry, not here.
 */

import { type OperationFunctionArgs, type OperationFunctionName, operationFunctions } from "./operationFunctions";
import type { ComputedArgSource, InventoryOperation } from "./operationsConfig";
import type { OperationInputs } from "./types";

/** The minimal shape the resolver needs of a sample field (name + content). */
type SampleField = { name: string | null; content: string | number | Date };

export type ComputedContext = {
  /** Fields on the origin's parent sample, for `parentSampleField` args (loaded before calling). */
  parentFields: ReadonlyArray<SampleField>;
  /** The current input values, for `input` args, and where each result is written. */
  values: OperationInputs;
  /** Resolves a field-name i18n key to the field name to match, in the user's locale. */
  resolveFieldName: (key: string) => string;
};

/** The content of the (case- and whitespace-insensitively) named field, or undefined if absent. */
function parentFieldValue(fields: ReadonlyArray<SampleField>, name: string): string | number | undefined {
  const wanted = name.trim().toLowerCase();
  const field = fields.find((f) => (f.name ?? "").trim().toLowerCase() === wanted);
  if (!field) return undefined;
  return field.content instanceof Date ? field.content.toISOString() : field.content;
}

function resolveArg(source: ComputedArgSource, ctx: ComputedContext): string | number | undefined {
  if ("parentSampleField" in source) {
    return parentFieldValue(ctx.parentFields, ctx.resolveFieldName(source.parentSampleField));
  }
  if ("constant" in source) return source.constant;
  const value = ctx.values[source.input];
  return typeof value === "string" || typeof value === "number" ? value : undefined;
}

/**
 * Applies the operation's computed values in config order, returning the values map augmented with
 * each result under its `into` key. Because results are written back, a later computed value can read
 * an earlier one via an `input` arg (chaining follows the array order). Assumes the config passed
 * load-time validation (operationsConfig), so every `fn` resolves.
 */
export function applyComputedValues(operation: InventoryOperation, ctx: ComputedContext): OperationInputs {
  let values = ctx.values;
  for (const computed of operation.effect.computed ?? []) {
    const args: OperationFunctionArgs = {};
    for (const [name, source] of Object.entries(computed.args)) {
      args[name] = resolveArg(source, { ...ctx, values });
    }
    const def = operationFunctions[computed.fn as OperationFunctionName];
    values = { ...values, [computed.into]: def.fn(args) };
  }
  return values;
}
