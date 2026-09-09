/**
 * Resolves an operation's `effect.computed` values at submit (DevDocs/adr/0007): for each computed value, it
 * sources every argument, applies the named Operation function, and writes the single result into the
 * `into` input. Pure and operation-agnostic - the wizard supplies the parent-sample fields and the
 * current values; the computation lives in the operation function registry, not here.
 */

import { type OperationFunctionArgs, type OperationFunctionName, operationFunctions } from "./operationFunctions";
import type { ComputedArgSource, InventoryOperation } from "./operationsConfig";
import type { OperationInputs } from "./types";

/**
 * The minimal shape the resolver needs of a sample field.
 *
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
  /** Fields on the origin's parent sample, for `parentSampleField` args (loaded before calling). */
  parentFields: ReadonlyArray<SampleField>;
  /** The current input values, for `input` args, and where each result is written. */
  values: OperationInputs;
  /** Resolves a field-name i18n key to the field name to match, in the user's locale. */
  resolveFieldName: (key: string) => string;
};

/**
 * The parent sample's fields a computed value may read: its template-defined fields AND its ad-hoc
 * extra (custom) fields, combined. Both must be searched - a value like Passage number is often added
 * as a custom field, so it lives in `extraFields`; reading only `fields` would miss it and every
 * `parentSampleField` lookup would fall back to its start value (the original Passage "stuck at 1" bug).
 */
export function gatherParentFields(sample: {
  fields: ReadonlyArray<SampleField>;
  extraFields: ReadonlyArray<SampleField>;
}): Array<SampleField> {
  return [...sample.fields, ...sample.extraFields];
}

/**
 * The content of the field a `parentSampleField` arg refers to, matched by definition KEY first and
 * by localized name second.
 *
 * The key is exact and locale-independent, so it is what keeps a lineage intact: a Passage counter
 * whose previous generation was created under a different locale, or under an earlier wording of the
 * same translation, is still found and still increments. Matching only on the current locale's
 * resolution of the key silently missed it and restarted the count at 1, forking the lineage into
 * one counter per locale (F6).
 *
 * The name fallback is PERMANENT, not migration cover: it is how a user's own hand-created "Passage
 * number" (no key at all) is picked up on the first Passage of an existing culture, which is
 * deliberate behaviour rather than an accident.
 */
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
