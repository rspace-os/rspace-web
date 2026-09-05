import type { Locator } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";
import { CommunitiesPage } from "./CommunitiesPage";

export class CommunityCreationPage extends BasePage {
  readonly path = "/system/createCommunity";

  get heading(): Locator {
    return this.page.getByRole("heading", { name: "Create a Community", exact: true });
  }

  get nameField(): Locator {
    return this.page.getByRole("textbox", { name: "Name", exact: true });
  }

  get descriptionField(): Locator {
    return this.page.getByRole("textbox", { name: "Optionally, enter a short description of this community." });
  }

  adminCheckbox(username: string): Locator {
    return this.page.getByRole("checkbox", { name: username, exact: false });
  }

  groupCheckbox(groupName: string): Locator {
    return this.page.getByRole("checkbox", { name: groupName, exact: true });
  }

  get submitButton(): Locator {
    return this.page.getByRole("button", { name: "Submit", exact: true });
  }

  errorText(textSubstring: string): Locator {
    return this.page.getByText(textSubstring);
  }

  async submitExpectingSuccess(): Promise<CommunitiesPage> {
    await this.submitButton.click();
    const page = new CommunitiesPage(this.page);
    await this.page.waitForURL((url) => url.pathname === page.path);
    return page;
  }
}
