import { formatList } from "@/modules/common/i18n/listFormat";
import type { UnitCategory } from "@/stores/stores/UnitStore";

// "remembered" is a specific template restored from the user's saved default: it resolves to a
// concrete templateId like "pick", but is presented as a banner with no radio selected so the user
// can override it. "unselected" is the initial state when nothing is remembered: no radio is
// selected and the user must make an explicit choice before Next is enabled.
export type TemplateMode = "none" | "pick" | "fromSample" | "remembered" | "unselected";

/**
 * Declared here, not in TemplateStep, so the module and the component cannot disagree: previously
 * duplicated declarations let a mode added to one compile cleanly against the other until a
 * runtime `switch` fell through. TemplateStep re-exports this type to keep existing imports working.
 */
export type TemplateSelection = {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  // Set when the user picks a specific template, so the amounts step offers that template's units
  // instead of the origin subsample's. Undefined falls back to the origin.
  quantityCategory?: UnitCategory;
  remember: boolean;
  /**
   * Set on a restored "remembered" template whose id came from the stored bundle rather than a
   * fresh check, so it is not yet confirmed to still exist or be usable. Cleared once the wizard
   * re-checks it; until then the template step is incomplete.
   */
  pendingCheck?: boolean;
};

/** The selection as it is stored in a "remember" bundle: no per-run state. */
export type TemplateDefault = Omit<TemplateSelection, "remember" | "pendingCheck">;

/**
 * Reduces the selection to the shape stored in the per-process "remember" bundle: a specific
 * template ("pick" or a still-in-effect "remembered") as a concrete "pick" with its id, name and
 * quantity category; every other mode as itself with no template id.
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
 * Selection to show for a stored default. Nothing remembered yields "unselected", so the user must
 * choose explicitly. A remembered specific template is shown as a banner ("remembered"); a
 * remembered "none"/"fromSample" is applied as that radio directly.
 */
export function templateSelectionFor(remembered: TemplateDefault | undefined): TemplateSelection {
  if (!remembered) return { mode: "unselected", templateId: null, remember: false };
  const isSpecific = remembered.mode === "pick" && remembered.templateId !== null;
  return {
    mode: isSpecific ? "remembered" : remembered.mode,
    templateId: remembered.templateId,
    templateName: remembered.templateName,
    quantityCategory: remembered.quantityCategory,
    remember: true,
    ...(isSpecific ? { pendingCheck: true } : {}),
  };
}

/**
 * Preselects "fromSample" when the origin's parent has its own template (the common case);
 * otherwise "unselected", so the user must choose explicitly. Callers pass parentHasTemplate=false
 * for a multi-origin operation, since "the parent" is ambiguous across origins.
 */
export function initialTemplateSelection(parentHasTemplate: boolean): TemplateSelection {
  return {
    mode: parentHasTemplate ? "fromSample" : "unselected",
    templateId: null,
    remember: false,
  };
}

/**
 * Whether the template step is complete: the user made a choice (not "unselected"), and any
 * specific template has finished validating (its id is set).
 *
 * "fromSample" is held to the same rule as "pick": both create a sample against a concrete
 * template, so both must clear the mandatory-without-default check before the step can advance.
 * Letting "fromSample" through unchecked meant the same template was blocked via "pick" but
 * accepted via "fromSample".
 *
 * The id is written only by a passing check, which is what makes its presence the signal.
 */
export function templateStepValid(selection: {
  mode: TemplateMode;
  templateId: number | null;
  pendingCheck?: boolean;
}): boolean {
  if (selection.mode === "unselected") return false;
  // A restored template's id came from the stored bundle, not a check, so it counts only once the
  // restore-time check clears pendingCheck. Otherwise a renamed template could show its old name,
  // or a trashed one be offered for Perform.
  if (selection.mode === "remembered") return !selection.pendingCheck;
  if (selection.mode === "pick" || selection.mode === "fromSample") return selection.templateId !== null;
  return true;
}

export function resolveTemplateId(params: {
  mode: TemplateMode;
  pickedTemplateId: number | null;
  originSampleTemplateId: number | null;
}): number | null {
  const { mode, pickedTemplateId, originSampleTemplateId } = params;
  // "unselected" is unreachable here (Next is disabled first); resolved to no template defensively.
  if (mode === "none" || mode === "unselected") return null;
  // "remembered" resolves like "pick": it's a concrete template restored from the saved default.
  if (mode === "pick" || mode === "remembered") return pickedTemplateId;
  // fromSample reuses the origin's own template; a template-less parent resolves to no template,
  // though the UI blocks that choice.
  return originSampleTemplateId;
}

export function templateSelectionBlock(fields: Array<{ name: string; mandatory: boolean; hasDefault: boolean }>): {
  blocked: boolean;
  missingFields: Array<string>;
} {
  const missingFields = fields.filter((f) => f.mandatory && !f.hasDefault).map((f) => f.name);
  return { blocked: missingFields.length > 0, missingFields };
}

/** Just enough of TemplateModel to judge a template: avoids importing the MobX model here. */
type TemplateFieldsLike = {
  fields: ReadonlyArray<{
    name: string;
    mandatory: boolean;
    type?: string;
    content?: unknown;
    link?: { targetGlobalId?: string | null } | null;
    selectedOptions?: ReadonlyArray<unknown> | null;
  }>;
};

/**
 * Field-type aware: a LINK field's default lives in `link.targetGlobalId`, not `content` (which is
 * always empty for links), so a content-only check marked every populated mandatory link as
 * missing. Blank counts as absent for content and link target alike, matching the server's own
 * mandatory-field check.
 */
function fieldHasDefault(field: TemplateFieldsLike["fields"][number]): boolean {
  if ((field.selectedOptions?.length ?? 0) > 0) return true;
  if (field.type === "link") return (field.link?.targetGlobalId ?? "").trim() !== "";
  return field.content !== null && field.content !== undefined && String(field.content).trim() !== "";
}

/**
 * Whether a template is usable, with the arguments its rejection message needs. Kept as one
 * function since duplicating this check previously let two screens disagree about the same
 * template after a fix landed on only one copy.
 *
 * The `t` call stays at each call site, not here: i18next's TFunction is typed against the
 * catalog's key union, so accepting it as a parameter would need a cast. Since only the ICU
 * arguments are returned, the two call sites can't compute them differently.
 */
export function templateBlockReason(
  template: TemplateFieldsLike,
  language: string,
): { blocked: boolean; count: number; fields: string } {
  const { blocked, missingFields } = templateSelectionBlock(
    template.fields.map((f) => ({
      name: f.name,
      mandatory: f.mandatory,
      hasDefault: fieldHasDefault(f),
    })),
  );
  return { blocked, count: missingFields.length, fields: formatList(missingFields, language) };
}
