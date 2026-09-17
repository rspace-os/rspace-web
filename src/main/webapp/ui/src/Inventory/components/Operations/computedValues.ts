import { type OperationFunctionArgs, operationFunctions } from "./operationFunctions";
import type { ComputedArgSource, InventoryKey, InventoryOperation } from "./operationsConfig";
import type { OperationInputs } from "./types";

/**
 * `operationFieldKey` is the field's STABLE identity when an operation generated it; `name` is a
 * localized resolution of that key, so it changes with the locale and with any rewording of the
 * translation. Absent (null/undefined) on every hand-created field.
 */
type SampleField = {
  name: string | null;
  content: string | number | Date;
  operationFieldKey?: string | null;
};

export type ComputedContext = {
  parentFields: ReadonlyArray<SampleField>;
  /** The current input values, for `input` args, and where each result is written. */
  values: OperationInputs;
  resolveFieldName: (key: InventoryKey) => string;
};

function parentFieldValue(fields: ReadonlyArray<SampleField>, key: string, name: string): string | number | undefined {
  const wanted = name.trim().toLowerCase();
  const field =
    fields.find((f) => f.operationFieldKey === key) ??
    fields.find((f) => (f.name ?? "").trim().toLowerCase() === wanted);
  if (!field) return undefined;
  return field.content instanceof Date ? field.content.toISOString() : field.content;
}

function resolveArg(source: ComputedArgSource, ctx: ComputedContext): string | number | undefined {
  if ("parentSampleField" in source) {
    return parentFieldValue(ctx.parentFields, source.parentSampleField, ctx.resolveFieldName(source.parentSampleField));
  }
  if ("constant" in source) return source.constant;
  const value = ctx.values[source.input];
  return typeof value === "string" || typeof value === "number" ? value : undefined;
}

/**
 * Because results are written back, a later computed value can read an earlier one via an `input`
 * arg (chaining follows the array order).
 */
export function applyComputedValues(operation: InventoryOperation, ctx: ComputedContext): OperationInputs {
  let values = ctx.values;
  for (const computed of operation.effect.computed ?? []) {
    const args: OperationFunctionArgs = {};
    for (const [name, source] of Object.entries(computed.args)) {
      args[name] = resolveArg(source, { ...ctx, values });
    }
    const def = operationFunctions[computed.fn];
    values = { ...values, [computed.into]: def.fn(args) };
  }
  return values;
}
