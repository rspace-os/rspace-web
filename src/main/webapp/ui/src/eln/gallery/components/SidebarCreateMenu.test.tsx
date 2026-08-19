import MenuItem from "@mui/material/MenuItem";
import { modalClasses } from "@mui/material/Modal";
import { render } from "@testing-library/react";
import { afterAll, describe, expect, test } from "vitest";
import SidebarCreateMenu from "./SidebarCreateMenu";

const anchor = document.createElement("button");
document.body.appendChild(anchor);
afterAll(() => {
  anchor.remove();
});

/**
 * PRT-1135. Clicking Close on a DMP import dialog clears the menu's anchor, so
 * the menu is closed as far as React is concerned, but it could still be left
 * on screen: a re-render landing while the Menu is mid-exit cancels
 * react-transition-group's `onExited` (mui/material-ui#32286), and MUI only
 * unmounts or hides a Modal once it has reached its `exited` state.
 */
describe("SidebarCreateMenu", () => {
  test("leaves nothing in the DOM as soon as it closes, without waiting for an exit transition", () => {
    const menu = (anchorEl: HTMLElement | null) => (
      <SidebarCreateMenu anchorEl={anchorEl} onClose={() => {}}>
        <MenuItem aria-label="upload" />
      </SidebarCreateMenu>
    );

    const { rerender, baseElement } = render(menu(anchor));
    expect(baseElement.querySelector(`.${modalClasses.root}`)).not.toBeNull();

    rerender(menu(null));

    expect(baseElement.querySelector(`.${modalClasses.root}`)).toBeNull();
  });
});
