/**
 * Pure helpers for the wizard's template step (adr/0003).
 *
 * - resolveTemplateId turns the user's choice into a single templateId. Option "fromSample" reuses
 *   the origin sample's existing template; the wizard never creates a template (a template-less
 *   parent must have one made separately first), so "fromSample" with no parent template is blocked
 *   in the UI and resolves to no template defensively here.
 * - templateSelectionBlock decides whether a picked template must be blocked because it has
 *   mandatory fields with no default value (the wizard has no way to supply those values), so the
 *   user is warned in the template step rather than hitting a server error at submit.
 * - templateSelectionToDefault / templateSelectionFor round-trip the current selection to and from
 *   the stored "remember" bundle (see processValues.ts).
 */

import type { UnitCategory } from "@/stores/stores/UnitStore";

// "remembered" is a specific template restored from the user's saved default: it resolves to a
// concrete templateId like "pick", but is presented as a banner with no radio selected so the user
// can override it. "unselected" is the initial state when nothing is remembered: no radio is selected
// and the user must make an explicit choice before Next is enabled (adr/0003).
export type TemplateMode = "none" | "pick" | "fromSample" | "remembered" | "unselected";

export type TemplateSelectionLike = {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  quantityCategory?: UnitCategory;
  remember: boolean;
};

export type TemplateDefault = {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  quantityCategory?: UnitCategory;
};

/**
 * The current selection reduced to the shape stored in the per-process "remember" bundle. A specific
 * template (an explicit "pick" or a still-in-effect "remembered") is stored as a concrete "pick" with
 * its id, name and quantity category, so it is restored and shown as that template next time; every
 * other mode ("none"/"fromSample") is stored as itself with no template id.
 */
export function templateSelectionToDefault(selection: {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  quantityCategory?: UnitCategory;
}): TemplateDefault {
  const isSpecific = selection.mode === "pick" || selection.mode === "remembered";
  return {
    mode: isSpecific ? "pick" : selection.mode,
    templateId: isSpecific ? selection.templateId : null,
    templateName: isSpecific ? selection.templateName : undefined,
    quantityCategory: isSpecific ? selection.quantityCategory : undefined,
  };
}

/**
 * The template-step selection to show for a stored default (or its absence). Nothing remembered
 * (first run, or the previous run was not remembered) yields "unselected": no radio selected, so the
 * user must make an explicit choice before Next is enabled. A remembered specific template is shown
 * as a banner ("remembered", no radio); a remembered "none"/"fromSample" is applied as that radio
 * directly (adr/0003).
 */
export function templateSelectionFor(remembered: TemplateDefault | undefined): TemplateSelectionLike {
  if (!remembered) return { mode: "unselected", templateId: null, remember: false };
  const isSpecific = remembered.mode === "pick" && remembered.templateId !== null;
  return {
    mode: isSpecific ? "remembered" : remembered.mode,
    templateId: remembered.templateId,
    templateName: remembered.templateName,
    quantityCategory: remembered.quantityCategory,
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

export function resolveTemplateId(params: {
  mode: TemplateMode;
  pickedTemplateId: number | null;
  originSampleTemplateId: number | null;
}): number | null {
  const { mode, pickedTemplateId, originSampleTemplateId } = params;
  // "unselected" is unreachable here (Next is disabled until the user chooses); treat it as "no
  // template" defensively.
  if (mode === "none" || mode === "unselected") return null;
  // "remembered" is a concrete template restored from the saved default, so it resolves like "pick".
  if (mode === "pick" || mode === "remembered") return pickedTemplateId;
  // fromSample: reuse the origin sample's own template. The wizard never creates one (adr/0003), so a
  // template-less parent (null) resolves to no template - the UI blocks this choice up front.
  return originSampleTemplateId;
}

export function templateSelectionBlock(fields: Array<{ name: string; mandatory: boolean; hasDefault: boolean }>): {
  blocked: boolean;
  missingFields: Array<string>;
} {
  const missingFields = fields.filter((f) => f.mandatory && !f.hasDefault).map((f) => f.name);
  return { blocked: missingFields.length > 0, missingFields };
}
