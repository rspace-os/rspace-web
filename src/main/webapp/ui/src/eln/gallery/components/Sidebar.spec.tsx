import { menuClasses } from "@mui/material/Menu";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { SidebarPage } from "./pageObjects/SidebarPage";
import { CreateMenuStory, DMPToolCreateMenuStory } from "./Sidebar.story";

const sidebar = new SidebarPage();

beforeEach(() => {
  worker.use(
    http.get("/apps/dmptool/baseUrlHost", () => HttpResponse.text("https://dmptool.org")),
    http.get("/apps/dmptool/plans", () => HttpResponse.json({ success: true, data: { items: [] } })),
  );
});

afterEach(cleanup);

/*
 * PRT-1118 and PRT-1135 cover mui/material-ui#32286. A Gallery Create menu could remain mounted
 * after its exit, leaving an invisible backdrop and leaked `aria-hidden` attributes. MUI 9.3.1
 * fixes the issue in PR #48881. DialogBoundary.spec.tsx verifies both upstream triggers.
 *
 * These tests verify removal of the `pointerEvents: "none"` workaround. The open menu remains
 * interactive, stays mounted below a DMP dialog, and unmounts after the dialog closes.
 */
describe("Gallery create menu (DMP enabled)", () => {
  test("the open menu and its DMP option are interactive", async () => {
    render(<CreateMenuStory />);

    await sidebar.openCreateMenu();
    await expect.element(sidebar.dmptool).toBeVisible();

    const menuRoot = sidebar.menu.element().closest(`.${menuClasses.root}`);
    expect(menuRoot).not.toBeNull();
    expect(getComputedStyle(menuRoot as Element).pointerEvents).not.toBe("none");
    expect(getComputedStyle(sidebar.dmptool.element()).pointerEvents).not.toBe("none");
  });

  test("the Gallery picker keeps the create menu open beneath the DMP dialog", async () => {
    render(<DMPToolCreateMenuStory isPicker />);

    await sidebar.openCreateMenu();
    const menuZIndex = sidebar.modalZIndex(sidebar.menu);
    await sidebar.dmptool.click();

    await expect.element(sidebar.dmpDialog).toBeVisible();
    expect(sidebar.modalZIndex(sidebar.dmpDialog)).toBeGreaterThan(menuZIndex);
    /* Count menu lists because ModalManager hides the menu from role queries (RSDEV-1317). */
    await expect.poll(() => sidebar.mountedMenuCount()).toBe(1);
  });

  test("the Gallery SPA keeps the create menu open until the DMP dialog closes", async () => {
    render(<DMPToolCreateMenuStory isPicker={false} />);

    await sidebar.openCreateMenu();
    const menuZIndex = sidebar.modalZIndex(sidebar.menu);
    await sidebar.dmptool.click();

    await expect.element(sidebar.dmpDialog).toBeVisible();
    expect(sidebar.modalZIndex(sidebar.dmpDialog)).toBeGreaterThan(menuZIndex);
    await expect.poll(() => sidebar.mountedMenuCount()).toBe(1);

    await sidebar.closeDmpDialog.click();
    await expect.element(sidebar.dmpDialog).not.toBeInTheDocument();
    /* Role queries cannot distinguish an unmounted menu from an aria-hidden menu (PRT-1135). */
    await expect.poll(() => sidebar.mountedMenuCount()).toBe(0);
  });
});
