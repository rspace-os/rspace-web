import type { Locator, Page } from "@playwright/test";

export class UserPickerComponent {
  constructor(
    private readonly page: Page,
    private readonly availableLabel: string,
    private readonly selectedLabel: string,
  ) {}

  private column(label: string): Locator {
    return this.page.locator(`[data-test-id="${label.split(" ").join("-")}-column"]`);
  }

  private availableColumn(): Locator {
    const availableTestId = this.availableLabel.split(" ").join("-");
    const selectedTestId = this.selectedLabel.split(" ").join("-");
    return this.page.locator(
      `[data-test-id="${availableTestId}-column"]:has(~ [data-test-id="${selectedTestId}-column"])`,
    );
  }

  private row(column: Locator, username: string): Locator {
    return column.locator(`[data-test-id="select-option-${username}"]`);
  }

  async addUser(username: string): Promise<void> {
    await this.row(this.availableColumn(), username).click();
    await this.page.locator(`[data-test-id="add-${this.selectedLabel.split(" ").join("-")}"]`).click();
  }

  async hasSelected(username: string): Promise<boolean> {
    return this.row(this.column(this.selectedLabel), username).isVisible();
  }
}
