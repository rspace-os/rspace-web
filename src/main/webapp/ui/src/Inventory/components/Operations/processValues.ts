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
import type { OperationInputs } from "./types";

export type ProcessValues = {
  /** The collected inputs to restore. The wizard omits the name/process-name keys before saving. */
  values: OperationInputs;
  template: TemplateDefault;
  documentation: DocumentationSelection;
};

const UNSELECTED_TEMPLATE: TemplateDefault = { mode: "unselected", templateId: null };

/** Guard a stored bundle back into shape, tolerating an absent template/documentation. */
export function normalizeProcessValues(stored: unknown): ProcessValues | null {
  if (typeof stored !== "object" || stored === null) return null;
  const s = stored as { values?: unknown; template?: unknown; documentation?: unknown };
  if (typeof s.values !== "object" || s.values === null) return null;
  const template =
    typeof s.template === "object" && s.template !== null ? (s.template as TemplateDefault) : UNSELECTED_TEMPLATE;
  return {
    values: s.values as OperationInputs,
    template,
    documentation: normalizeDocumentation(s.documentation),
  };
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
