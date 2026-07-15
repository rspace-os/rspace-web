/**
 * Pure helpers for the wizard's template step (adr/0003).
 *
 * - resolveTemplateId turns the user's choice into a single templateId. Option "fromSample" reuses
 *   the origin sample's existing template if it has one, and only creates a new template when the
 *   sample has none (so we do not proliferate templates).
 * - templateSelectionBlock decides whether a picked template must be blocked because it has
 *   mandatory fields with no default value (the wizard has no way to supply those values), so the
 *   user is warned in the template step rather than hitting a server error at submit.
 * - templateDefaultsAfterPerform computes the per-user, per-operation "remembered" template choice
 *   to persist after Perform.
 */

import { omit } from "es-toolkit";

// "remembered" is a specific template restored from the user's saved default: it resolves to a
// concrete templateId like "pick", but is presented as a banner with no radio selected so the user
// can override it. "unselected" is the initial state when nothing is remembered: no radio is selected
// and the user must make an explicit choice before Next is enabled (adr/0003).
export type TemplateMode = "none" | "pick" | "fromSample" | "remembered" | "unselected";

export type TemplateSelectionLike = {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  remember: boolean;
};

export type TemplateDefault = { mode: TemplateMode; templateId: number | null; templateName?: string };

/**
 * The selection to persist as the remembered default after Perform. The backend cannot link a
 * newly-created template back to the origin sample, so when "use parent template" had to CREATE a
 * template (the parent had none), we instead remember it as that specific template. Subsequent runs
 * then reuse it (shown as the remembered template) rather than creating duplicates. Every other
 * case - including "fromSample" when the parent already had a template (no duplication) - is
 * remembered as chosen.
 */
export function selectionToRemember(params: {
  selection: TemplateSelectionLike;
  resolvedTemplateId: number | null;
  originHadTemplate: boolean;
  createdTemplateName: string;
}): TemplateSelectionLike {
  const { selection, resolvedTemplateId, originHadTemplate, createdTemplateName } = params;
  if (selection.mode === "fromSample" && !originHadTemplate && resolvedTemplateId !== null) {
    return {
      mode: "pick",
      templateId: resolvedTemplateId,
      templateName: createdTemplateName,
      remember: selection.remember,
    };
  }
  return selection;
}

/**
 * The per-operation template defaults after "Perform" is clicked. When "remember" is ticked, store
 * this operation's choice (only "pick" keeps a templateId and its name, so the name can be shown as
 * the default next time); when it is unticked, drop any previously-remembered choice for this
 * operation so that unticking is itself remembered. Keyed by operation, so one operation's choice
 * never affects another (e.g. "derive" vs "cryopreserve"). Persisted only on perform, never on
 * cancel: the caller invokes this solely after a successful operation.
 */
export function templateDefaultsAfterPerform(
  current: Record<string, TemplateDefault>,
  operationKey: string,
  selection: { mode: TemplateMode; templateId: number | null; templateName?: string; remember: boolean },
): Record<string, TemplateDefault> {
  if (!selection.remember) return omit(current, [operationKey]);
  // Both an explicit "pick" and a still-in-effect "remembered" template are stored as a concrete
  // "pick" so they are restored (and shown) as a specific template next time.
  const isSpecific = selection.mode === "pick" || selection.mode === "remembered";
  return {
    ...current,
    [operationKey]: {
      mode: isSpecific ? "pick" : selection.mode,
      templateId: isSpecific ? selection.templateId : null,
      templateName: isSpecific ? selection.templateName : undefined,
    },
  };
}

/**
 * The template-step selection to show for a stored default (or its absence). Nothing remembered
 * (first run, or the previous run left "remember" unticked) yields "unselected": no radio selected,
 * so the user must make an explicit choice before Next is enabled. A remembered specific template is
 * shown as a banner ("remembered", no radio); a remembered "none"/"fromSample" is applied as that
 * radio directly (adr/0003).
 */
export function templateSelectionFor(remembered: TemplateDefault | undefined): TemplateSelectionLike {
  if (!remembered) return { mode: "unselected", templateId: null, remember: false };
  const isSpecific = remembered.mode === "pick" && remembered.templateId !== null;
  return {
    mode: isSpecific ? "remembered" : remembered.mode,
    templateId: remembered.templateId,
    templateName: remembered.templateName,
    remember: true,
  };
}

/**
 * Whether the template step is complete enough to advance: the user has made a choice (not the
 * initial "unselected" state), and a picked template has finished validating (its id is set).
 */
export function templateStepValid(selection: { mode: TemplateMode; templateId: number | null }): boolean {
  if (selection.mode === "unselected") return false;
  if (selection.mode === "pick") return selection.templateId !== null;
  return true;
}

export async function resolveTemplateId(params: {
  mode: TemplateMode;
  pickedTemplateId: number | null;
  originSampleTemplateId: number | null;
  createTemplate: () => Promise<number>;
}): Promise<number | null> {
  const { mode, pickedTemplateId, originSampleTemplateId, createTemplate } = params;
  // "unselected" is unreachable here (Next is disabled until the user chooses), but treat it as "no
  // template" defensively rather than falling through to the create-a-template branch.
  if (mode === "none" || mode === "unselected") return null;
  // "remembered" is a concrete template restored from the saved default, so it resolves like "pick".
  if (mode === "pick" || mode === "remembered") return pickedTemplateId;
  // fromSample: reuse the origin sample's own template if it has one; create only when it has none.
  return originSampleTemplateId ?? (await createTemplate());
}

export function templateSelectionBlock(fields: Array<{ name: string; mandatory: boolean; hasDefault: boolean }>): {
  blocked: boolean;
  missingFields: Array<string>;
} {
  const missingFields = fields.filter((f) => f.mandatory && !f.hasDefault).map((f) => f.name);
  return { blocked: missingFields.length > 0, missingFields };
}
