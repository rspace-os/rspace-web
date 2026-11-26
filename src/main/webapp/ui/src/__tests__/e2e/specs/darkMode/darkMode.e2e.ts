import { expect, type Page } from "@playwright/test";
import type { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import type { FoldersClient } from "@/__tests__/e2e/api/clients/FoldersClient";
import {
  backgroundLuminance,
  colourContrastViolations,
  darkSwitches,
  expectDarkMode,
  expectDarkPage,
  expectLightMode,
  expectLightPageWithoutDarkSheets,
  MODE_COOKIE,
  modeCookie,
  recordDarkSwitches,
  storedMode,
  textContrast,
} from "@/__tests__/e2e/assertions/colorMode";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/colorMode";
import type { NotebookPage } from "@/__tests__/e2e/pageObjects/notebook/NotebookPage";
import type { WorkspacePage } from "@/__tests__/e2e/pageObjects/workspace/WorkspacePage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

type Setup = {
  page: Page;
  pageWorkspace: WorkspacePage;
  pageNotebook: NotebookPage;
  clientDocuments: DocumentsClient;
  clientFolders: FoldersClient;
};

type PageUnderTest = { name: string; open: (setup: Setup) => Promise<void> };

/** Opens a page and waits for the requests that fill it, so axe sees the finished page. */
function path(name: string, url: string): PageUnderTest {
  return {
    name,
    open: async ({ page }) => {
      await page.goto(url);
      await page.waitForLoadState("networkidle");
    },
  };
}

async function createNotebook({ page, pageNotebook, clientDocuments, clientFolders }: Setup): Promise<void> {
  const notebook = await clientFolders.create({ name: uniqueName("DarkModeNotebook"), notebook: true });
  await clientDocuments.create({
    name: uniqueName("DarkModeEntry"),
    parentFolderId: notebook.id,
    fields: [{ content: "<p>Notebook entry text</p>" }],
  });
  await page.goto(`/notebookEditor/${notebook.id}`);
  await pageNotebook.isLoaded();
  await page.waitForLoadState("networkidle");
}

const USER_PAGES: PageUnderTest[] = [
  path("Workspace", "/workspace"),
  {
    name: "the document editor",
    open: async ({ page, pageWorkspace }) => {
      await pageWorkspace.open();
      await pageWorkspace.createBasicDocument();
      await page.waitForLoadState("networkidle");
    },
  },
  { name: "a notebook", open: createNotebook },
  path("My RSpace", "/groups/viewPIGroup"),
  path("the profile page", "/userform"),
  path("Gallery", "/gallery"),
  path("Inventory", "/inventory"),
  path("Apps", "/apps"),
  path("the form editor", "/workspace/editor/form/"),
  path("the forms list", "/workspace/editor/form/list"),
];

const SYSADMIN_PAGES: PageUnderTest[] = [
  path("the System users page", "/system"),
  path("the System configuration page", "/system/config"),
  path("the System groups page", "/system/groups/list"),
];

const LOGGED_OUT_PAGES: PageUnderTest[] = [
  path("the login page", "/login"),
  path("the signup page", "/signup"),
  path("the password reset page", "/public/requestPasswordReset"),
];

const PLUGIN_TITLE = "Dilution calculator";
const PLUGIN_URL = "/scripts/externalTinymcePlugins/tools/dilution-calculator.html";

function describePages(role: string, pages: PageUnderTest[]): void {
  test.describe("in dark mode", () => {
    test.use({ colorMode: "dark" });
    for (const entry of pages) {
      test(`As ${role}, I see ${entry.name} dark with legible text`, async ({
        page,
        pageErrors,
        pageWorkspace,
        pageNotebook,
        clientDocuments,
        clientFolders,
      }) => {
        await entry.open({ page, pageWorkspace, pageNotebook, clientDocuments, clientFolders });
        await expectDarkPage(page);
        expect(await colourContrastViolations(page)).toEqual([]);
        expect(pageErrors).toEqual([]);
      });
    }
  });

  test.describe("in light mode", () => {
    test.use({ colorMode: "light" });
    for (const entry of pages) {
      test(`As ${role}, I see ${entry.name} light without dark stylesheets`, async ({
        page,
        pageErrors,
        pageWorkspace,
        pageNotebook,
        clientDocuments,
        clientFolders,
      }) => {
        await entry.open({ page, pageWorkspace, pageNotebook, clientDocuments, clientFolders });
        await expectLightPageWithoutDarkSheets(page);
        expect(pageErrors).toEqual([]);
      });
    }
  });
}

test.describe("Dark mode", { tag: tags.DARK_MODE }, () => {
  test.describe("pages as a user", () => {
    describePages("a user", USER_PAGES);
  });

  test.describe("pages as a sysadmin", () => {
    test.use({ storageState: storageStatePath(SYSADMIN.username) });
    describePages("a sysadmin", SYSADMIN_PAGES);
  });

  test.describe("pages when logged out", () => {
    test.use({ storageState: { cookies: [], origins: [] } });
    describePages("a visitor", LOGGED_OUT_PAGES);
  });

  test.describe("switching", () => {
    test.use({ colorMode: "light" });

    test("As a user, I can switch between light, dark and system mode from the account menu, without a reload, and my other tabs follow", async ({
      page,
      pageWorkspace,
      componentColorModeMenu,
      secondTab,
    }) => {
      await pageWorkspace.open();
      await secondTab.page.goto("/gallery");
      await expectLightMode(secondTab.page);
      await page.evaluate(() => {
        (window as unknown as { e2eNotReloaded: boolean }).e2eNotReloaded = true;
      });

      await componentColorModeMenu.choose("dark");
      await expectDarkPage(page);
      await expectDarkPage(secondTab.page);
      expect(await storedMode(page)).toBe("dark");
      expect(await modeCookie(page)).toBe("dark");

      // The browser reports a light system setting, so system mode shows light.
      await componentColorModeMenu.choose("system");
      await expectLightMode(page);
      await expectLightMode(secondTab.page);
      expect(await storedMode(page)).toBe("system");
      expect(await modeCookie(page)).toBe("system");

      await page.emulateMedia({ colorScheme: "dark" });
      await expectDarkPage(page);
      await componentColorModeMenu.open();
      await expect(componentColorModeMenu.switchToLight).toBeVisible();
      await expect(componentColorModeMenu.useSystem).toHaveCount(0);
      await componentColorModeMenu.close();

      expect(await page.evaluate(() => (window as unknown as { e2eNotReloaded?: boolean }).e2eNotReloaded)).toBe(true);
    });

    test("As a user, switching to dark never shows the page dark before its dark stylesheets have loaded", async ({
      page,
      pageWorkspace,
      componentColorModeMenu,
    }) => {
      await recordDarkSwitches(page);
      await pageWorkspace.open();
      await componentColorModeMenu.choose("dark");
      await expectDarkPage(page);
      expect(await darkSwitches(page)).toEqual([{ pendingSheets: [], hidden: false }]);
    });
  });

  test.describe("with an out-of-date mode cookie", () => {
    test.use({ colorMode: "dark" });

    test("As a user, the page stays hidden until the dark stylesheets it adds have loaded", async ({
      page,
      context,
      pageWorkspace,
    }) => {
      // Stored dark, but the cookie says light, so the server renders no dark stylesheets.
      await context.addCookies([{ name: MODE_COOKIE, value: "light", url: env.baseURL }]);
      await recordDarkSwitches(page);
      await pageWorkspace.open();
      await expectDarkPage(page);
      const switches = await darkSwitches(page);
      expect(switches.length).toBeGreaterThan(0);
      for (const change of switches) {
        expect(change.pendingSheets.length === 0 || change.hidden, JSON.stringify(change)).toBe(true);
      }
      await expect(page.locator("html")).not.toHaveAttribute("style", /visibility:\s*hidden/);
    });
  });

  test.describe("printing", () => {
    test.use({ colorMode: "dark" });

    for (const url of ["/workspace", "/gallery"]) {
      test(`As a user, printing ${url} is always light and leaves my mode alone`, async ({ page, secondTab }) => {
        await page.goto(url);
        await page.waitForLoadState("networkidle");
        await secondTab.page.goto("/workspace");
        await expectDarkPage(page);

        const duringPrint = await page.evaluate(() => {
          window.dispatchEvent(new Event("beforeprint"));
          return document.documentElement.className;
        });
        expect(duringPrint.split(/\s+/)).toContain("light");
        expect(duringPrint.split(/\s+/)).not.toContain("dark");
        expect(await backgroundLuminance(page.locator("body"))).toBeGreaterThan(0.8);
        expect(await storedMode(page)).toBe("dark");
        await expectDarkMode(secondTab.page);

        await page.evaluate(() => window.dispatchEvent(new Event("afterprint")));
        await expectDarkPage(page);

        await page.emulateMedia({ media: "print" });
        expect(await backgroundLuminance(page.locator("body")), "body background in print media").toBeGreaterThan(0.8);
      });
    }
  });

  test.describe("the editor", () => {
    test.describe("from dark", () => {
      test.use({ colorMode: "dark" });

      test("As a user, a plugin dialog follows my mode when it opens and when I switch", async ({
        page,
        pageWorkspace,
        componentTinyMcePluginDialog,
        secondTab,
      }) => {
        await pageWorkspace.open();
        await pageWorkspace.createBasicDocument();
        const frame = await componentTinyMcePluginDialog.openUrl(PLUGIN_TITLE, PLUGIN_URL);
        await expectDarkMode(frame);

        // The dialog is modal, so the mode changes from another tab.
        await secondTab.page.goto("/workspace");
        await secondTab.colorModeMenu.choose("light");
        await expectLightMode(page);
        await expectLightMode(frame);

        await secondTab.colorModeMenu.choose("dark");
        await expectDarkMode(page);
        await expectDarkMode(frame);
        const unloaded = await frame
          .locator("link[data-dark-mode-sheet]")
          .evaluateAll((links) => links.filter((link) => !(link as HTMLLinkElement).sheet).length);
        expect(unloaded).toBe(0);
      });
    });

    test.describe("from light", () => {
      test.use({ colorMode: "light" });

      test("As a user, an open editor switches to the dark skin and back while its content stays white", async ({
        page,
        pageWorkspace,
        componentColorModeMenu,
      }) => {
        await pageWorkspace.open();
        await pageWorkspace.createBasicDocument();
        const lightSkin = page.locator('link[href*="/skins/ui/oxide/"]');
        const darkSkin = page.locator('link[href*="/skins/ui/oxide-dark/"]');
        // TinyMCE's editing area is an iframe with no accessible name; its class is stable.
        const content = page.locator("iframe.tox-edit-area__iframe").first().contentFrame().locator("body");
        await expect(lightSkin).not.toHaveCount(0);
        await expect(darkSkin).toHaveCount(0);

        await componentColorModeMenu.choose("dark");
        await expectDarkMode(page);
        await expect(darkSkin).not.toHaveCount(0);
        await expect(lightSkin).toHaveCount(0);
        expect(await backgroundLuminance(content), "editor content background").toBeGreaterThan(0.9);

        await componentColorModeMenu.choose("light");
        await expectLightMode(page);
        await expect(lightSkin).not.toHaveCount(0);
        await expect(darkSkin).toHaveCount(0);
      });
    });
  });

  test.describe("user content", () => {
    test.use({ colorMode: "dark" });

    test("As a user, document and notebook content stays white in dark mode", async ({
      page,
      pageWorkspace,
      pageNotebook,
      clientDocuments,
      clientFolders,
    }) => {
      const doc = await clientDocuments.create({
        name: uniqueName("DarkModeContent"),
        fields: [{ content: "<p>Document text</p>" }],
      });
      await pageWorkspace.openDocument(doc.id);
      await expectDarkPage(page);
      // Legacy view-mode field container; it has no role or stable id.
      expect(await backgroundLuminance(page.locator(".textFieldViewModeDiv").first())).toBeGreaterThan(0.9);

      await createNotebook({ page, pageWorkspace, pageNotebook, clientDocuments, clientFolders });
      await expectDarkPage(page);
      // Legacy notebook entry container; it has no role or stable id.
      expect(await backgroundLuminance(page.locator(".journalPageContent").first())).toBeGreaterThan(0.9);
    });
  });

  test.describe("in a browser without CSS @scope", () => {
    test.use({ colorMode: "dark" });
    test.beforeEach(async ({ context }) => {
      await context.addInitScript(() => {
        delete (window as { CSSScopeRule?: unknown }).CSSScopeRule;
      });
    });

    test("As a user, pages React does not render stay light and the account menu says why", async ({
      page,
      pageWorkspace,
      componentColorModeMenu,
    }) => {
      await pageWorkspace.open();
      await expect(page.locator("html")).toHaveAttribute("data-dark-mode-unsupported", "");
      await expectLightMode(page);
      expect(await backgroundLuminance(page.locator("body"))).toBeGreaterThan(0.8);

      await componentColorModeMenu.open();
      const item = componentColorModeMenu.switchToDark;
      await expect(item).toBeDisabled();
      await expect(item).toContainText("This page needs a newer browser");
      await expect(componentColorModeMenu.useSystem).toHaveCount(0);
      expect(await textContrast(item.getByText("Switch to dark mode", { exact: true }))).toBeGreaterThanOrEqual(4.5);
      expect(await textContrast(item.getByText("This page needs a newer browser"))).toBeGreaterThanOrEqual(4.5);
    });

    test("As a user, pages React renders still show dark", async ({ page }) => {
      await page.goto("/gallery");
      await page.waitForLoadState("networkidle");
      await expectDarkPage(page);
    });

    test.describe("when logged out", () => {
      test.use({ storageState: { cookies: [], origins: [] } });

      test("As a visitor, the Theme select is hidden", async ({ page, componentColorModeSelect }) => {
        await page.goto("/login");
        await expectLightMode(page);
        await expect(componentColorModeSelect.select).toBeHidden();
      });
    });
  });

  test.describe("the logged-out Theme select", () => {
    test.use({ storageState: { cookies: [], origins: [] } });

    test("As a visitor, I can choose a theme and it persists across a reload", async ({
      page,
      pageLogin,
      componentColorModeSelect,
    }) => {
      await recordDarkSwitches(page);
      await pageLogin.open();
      await expect(componentColorModeSelect.select).toHaveValue("system");
      await expectLightMode(page);

      await componentColorModeSelect.choose("Dark");
      await expectDarkPage(page);
      expect(await darkSwitches(page), "switches before the dark stylesheets loaded").toEqual([
        { pendingSheets: [], hidden: false },
      ]);
      await page.reload();
      await expectDarkPage(page);
      await expect(componentColorModeSelect.select).toHaveValue("dark");

      await componentColorModeSelect.choose("Light");
      await expectLightMode(page);
      await page.reload();
      await expectLightPageWithoutDarkSheets(page);
      await expect(componentColorModeSelect.select).toHaveValue("light");
    });
  });
});
