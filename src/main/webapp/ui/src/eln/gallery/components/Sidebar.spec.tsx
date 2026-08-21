import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { SidebarPage } from "./pageObjects/SidebarPage";
import { DMPToolCreateMenuStory } from "./Sidebar.story";

const sidebar = new SidebarPage();

beforeEach(() => {
  worker.use(
    http.get("/apps/dmptool/baseUrlHost", () => HttpResponse.text("https://dmptool.org")),
    http.get("/apps/dmptool/plans", () => HttpResponse.json({ success: true, data: { items: [] } })),
  );
});

afterEach(cleanup);

describe("Gallery create menu (DMP enabled)", () => {
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
