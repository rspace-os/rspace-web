import { menuClasses } from "@mui/material/Menu";
import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { SidebarPage } from "./pageObjects/SidebarPage";
import { CreateMenuStory } from "./Sidebar.story";

const sidebar = new SidebarPage();

afterEach(cleanup);

/*
 * PRT-1118. With a DMP integration enabled, dismissing the Gallery create menu
 * froze the page: a re-render landing while the menu was mid-exit cancelled
 * react-transition-group's `onExited` (mui/material-ui#32286), so the menu's
 * Modal never unmounted and its invisible (opacity-0) backdrop kept intercepting
 * every click. The fix (Sidebar.tsx) makes the closed menu click-through.
 *
 * The freeze only manifests under a production React build (`-DgenerateReactDist`,
 * not `-DreactDevMode`, which StrictMode-masks it) and the underlying race is too
 * timing-dependent to reproduce deterministically here, so there is no automated
 * test for the frozen state. This spec guards the deterministic half in the
 * normal browser suite: the OPEN menu must stay interactive (the pointer-events
 * condition must never be inverted onto the open state).
 */
describe("Gallery create menu (DMP enabled)", () => {
  test("the open menu and its DMP option are interactive (pointer-events not disabled)", async () => {
    render(<CreateMenuStory />);

    await sidebar.openCreateMenu();
    await expect.element(sidebar.dmptool).toBeVisible();

    const menuRoot = sidebar.menu.element().closest(`.${menuClasses.root}`);
    expect(menuRoot).not.toBeNull();
    expect(getComputedStyle(menuRoot as Element).pointerEvents).not.toBe("none");
    expect(getComputedStyle(sidebar.dmptool.element()).pointerEvents).not.toBe("none");
  });
});
