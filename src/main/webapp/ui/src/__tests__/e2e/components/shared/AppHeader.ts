import type { Locator, Page } from "@playwright/test";

export type NavItem = "Workspace" | "Gallery" | "Inventory" | "My RSpace" | "System";

/**
 * The app-wide present on every authenticated page (accessible
 * landmark name "page header").
 */
export class AppHeader {
  readonly brandingImage: Locator;
  readonly pageHeading: Locator;
  readonly notificationsLink: Locator;
  readonly accountMenuButton: Locator;
  readonly openHelpButton: Locator;
  private readonly desktopNav: Locator;
  private readonly mobileNav: Locator;

  constructor(private readonly page: Page) {
    this.brandingImage = page.getByRole("img", { name: "branding" });
    this.pageHeading = page.getByRole("heading", { level: 1 });
    this.notificationsLink = page.getByRole("link", { name: "Notifications" });
    this.accountMenuButton = page.getByRole("button", { name: "Account Menu" });
    this.openHelpButton = page.getByRole("button", { name: "Open Help" });
    this.desktopNav = page.getByRole("navigation", { name: "main links" });
    this.mobileNav = page.getByRole("navigation", { name: "Main Navigation" });
  }

  /**
   * Uses `textContent`, not `innerText` the heading has
   * `text-transform: uppercase` applied, so `innerText()` (which reflects
   * CSS-rendered text) returns `"WORKSPACE"`, while `textContent()` (raw
   * DOM text, matching the casing every other locator in this class expects)
   * returns `"Workspace"`.
   */
  async getPageHeading(): Promise<string> {
    return (await this.pageHeading.textContent())?.trim() ?? "";
  }

  async isMobileNav(): Promise<boolean> {
    return (await this.mobileNav.count()) > 0;
  }

  /** Works in either desktop or mobile layout — checks which is active and uses the right path. */
  async navigateTo(item: NavItem): Promise<void> {
    if (await this.isMobileNav()) {
      await this.mobileNav.getByRole("button").click();
      await this.page.getByRole("menuitem", { name: new RegExp(`^${item}`) }).click();
    } else {
      await this.desktopNav.getByRole("link", { name: item, exact: true }).click();
    }
  }

  async openAccountMenu(): Promise<void> {
    await this.accountMenuButton.click();
  }

  /** Opens the account menu and clicks "Log Out". */
  async logOut(): Promise<void> {
    await this.openAccountMenu();
    await this.page.getByRole("menuitem", { name: "Log Out" }).click();
  }

  /** Opens the account menu and clicks "Apps" (navigates to `/apps`). */
  async openAppsFromAccountMenu(): Promise<void> {
    await this.openAccountMenu();
    await this.page.getByRole("menuitem", { name: "Apps" }).click();
  }

  /**
   * Opens the account menu and clicks "Messaging" (navigates to `/dashboard`
   * — the same destination as `notificationsLink`, just a second entry point).
   */
  async openMessagingFromAccountMenu(): Promise<void> {
    await this.openAccountMenu();
    await this.page.getByRole("menuitem", { name: "Messaging" }).click();
  }
}
