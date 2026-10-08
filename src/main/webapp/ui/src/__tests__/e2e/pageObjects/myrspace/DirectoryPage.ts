import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";
import { UserProfilePage } from "./UserProfilePage";

export class DirectoryPage extends BasePage {
  readonly path = "/directory";

  // Community (cloud) servers label the box with its placeholder instead of "Search".
  private get searchBox(): Locator {
    return this.page
      .getByRole("textbox", { name: "Search", exact: true })
      .or(this.page.getByRole("textbox", { name: "By name, email or username", exact: true }));
  }

  /** Community (cloud) only: users are hidden until you search. */
  get searchPrompt(): Locator {
    return this.page.getByText("Please search for users using the search bar on the right of the page.");
  }

  noResultsMessage(query: string): Locator {
    return this.page.getByText(`Your search for '${query}' returned no results.`);
  }

  userRow(username: string): Locator {
    return this.page.getByRole("row").filter({ has: this.page.getByRole("link", { name: username, exact: true }) });
  }

  columnHeader(name: string): Locator {
    return this.page.getByRole("columnheader", { name, exact: true });
  }

  async search(query: string): Promise<void> {
    await this.searchBox.fill(query);
    await this.page.getByRole("button", { name: "Search", exact: true }).click();
    // Hits and misses both name the query here once its rows are inserted; "Clear search" alone
    // stays visible from any earlier search.
    await this.page
      .locator("#searchModePanel")
      .filter({ hasText: `'${query}'` })
      .waitFor({ state: "visible" });
  }

  async openUserProfile(username: string): Promise<UserProfilePage> {
    await this.search(username);
    await this.page.getByRole("link", { name: username, exact: true }).click();
    const profile = new UserProfilePage(this.page);
    await profile.waitUntilLoaded();
    return profile;
  }
}
