import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Page object for the InfoCard, as mounted by the story in InfoCard.story.tsx.
 */
export class InfoCardPage {
  // The badge has no accessible name; MUI tags its icon with this test id.
  get badge(): Locator {
    return page.getByTestId("InfoOutlinedIcon");
  }

  get card(): Locator {
    return page.getByRole("tooltip");
  }

  get openButton(): Locator {
    return page.getByRole("button", { name: "Open" });
  }

  descriptionText(text: string): Locator {
    return page.getByText(text, { exact: true });
  }

  async open(): Promise<void> {
    await userEvent.hover(this.badge);
  }
}
