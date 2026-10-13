import { formatList } from "@/modules/common/i18n/listFormat";
import type { UnitCategory } from "@/stores/stores/UnitStore";

// "remembered" is a specific template restored from the user's saved default: it resolves to a
// concrete templateId like "pick", but is presented as a banner with no radio selected so the user
// can override it.
export type TemplateMode = "none" | "pick" | "fromSample" | "remembered" | "unselected";

export type TemplateSelection = {
  mode: TemplateMode;
  templateId: number | null;
  templateName?: string;
  quantityCategory?: UnitCategory;
  remember: boolean;
  /**
   * Set on a restored "remembered" template whose id came from the stored bundle rather than a
   * fresh check, so it is not yet confirmed to still exist or be usable. Cleared once the wizard
   * re-checks it; until then the template step is incomplete.
   */
  pendingCheck?: boolean;
};

export type TemplateDefault = Omit<TemplateSelection, "remember" | "pendingCheck">;

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

export function initialTemplateSelection(parentHasTemplate: boolean): TemplateSelection {
  return {
    mode: parentHasTemplate ? "fromSample" : "unselected",
    templateId: null,
    remember: false,
  };
}

export function templateStepValid(selection: {
  mode: TemplateMode;
  templateId: number | null;
  pendingCheck?: boolean;
}): boolean {
  if (selection.mode === "unselected") return false;
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
  if (mode === "none" || mode === "unselected") return null;
  if (mode === "pick" || mode === "remembered") return pickedTemplateId;
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

function fieldHasDefault(field: TemplateFieldsLike["fields"][number]): boolean {
  if ((field.selectedOptions?.length ?? 0) > 0) return true;
  if (field.type === "link") return (field.link?.targetGlobalId ?? "").trim() !== "";
  return field.content !== null && field.content !== undefined && String(field.content).trim() !== "";
}

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
