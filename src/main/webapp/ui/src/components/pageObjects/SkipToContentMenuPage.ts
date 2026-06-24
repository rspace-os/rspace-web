import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for SkipToContentMenu, as mounted by the stories in
 * SkipToContentMenu.story.tsx. Encapsulates locators and user interactions;
 * assertions live in the specs themselves.
 */
export class SkipToContentMenuPage {
  /** The navigation menu container (role="menu"). */
  get menu(): Locator {
    return page.getByRole("menu", { name: "Skip to content navigation" });
  }

  /** The skip button for a given landmark name. */
  skipButton(landmarkName: string): Locator {
    return page.getByRole("button", { name: `Skip to ${landmarkName}` });
  }

  /**
   * Returns a locator for the focusable content box of the given landmark.
   * The TestLandmark story component renders the box as a labelled region
   * (`role="region"` + `aria-label="{name} Content"`), which is also the
   * element that receives focus, so we can locate it semantically by role+name.
   */
  landmarkContent(landmarkName: string): Locator {
    return page.getByRole("region", { name: `${landmarkName} Content`, exact: true });
  }

  /**
   * Focuses the skip button for the given landmark (defaults to "Header").
   * Vitest browser Locator has no .focus() method — we use the underlying DOM
   * element to programmatically trigger focus.
   */
  focusSkipButton(landmarkName = "Header"): void {
    (this.skipButton(landmarkName).element() as HTMLElement).focus();
  }

  /**
   * Activates the skip button for the given landmark via keyboard Enter.
   * Focuses it first, then sends Enter so the component navigates to the landmark.
   */
  async activateSkipButton(landmarkName: string): Promise<void> {
    (this.skipButton(landmarkName).element() as HTMLElement).focus();
    await userEvent.keyboard("{Enter}");
  }

  /** Presses the ArrowDown key. */
  async pressArrowDown(): Promise<void> {
    await userEvent.keyboard("{ArrowDown}");
  }

  /** Presses the ArrowUp key. */
  async pressArrowUp(): Promise<void> {
    await userEvent.keyboard("{ArrowUp}");
  }

  /** Presses the Escape key. */
  async pressEscape(): Promise<void> {
    await userEvent.keyboard("{Escape}");
  }

  /** Clicks the "Show Extra Landmarks" button in the DynamicLandmarksExample. */
  async showExtraLandmarks(): Promise<void> {
    await page.getByRole("button", { name: "Show Extra Landmarks" }).click();
  }
}
