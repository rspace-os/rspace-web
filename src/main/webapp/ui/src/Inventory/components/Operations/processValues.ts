/**
 * Pure helpers for the single per-process "remember" bundle (adr/0003, adr/0004). One checkbox on
 * step 1 governs everything the user entered for a process name: the collected input values (amounts
 * and any operation-specific fields), the template choice, and the documentation link. The bundle is
 * saved under the process's rememberKey and re-applied when that process name is used again.
 *
 * Supersedes the previous per-item defaults (separate template / documentation / amount preferences).
 */

import type { DocumentationSelection } from "./DocumentationStep";
import { normalizeDocumentation } from "./documentationResolution";
import type { TemplateDefault } from "./templateResolution";
import type { AmountMode, OperationInputs, PerSubsampleAmounts } from "./types";

export type ProcessValues = {
  /** The collected inputs to restore. The wizard omits the name/process-name keys before saving. */
  values: OperationInputs;
  template: TemplateDefault;
  documentation: DocumentationSelection;
  /** The amount mode chosen for a multi-origin run (adr/0009); absent in older bundles = "same". */
  amountMode?: AmountMode;
  /** Per-origin amounts by origin global id, for "perSubsample" mode; absent otherwise. */
  perSubsampleAmounts?: PerSubsampleAmounts;
};

const UNSELECTED_TEMPLATE: TemplateDefault = { mode: "unselected", templateId: null };
const AMOUNT_MODES: ReadonlyArray<AmountMode> = ["same", "all", "perSubsample"];

function normalizeAmountMode(value: unknown): AmountMode {
  return typeof value === "string" && (AMOUNT_MODES as ReadonlyArray<string>).includes(value)
    ? (value as AmountMode)
    : "same";
}

/** Keep only entries that are a complete numeric quantity, dropping anything malformed in storage. */
function normalizePerSubsampleAmounts(value: unknown): PerSubsampleAmounts {
  if (typeof value !== "object" || value === null) return {};
  const out: PerSubsampleAmounts = {};
  for (const [globalId, q] of Object.entries(value as Record<string, unknown>)) {
    if (typeof q === "object" && q !== null) {
      const { numericValue, unitId } = q as { numericValue?: unknown; unitId?: unknown };
      if (typeof numericValue === "number" && typeof unitId === "number") out[globalId] = { numericValue, unitId };
    }
  }
  return out;
}

/** Guard a stored bundle back into shape, tolerating an absent template/documentation/amount mode. */
export function normalizeProcessValues(stored: unknown): ProcessValues | null {
  if (typeof stored !== "object" || stored === null) return null;
  const s = stored as {
    values?: unknown;
    template?: unknown;
    documentation?: unknown;
    amountMode?: unknown;
    perSubsampleAmounts?: unknown;
  };
  if (typeof s.values !== "object" || s.values === null) return null;
  const template =
    typeof s.template === "object" && s.template !== null ? (s.template as TemplateDefault) : UNSELECTED_TEMPLATE;
  const result: ProcessValues = {
    values: s.values as OperationInputs,
    template,
    documentation: normalizeDocumentation(s.documentation),
  };
  // Only carried for multi-origin runs; an older bundle without them normalises to no field, and
  // consumers default the mode to "same" (adr/0009).
  if (s.amountMode !== undefined) result.amountMode = normalizeAmountMode(s.amountMode);
  if (s.perSubsampleAmounts !== undefined)
    result.perSubsampleAmounts = normalizePerSubsampleAmounts(s.perSubsampleAmounts);
  return result;
}

/**
 * The bundle store after Perform. When "remember" is ticked, store this run's bundle under the key;
 * when it is not, leave the store untouched (the previously-saved bundle, if any, is kept - unticking
 * means "do not save this run", never "delete what was saved"; grill Q1). Persisted only on Perform.
 */
export function processValuesAfterPerform(
  current: Record<string, ProcessValues>,
  key: string,
  bundle: ProcessValues,
  remember: boolean,
): Record<string, ProcessValues> {
  if (!remember) return current;
  return { ...current, [key]: bundle };
}
