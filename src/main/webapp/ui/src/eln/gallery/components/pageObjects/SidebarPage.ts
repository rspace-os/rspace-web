import { modalClasses } from "@mui/material/Modal";
import { type Locator, page } from "vitest/browser";

export class SidebarPage {
  get createButton(): Locator {
    return page.getByRole("button", { name: "Create" });
  }

  get menu(): Locator {
    return page.getByRole("menu");
  }

  get dmptool(): Locator {
    return page.getByRole("menuitem", { name: /dmptool/i });
  }

  get dmpDialog(): Locator {
    return page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "DMPTool" }) });
  }

  get closeDmpDialog(): Locator {
    return page.getByRole("button", { name: "Close" });
  }

  modalZIndex(locator: Locator): number {
    // MUI applies z-index to the shared Modal root, not the semantic menu/dialog element.
    const modal = locator.element().closest(`.${modalClasses.root}`);
    if (!modal) throw new Error("Expected element to be rendered inside a modal");
    const computedZIndex = getComputedStyle(modal).zIndex;
    const zIndex = Number.parseInt(computedZIndex, 10);
    if (Number.isNaN(zIndex)) throw new Error(`Expected modal z-index to be numeric, received "${computedZIndex}"`);
    return zIndex;
  }

  async openCreateMenu(): Promise<void> {
    await this.createButton.click();
  }
}
