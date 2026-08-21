import { menuClasses } from "@mui/material/Menu";
import { modalClasses } from "@mui/material/Modal";
import { type Locator, page } from "vitest/browser";

export class SidebarPage {
  get createButton(): Locator {
    return page.getByRole("button", { name: "Create" });
  }

  get menu(): Locator {
    return page.getByRole("menu", { name: "Create", exact: true });
  }

  /**
   * How many menu lists are mounted, found by class rather than by role.
   *
   * While a modal Dialog is open above it the menu is legitimately
   * `aria-hidden`, so `getByRole("menu")` cannot see it -- which makes a role
   * query useless for asking "is the menu still mounted?", and makes a
   * role-based `not.toBeInTheDocument()` pass whether the menu was unmounted or
   * merely left hidden. Counting elements distinguishes the two.
   */
  mountedMenuCount(): number {
    return document.querySelectorAll(`.${menuClasses.list}`).length;
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
