import { type Locator, page, userEvent } from "vitest/browser";

export class HiddenInvalidFieldsPage {
  get hoursRun(): Locator {
    return page.getByRole("spinbutton", { name: "Hours run" });
  }

  get save(): Locator {
    return page.getByRole("button", { name: "Save" });
  }

  get saveReason(): Locator {
    return page.getByRole("alert");
  }

  async answerServiced(answer: "Yes" | "No"): Promise<void> {
    await page.getByRole("radio", { name: answer, exact: true }).click();
  }

  async typeHoursRun(text: string): Promise<void> {
    await userEvent.type(this.hoursRun, text);
  }

  async clearHoursRun(): Promise<void> {
    await userEvent.clear(this.hoursRun);
  }
}
