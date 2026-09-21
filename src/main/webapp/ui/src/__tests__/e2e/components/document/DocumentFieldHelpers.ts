import type { Page } from "@playwright/test";

// Multi-field documents (e.g. Experiment) render each field collapsed until its own
// #edit_<fieldId> pencil is clicked; Basic Document fields are already expanded. Races
// both signals rather than a one-shot isVisible() check, which would false-trigger a
// click before a fresh load/reload finishes mounting an already-expanded iframe.
export async function activateFieldForEditing(page: Page, fieldId: string): Promise<void> {
  const iframe = page.locator(`iframe#rtf_${fieldId}_ifr`);
  const editButton = page.locator(`#edit_${fieldId}`);
  let expanded = false;
  await Promise.race([
    iframe.waitFor({ state: "visible" }).then(() => {
      expanded = true;
    }),
    editButton.waitFor({ state: "visible" }).then(async () => {
      if (expanded) return;
      await editButton.click();
    }),
  ]);
  await iframe.waitFor({ state: "visible" });
}

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
