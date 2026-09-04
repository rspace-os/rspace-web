/**
 * Pure helpers for the single per-process "remember" bundle (DevDocs/adr/0007). One checkbox on
 * step 1 governs everything the user entered for a process name: the collected input values (amounts
 * and any operation-specific fields), the template choice, and the documentation link. The bundle is
 * saved under the process's rememberKey and re-applied when that process name is used again.
 *
 * Supersedes the previous per-item defaults (separate template / documentation / amount preferences).
 */

import type { UnitCategory } from "@/stores/stores/UnitStore";
import type { DocumentationSelection } from "./DocumentationStep";
import { normalizeDocumentation } from "./documentationResolution";
import type { TemplateDefault, TemplateMode } from "./templateResolution";
import type { AmountMode, OperationInputs, PerSubsampleAmounts } from "./types";

/** The template modes a stored bundle may name; anything else is stale or corrupt. */
const TEMPLATE_MODES: ReadonlySet<string> = new Set(["none", "pick", "fromSample", "remembered", "unselected"]);

export type ProcessValues = {
  /** The collected inputs to restore. The wizard omits the name/process-name keys before saving. */
  values: OperationInputs;
  template: TemplateDefault;
  documentation: DocumentationSelection;
  /** The amount mode chosen for a multi-origin run (DevDocs/adr/0007); absent in older bundles = "same". */
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

/**
 * Guard a stored template choice back into shape. Preferences are persisted JSON that outlives the
 * code that wrote them, so an unrecognised mode used to be cast straight through: templateStepValid
 * accepted it and resolveTemplateId then treated it as "fromSample", silently swapping the user's
 * template (Copilot review, PR #1090). Anything unrecognised falls back to "unselected", which
 * blocks the step until the user chooses.
 */
function normalizeTemplateDefault(stored: unknown): TemplateDefault {
  if (typeof stored !== "object" || stored === null) return UNSELECTED_TEMPLATE;
  const { mode, templateId, templateName, quantityCategory } = stored as {
    mode?: unknown;
    templateId?: unknown;
    templateName?: unknown;
    quantityCategory?: unknown;
  };
  if (!TEMPLATE_MODES.has(mode as string)) return UNSELECTED_TEMPLATE;
  // A "pick" is only meaningful with a real id; without one it would restore as a chosen template
  // that cannot be resolved.
  const id = typeof templateId === "number" && Number.isFinite(templateId) ? templateId : null;
  if (mode === "pick" && id === null) return UNSELECTED_TEMPLATE;
  const result: TemplateDefault = { mode: mode as TemplateMode, templateId: id };
  if (typeof templateName === "string") result.templateName = templateName;
  if (typeof quantityCategory === "string") result.quantityCategory = quantityCategory as UnitCategory;
  return result;
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
  const template = normalizeTemplateDefault(s.template);
  const result: ProcessValues = {
    values: s.values as OperationInputs,
    template,
    documentation: normalizeDocumentation(s.documentation),
  };
  // Only carried for multi-origin runs; an older bundle without them normalises to no field, and
  // consumers default the mode to "same" (DevDocs/adr/0007).
  if (s.amountMode !== undefined) result.amountMode = normalizeAmountMode(s.amountMode);
  if (s.perSubsampleAmounts !== undefined)
    result.perSubsampleAmounts = normalizePerSubsampleAmounts(s.perSubsampleAmounts);
  return result;
}

/**
 * The bundle store after a remembered Perform: this run's bundle stored under the key. The wizard
 * calls this only when "remember" is ticked; an unremembered Perform never reaches it, so the
 * previously-saved bundle (if any) is kept - unticking means "do not save this run", never "delete
 * what was saved" (grill Q1). Persisted only on Perform.
 */
export function processValuesAfterPerform(
  current: Record<string, ProcessValues>,
  key: string,
  bundle: ProcessValues,
): Record<string, ProcessValues> {
  return { ...current, [key]: bundle };
}
