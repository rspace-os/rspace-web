import { type OperationFunctionArgs, operationFunctions } from "./operationFunctions";
import type { ComputedArgSource, InventoryKey, InventoryOperation } from "./operationsConfig";
import type { OperationInputs } from "./types";

type SampleField = {
  name: string | null;
  content: string | number | Date;
  operationFieldKey?: string | null;
};

export type ComputedContext = {
  parentFields: ReadonlyArray<SampleField>;
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
  return source.constant;
}

export function applyComputedValues(operation: InventoryOperation, ctx: ComputedContext): OperationInputs {
  let values = ctx.values;
  for (const computed of operation.effect.computed ?? []) {
    const args: OperationFunctionArgs = {};
    for (const [name, source] of Object.entries(computed.args)) {
      args[name] = resolveArg(source, ctx);
    }
    const def = operationFunctions[computed.fn];
    values = { ...values, [computed.into]: def.fn(args) };
  }
  return values;
}
