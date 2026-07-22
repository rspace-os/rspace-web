import type { Page } from "@playwright/test";

export async function resolveFieldId(
  page: Page,
  fieldName: string,
  index: number,
  callerName: string,
): Promise<string> {
  // Legacy document fields expose only this class before their dynamic IDs are resolved.
  const fieldTd = page.locator("td.field-name").filter({ hasText: fieldName }).nth(index);
  const tdId = await fieldTd.getAttribute("id");
  if (!tdId) {
    throw new Error(
      `${callerName}('${fieldName}', ${index}): no td.field-name element with an id attribute found. ` +
        "Verify the field name is spelled exactly as rendered and the document is in the expected mode.",
    );
  }
  const fieldId = tdId.replace("field-name-", "");
  if (!fieldId) {
    throw new Error(
      `${callerName}('${fieldName}', ${index}): id attribute '${tdId}' yielded an empty field id after ` +
        "stripping the 'field-name-' prefix.",
    );
  }
  return fieldId;
}
