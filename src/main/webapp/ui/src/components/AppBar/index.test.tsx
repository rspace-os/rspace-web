import { afterEach, beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/useWhoAmI";
import "@/__tests__/__mocks__/useWebSocketNotifications";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { render, screen, waitFor } from "@/__tests__/customQueries";
import { stubAppChrome, type VisibleTabs } from "@/__tests__/helpers/appChrome";
import axios from "@/common/axios";
import { SimplePageWithAppBar } from "./index.story";

const mockAxios = new MockAdapter(axios);

function stubEndpoints(visibleTabs?: Partial<VisibleTabs>) {
  mockAxios.reset();
  stubAppChrome(mockAxios, { visibleTabs });
  // Any other request the bootstrap fires (e.g. whoami when not mocked) should
  // resolve cleanly rather than reject and produce console noise.
  mockAxios.onAny().reply(200, {});
}

/**
 * The avatar button is disabled while the nav data is loading, so wait for the
 * enabled (loaded) state before interacting with the account menu.
 */
async function waitForLoaded() {
  await waitFor(() => {
    expect(screen.getByRole("button", { name: "common:appBar.accountMenu" })).toBeEnabled();
  });
}

describe("App Bar", () => {
  beforeEach(() => {
    stubEndpoints();
  });

  afterEach(() => {
    mockAxios.reset();
  });

  describe("Hidden heading", () => {
    /*
     * The app bar should have a visually hidden heading that is accessible to
     * screen readers when we don't show the heading to all users.
     */
    test("On Workspace, a hidden heading should be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="workspace" />);
      const heading = await screen.findByRole("heading", { level: 1 });
      expect(heading).toHaveTextContent("common:appBar.sections.workspace.title");
    });

    test("On Inventory, a hidden heading should be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="inventory" />);
      const heading = await screen.findByRole("heading", { level: 1 });
      expect(heading).toHaveTextContent("common:appBar.sections.inventory.title");
    });

    test("On Gallery, a hidden heading should be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="gallery" />);
      const heading = await screen.findByRole("heading", { level: 1 });
      expect(heading).toHaveTextContent("common:appBar.sections.gallery.title");
    });

    test("On System, a hidden heading should be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="system" />);
      const heading = await screen.findByRole("heading", { level: 1 });
      expect(heading).toHaveTextContent("common:appBar.sections.system.title");
    });

    test("On My RSpace, a hidden heading should be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="myRSpace" />);
      const heading = await screen.findByRole("heading", { level: 1 });
      expect(heading).toHaveTextContent("common:appBar.sections.myRSpace.title");
    });

    test("On any other page, a hidden heading should not be shown", async () => {
      render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);
      await waitForLoaded();
      expect(screen.queryByRole("heading", { level: 1 })).toBe(null);
      /*
       * The app bar should have a visually hidden heading that is accessible to
       * screen readers when we don't show the heading to all users.
       */
    });
  });

  test("On dialog variant, the heading is always shown", async () => {
    render(<SimplePageWithAppBar variant="dialog" currentPage="Test Page" />);
    const heading = await screen.findByRole("heading", { level: 2 });
    expect(heading).toHaveTextContent("Test Page");
  });

  test("When the user avatar is clicked, a menu should appear with profile and logout options", async () => {
    const user = userEvent.setup();
    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);
    await waitForLoaded();

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));

    const accountMenu = await screen.findByRole("menu", {
      name: "common:appBar.accountMenu",
    });
    expect(accountMenu).toHaveTextContent("user user");
    const logoutOption = await screen.findByRole("menuitem", {
      name: "common:appBar.logOut",
    });
    expect(logoutOption).toBeVisible();
  });

  /*
   * The /uiNavigationData endpoint provides the visibleTabs object, which
   * determines which navigation opions are available in the app bar and the
   * behaviour of the ones that are visible. These options are determined based
   * on the user's permissions and the server configuration.
   */
  describe("visibleTabs", () => {
    test("When visibleTabs.inventory is false, the link should be hidden", async () => {
      stubEndpoints({
        inventory: false,
        myLabGroups: true,
        published: false,
        system: false,
      });
      render(<SimplePageWithAppBar variant="page" />);
      await waitForLoaded();
      expect(screen.queryByRole("link", { name: "common:appBar.sections.inventory.title" })).toBe(null);
      /*
       * This is when the sysadmin has disallowed Inventory entirely.
       */
    });

    test("When visibleTabs.myLabGroups is true, the link should point to /groups/viewPIGroup", async () => {
      stubEndpoints({
        inventory: false,
        myLabGroups: true,
        published: false,
        system: false,
      });
      render(<SimplePageWithAppBar variant="page" />);
      await waitForLoaded();
      const myRSpaceLink = await screen.findByRole("link", {
        name: "common:appBar.sections.myRSpace.title",
      });
      expect(myRSpaceLink.getAttribute("href")).toBe("/groups/viewPIGroup");
      /*
       * For PIs, the MyRSpace link points to the group page for which the user
       * is the PI, which is more helpful to them than their personal page.
       */
    });

    test("When visibleTabs.myLabGroups is false, the link should point to /userform", async () => {
      stubEndpoints({
        inventory: false,
        myLabGroups: false,
        published: false,
        system: false,
      });
      render(<SimplePageWithAppBar variant="page" />);
      await waitForLoaded();
      const myRSpaceLink = await screen.findByRole("link", {
        name: "common:appBar.sections.myRSpace.title",
      });
      expect(myRSpaceLink.getAttribute("href")).toBe("/userform");
      /*
       * For non-PIs, the MyRSpace link points to their account page, which is
       * more helpful to them than the group page.
       */
    });

    test("When visibleTabs.system is false, the link should be hidden", async () => {
      stubEndpoints({
        inventory: false,
        myLabGroups: true,
        published: false,
        system: false,
      });
      render(<SimplePageWithAppBar variant="page" />);
      await waitForLoaded();
      expect(screen.queryByRole("link", { name: "common:appBar.sections.system.title" })).toBe(null);
      /*
       * The System page is only available to sysadmins, so if the user does not
       * have the permissions to view it, it should not be shown.
       */
    });

    test("When visibleTabs.published is false, the menuitem should be hidden", async () => {
      const user = userEvent.setup();
      stubEndpoints({
        inventory: false,
        myLabGroups: true,
        published: false,
        system: false,
      });
      render(<SimplePageWithAppBar variant="page" />);
      await waitForLoaded();
      await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
      await screen.findByRole("menu", { name: "common:appBar.accountMenu" });
      expect(screen.queryByRole("menuitem", { name: "common:appBar.published" })).toBe(null);
      /*
       * The published page is another piece of functionality that the sysadmin
       * can disable for all users, so if it has not been enabled then the
       * menuitem should not be shown.
       */
    });
  });

  test("On page variant, the icons on the right should be in the correct order", async () => {
    render(<SimplePageWithAppBar variant="page" accessibilityTips={{ supportsHighContrastMode: true }} />);
    await waitForLoaded();

    const accountMenuButton = screen.getByRole("button", {
      name: "common:appBar.accountMenu",
    });
    expect(accountMenuButton).toBeVisible();
    const helpMenuButton = screen.getByRole("button", { name: "common:helpDocs.openHelp" });
    expect(helpMenuButton).toBeVisible();

    const accountBeforeHelp = Boolean(
      accountMenuButton.compareDocumentPosition(helpMenuButton) & Node.DOCUMENT_POSITION_FOLLOWING,
    );
    expect(accountBeforeHelp, "Account button should be before Help button").toBe(true);
    /*
     * This is so that across the different variants the help button remains in a
     * consistent location -- the furthest right -- as having help be in a
     * consistent location across the entire product is an a11y requirement.
     */
  });

  test("On dialog variant, the icons on the right should be in the correct order", async () => {
    render(<SimplePageWithAppBar variant="dialog" accessibilityTips={{ supportsHighContrastMode: true }} />);

    const accessibilityTipsButton = await screen.findByRole("button", {
      name: "common:accessibilityTips.buttonLabel",
    });
    expect(accessibilityTipsButton).toBeVisible();
    const helpMenuButton = screen.getByRole("button", { name: "common:helpDocs.openHelp" });
    expect(helpMenuButton).toBeVisible();

    const accessibilityTipsBeforeHelp = Boolean(
      accessibilityTipsButton.compareDocumentPosition(helpMenuButton) & Node.DOCUMENT_POSITION_FOLLOWING,
    );
    expect(accessibilityTipsBeforeHelp, "Accessibility Tips button should be before Help button").toBe(true);
    /*
     * This is so that across the different variants the help button remains in a
     * consistent location -- the furthest right -- as having help be in a
     * consistent location across the entire product is an a11y requirement.
     */
  });
});
