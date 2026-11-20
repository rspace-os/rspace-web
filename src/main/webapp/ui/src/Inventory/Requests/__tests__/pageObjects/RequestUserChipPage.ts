import { type Locator, page } from "vitest/browser";
import { LONG_NAME } from "../RequestUserChip.story";

export class RequestUserChipPage {
  get chip(): Locator {
    return page.getByRole("button", { name: `${LONG_NAME.first} ${LONG_NAME.last}` });
  }

  get table(): Locator {
    return page.getByRole("table");
  }

  /** MUI's Chip label span, which holds the name and carries the overflow/ellipsis CSS. */
  get chipLabel(): Locator {
    return this.chip.getByText(`${LONG_NAME.first} ${LONG_NAME.last}`, { exact: true });
  }
}
