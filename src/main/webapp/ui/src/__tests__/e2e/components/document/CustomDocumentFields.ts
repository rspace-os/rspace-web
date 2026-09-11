import { expect, type Locator, type Page } from "@playwright/test";
import { resolveFieldId } from "./DocumentFieldHelpers";

/** Inline editing of the legacy document's non-rich-text fields. */
export class CustomDocumentFields {
  constructor(private readonly page: Page) {}

  async setInput(name: string, value: string): Promise<void> {
    const id = await resolveFieldId(this.page, name, 0, "setInput");
    // JSP inputs use the numeric field ID and have no accessible name.
    const input = this.page.locator(`#field_${id}`).locator("input:not([type=hidden]):visible");
    if (!(await input.isVisible())) await this.page.locator(`#edit_${id}`).click();
    // The legacy timepicker parses key events; fill() alone is reverted on blur.
    await input.press("ControlOrMeta+A");
    await input.pressSequentially(value);
    await this.waitForAutosave(id, () => input.press("Tab"), value);
  }

  async setOptions(name: string, values: string[]): Promise<void> {
    const id = await resolveFieldId(this.page, name, 0, "setOptions");
    await this.page.locator(`#edit_${id}`).click();
    const field = this.page.locator(`#field_${id}`);
    const choices = field.getByRole("checkbox");
    const radios = field.getByRole("radio");
    await choices.or(radios).first().waitFor({ state: "visible" });
    const options = await choices.or(radios).all();
    const optionValues = await Promise.all(options.map((option) => option.inputValue()));
    const selectedValues = optionValues.filter((value) => values.includes(value));
    expect(selectedValues.toSorted(), "Every requested option must exist in the field").toEqual(values.toSorted());

    const expectedData =
      (await choices.count()) > 0
        ? selectedValues.map((value) => `fieldChoices=${value}`).join("&")
        : selectedValues[0];
    await this.waitForAutosave(
      id,
      async () => {
        for (const option of options) {
          const selected = values.includes(await option.inputValue());
          if (selected) await option.check();
          else if ((await option.getAttribute("type")) === "checkbox") await option.uncheck();
        }
      },
      expectedData,
    );
  }

  async value(name: string): Promise<Locator> {
    const id = await resolveFieldId(this.page, name, 0, "value");
    return this.page.locator(`#plainText_${id}, #choiceText_${id}, #radioText_${id}`);
  }

  async notebookValue(name: string): Promise<string> {
    const heading = this.page.locator("#journalPage").getByRole("heading", { name, exact: true, level: 2 });
    await heading.waitFor({ state: "visible" });
    return heading.evaluate((element) => {
      const parts: string[] = [];
      for (let node = element.nextSibling; node; node = node.nextSibling) {
        if (node instanceof HTMLElement && node.tagName === "H2") break;
        parts.push(node.textContent ?? "");
      }
      return parts
        .map((part) => part.trim())
        .filter((part) => part.length > 0)
        .join(" ");
    });
  }

  private async waitForAutosave(id: string, action: () => Promise<void>, value?: string): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => {
        if (!res.url().endsWith("/ajax/autosaveField")) return false;
        const fields = new URLSearchParams(res.request().postData() ?? "");
        return fields.get("fieldId") === id && (value === undefined || fields.get("dataValue") === value);
      }),
      action(),
    ]);
    expect(response.ok(), "Autosave HTTP response").toBe(true);
    expect(await response.json(), "Autosave must persist the field without validation errors").toMatchObject({
      data: true,
    });
  }
}
