import { expect, type Locator, type Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";
import { waitForImageDecoded } from "./EditLocationsDialog";

export class MoveDialogComponent {
  readonly root: Locator;
  private readonly destinationTree: Locator;
  private readonly selectedDestinationHeading: Locator;
  private readonly cancelButton: Locator;
  private readonly moveButton: Locator;
  private readonly makeTopLevelButton: Locator;
  private readonly searchInput: Locator;
  private readonly nextButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Moving " });
    this.destinationTree = this.root.getByRole("tree");
    this.selectedDestinationHeading = this.root.getByRole("heading", { level: 3, name: "Selected Destination" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
    this.moveButton = this.root.getByRole("button", { name: "Move", exact: true });
    this.makeTopLevelButton = this.root.getByRole("button", { name: "Make Top-level" });
    this.searchInput = this.root.getByRole("searchbox", { name: "Search" });
    // Avoid the tree's "Go to next page" button.
    this.nextButton = this.root.getByRole("button", { name: "Next", exact: true });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
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

  async selectDestination(name: string): Promise<void> {
    const item = this.destinationTree.getByRole("treeitem", { name }).first();
    await expect(async () => {
      await item.click({ timeout: 3_000 }).catch(() => {});
      await expect(item).toHaveAttribute("aria-checked", "true", { timeout: 3_000 });
    }).toPass({ timeout: 30_000 });
  }

  async selectedDestinationName(): Promise<string> {
    return this.selectedDestinationHeading.innerText();
  }

  async selectLocations(count: number): Promise<void> {
    if (await this.nextButton.isVisible().catch(() => false)) {
      await this.nextButton.click();
    }
    const progressAlert = this.root.getByRole("alert");
    await progressAlert.waitFor({ state: "visible" });
    const image = this.root.getByAltText("Preview of ").first();
    await waitForImageDecoded(this.page, image);

    const warmupPoint = await image
      .boundingBox()
      .then((box) => (box ? { x: box.x + box.width / 2, y: box.y + box.height / 2 } : null));
    if (warmupPoint) {
      await this.page.mouse.click(warmupPoint.x, warmupPoint.y);
    }

    const readPlacedCount = () =>
      progressAlert.innerText().then((text) => Number(/\((\d+)\/\d+ placed\)/.exec(text)?.[1] ?? 0));

    let openLocations: Array<{ x: number; y: number }> = [];
    await expect(async () => {
      const imageBox = await image.boundingBox();
      if (!imageBox) {
        throw new Error("Locations image is not visible in the Move dialog.");
      }
      const candidates = this.root.getByText(/^[1-9]\d*$/);
      const candidateCount = await candidates.count();
      const found: Array<{ x: number; y: number }> = [];
      for (let j = 0; j < candidateCount; j++) {
        const box = await candidates.nth(j).boundingBox();
        if (
          box &&
          box.x >= imageBox.x &&
          box.x <= imageBox.x + imageBox.width &&
          box.y >= imageBox.y &&
          box.y <= imageBox.y + imageBox.height
        ) {
          const x = box.x + box.width / 2;
          const y = box.y + box.height / 2;

          const isWarmupTarget =
            warmupPoint && Math.abs(x - warmupPoint.x) < box.width && Math.abs(y - warmupPoint.y) < box.height;
          if (!isWarmupTarget) {
            found.push({ x, y });
          }
        }
      }
      const stillNeeded = count - (await readPlacedCount());
      if (found.length < stillNeeded) {
        throw new Error(`Only ${found.length} open location(s) found in the Move dialog, need ${stillNeeded}.`);
      }
      openLocations = found.slice(0, stillNeeded);
    }).toPass({ timeout: 15_000 });

    let placed = await readPlacedCount();
    for (const { x, y } of openLocations) {
      await this.page.mouse.click(x, y);
      placed += 1;

      const expected = placed === count ? "Selection complete." : `(${placed}/${count} placed)`;
      await expect(progressAlert).toContainText(expected, { timeout: 45_000 });
    }
  }

  async makeTopLevel(): Promise<void> {
    await this.makeTopLevelButton.click();
  }

  async confirmMove(): Promise<void> {
    if (await this.nextButton.isVisible().catch(() => false)) {
      await this.nextButton.click();
    }
    await expect(async () => {
      await this.moveButton.click({ timeout: 3_000 }).catch(() => {});
      await this.root.waitFor({ state: "detached", timeout: 3_000 });
    }).toPass({ timeout: 30_000 });
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
