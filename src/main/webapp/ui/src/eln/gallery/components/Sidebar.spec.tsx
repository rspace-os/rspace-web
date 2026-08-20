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
 * PRT-1118 / PRT-1135. With a DMP integration enabled, the Gallery create menu
 * could survive its own exit transition: the Modal stayed mounted with an
 * invisible backdrop intercepting every click (PRT-1118), and the aria-hidden
 * it had applied to the rest of the page was never lifted (PRT-1135). Root
 * cause was mui/material-ui#32286, fixed upstream in @mui/material 9.3.1 by
 * PR #48881; see components/DialogBoundary.spec.tsx for the A/B that verifies
 * it. The `pointerEvents: "none"` workaround this file used to guard has been
 * removed now that the exit completes reliably.
 *
 * These tests remain the regression gate for that removal: the OPEN menu must
 * stay interactive, and the menu must unmount once the DMP dialog closes.
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
    await expect.element(sidebar.menu).toBeVisible();
  });

  test("the Gallery SPA keeps the create menu open until the DMP dialog closes", async () => {
    render(<DMPToolCreateMenuStory isPicker={false} />);

    await sidebar.openCreateMenu();
    const menuZIndex = sidebar.modalZIndex(sidebar.menu);
    await sidebar.dmptool.click();

    await expect.element(sidebar.dmpDialog).toBeVisible();
    expect(sidebar.modalZIndex(sidebar.dmpDialog)).toBeGreaterThan(menuZIndex);
    await expect.element(sidebar.menu).toBeVisible();

    await sidebar.closeDmpDialog.click();
    await expect.element(sidebar.dmpDialog).not.toBeInTheDocument();
    await expect.element(sidebar.menu).not.toBeInTheDocument();
  });
});
