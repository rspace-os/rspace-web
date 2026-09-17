/**
 * Pure helpers for the per-process "remember" bundle: the collected input values, template choice,
 * and documentation link, saved under the process's rememberKey and re-applied when that process
 * name is used again.
 */

import type { UnitCategory } from "@/stores/stores/UnitStore";
import type { DocumentationSelection } from "./DocumentationStep";
import type { TemplateDefault, TemplateMode } from "./templateResolution";
import type { AmountMode, OperationInputs, PerSubsampleAmounts } from "./types";

const TEMPLATE_MODES: ReadonlySet<string> = new Set(["none", "pick", "fromSample", "remembered", "unselected"]);

export type ProcessValues = {
  /** The wizard omits the name/process-name keys before saving. */
  values: OperationInputs;
  template: TemplateDefault;
  documentation: DocumentationSelection;
  amountMode?: AmountMode;
  perSubsampleAmounts?: PerSubsampleAmounts;
};

const UNSELECTED_TEMPLATE: TemplateDefault = { mode: "unselected", templateId: null };
const AMOUNT_MODES: ReadonlyArray<AmountMode> = ["same", "all", "perSubsample"];

function normalizeDocumentation(stored: unknown): DocumentationSelection {
  const s = stored as { globalId?: unknown; name?: unknown } | null | undefined;
  return s && typeof s.globalId === "string" && typeof s.name === "string"
    ? { globalId: s.globalId, name: s.name }
    : null;
}

function normalizeAmountMode(value: unknown): AmountMode {
  return typeof value === "string" && (AMOUNT_MODES as ReadonlyArray<string>).includes(value)
    ? (value as AmountMode)
    : "same";
}

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
  if (s.amountMode !== undefined) result.amountMode = normalizeAmountMode(s.amountMode);
  if (s.perSubsampleAmounts !== undefined)
    result.perSubsampleAmounts = normalizePerSubsampleAmounts(s.perSubsampleAmounts);
  return result;
}
