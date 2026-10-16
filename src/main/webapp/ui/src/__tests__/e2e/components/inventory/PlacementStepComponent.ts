import { expect, type Locator, type Page } from "@playwright/test";
import { ContentGridComponent } from "./ContentGridComponent";

const PROGRESS = /Loading the container…|more locations? in|already occupied|Deselect \d+ locations?|will be placed in/;
const DEAD_END = /Choose another container\.|Choose a grid or list container\.|choose "Leave on my workbench"/;

/** The operation wizard's Location step. */
export class PlacementStepComponent {
  readonly workbenchRadio: Locator;
  readonly containerRadio: Locator;
  readonly searchInput: Locator;
  readonly changeContainerButton: Locator;
  /** "Placing in {name} ({globalId})", shown once a usable container is picked. */
  readonly placingIn: Locator;
  /** Progress and readiness, announced politely. */
  readonly status: Locator;
  /** A container that cannot be used, announced assertively. */
  readonly deadEnd: Locator;
  readonly grid: ContentGridComponent;

  constructor(
    private readonly page: Page,
    private readonly root: Locator,
  ) {
    this.workbenchRadio = root.getByRole("radio", { name: "Leave on my workbench" });
    this.containerRadio = root.getByRole("radio", { name: "Choose a location" });
    this.searchInput = root.getByRole("searchbox", { name: "Search" });
    this.changeContainerButton = root.getByRole("button", { name: "Change container" });
    this.placingIn = root.getByText(/^Placing in /);
    this.status = root.getByRole("status").filter({ hasText: PROGRESS });
    this.deadEnd = root.getByRole("alert").filter({ hasText: DEAD_END });
    this.grid = new ContentGridComponent(page, root);
  }

  async chooseWorkbench(): Promise<void> {
    await this.workbenchRadio.check();
  }

  async chooseContainer(): Promise<void> {
    await this.containerRadio.check();
    await this.searchInput.waitFor({ state: "visible" });
  }

  async search(query: string): Promise<void> {
    await this.searchInput.fill(query);
    await Promise.all([
      this.page.waitForResponse(
        (res) => new URL(res.url()).pathname.endsWith("/api/inventory/v1/search") && res.request().method() === "GET",
      ),
      this.searchInput.press("Enter"),
    ]);
  }

  /**
   * Searches for the container and clicks its name in the results; a click on the row away from the
   * name does not pick it. Waits until the container has loaded, whether it can be used or not.
   */
  async pickContainer(name: string): Promise<void> {
    if (!(await this.searchInput.isVisible())) await this.chooseContainer();
    await this.search(name);
    await this.root.getByRole("treeitem", { name }).getByText(name, { exact: true }).click();
    await expect(this.placingIn.or(this.deadEnd)).toBeVisible();
  }

  async pick(name: string): Promise<void> {
    await this.pickContainer(name);
    await expect(this.placingIn).toContainText(name);
  }

  async changeContainer(): Promise<void> {
    await this.changeContainerButton.click();
    await this.searchInput.waitFor({ state: "visible" });
  }
}
