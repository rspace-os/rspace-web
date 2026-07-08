import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render, screen, waitFor, within } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import { DefaultSidebar, S3_FILESTORE_ID, S3FilestoreSidebar } from "./Sidebar.story";

const mockAxios = new MockAdapter(axios);

/**
 * Mirror the network stubs the Playwright spec installed via `router.route`,
 * for the endpoints the Sidebar (and its providers) hit on mount. All of these
 * calls go through `@/common/axios`, so they are mocked via MockAdapter rather
 * than the global fetch mock.
 */
function mockNetwork() {
  // Analytics provider
  mockAxios.onGet("/session/ajax/analyticsProperties").reply(200, { analyticsEnabled: false });

  // UiPreferences provider
  mockAxios.onGet("/userform/ajax/preference").reply(200, {});
  mockAxios.onPost("/userform/ajax/preference").reply(200, {});

  // Deployment property lookup (netfilestores.enabled), used by Sidebar +
  // AddFilestoreMenuItem
  mockAxios.onGet("/deploymentproperties/ajax/property").reply(200, false);

  // Folder creation endpoint (galleryApi baseURL is /gallery/ajax)
  mockAxios.onPost("/gallery/ajax/createFolder").reply(200, {
    data: true,
    error: null,
    success: true,
    errorMsg: null,
  });

  // S3 filestore folder creation endpoint
  mockAxios.onPost(`/api/v1/gallery/filestores/${S3_FILESTORE_ID}/folder`).reply(200, {
    data: "my-bucket/test/",
  });

  // DMP integration status lookups (DmpMenuSection)
  const integrationInfo = (
    name: string,
    overrides: Partial<{
      displayName: string;
      available: boolean;
      enabled: boolean;
    }> = {},
  ) => ({
    data: {
      name,
      displayName: overrides.displayName ?? name,
      available: overrides.available ?? false,
      enabled: overrides.enabled ?? false,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });
  mockAxios
    .onGet("/integration/integrationInfo", { params: { name: "DMPTOOL" } })
    .reply(200, integrationInfo("DMPTOOL", { displayName: "DMPtool", available: true }));
  mockAxios.onGet("/integration/integrationInfo", { params: { name: "DMPONLINE" } }).reply(
    200,
    integrationInfo("DMPONLINE", {
      displayName: "DMPonline",
      available: true,
      enabled: true,
    }),
  );
  mockAxios
    .onGet("/integration/integrationInfo", { params: { name: "ARGOS" } })
    .reply(200, integrationInfo("ARGOS", { displayName: "Argos" }));
  mockAxios
    .onGet("/integration/integrationInfo", { params: { name: "DSW" } })
    .reply(200, integrationInfo("DSW", { displayName: "DSW" }));

  // DmpMenuSection also fetches the aggregated integration list; the component
  // tolerates failures here, but stub it to keep the console clean.
  mockAxios.onGet("/integration/allIntegrations").reply(200, {
    success: true,
    data: { DSW: { options: {} } },
    error: null,
  });

  // AddFilestoreMenuItem fetches the configured filesystems on mount.
  mockAxios.onGet("/api/v1/gallery/filesystems").reply(200, [
    {
      id: 1,
      name: "irods test",
      url: "irods-test.researchspace.com",
      clientType: "IRODS",
      authType: "PASSWORD",
      options: {},
      loggedAs: null,
    },
  ]);
}

describe("Sidebar", () => {
  beforeEach(() => {
    mockAxios.reset();
    mockNetwork();
  });

  afterEach(() => {
    mockAxios.reset();
    vi.clearAllMocks();
  });

  test("Should have no axe violations", async () => {
    const { baseElement } = render(<DefaultSidebar />);

    // wait for the sidebar to be on screen before scanning
    await screen.findByRole("button", { name: "common:actions.create" });

    await expectAccessible(baseElement);
  });

  describe("New Folder", () => {
    test("Clicking the Submit button should work", async () => {
      const user = userEvent.setup();
      render(<DefaultSidebar />);

      // the sidebar is visible
      await user.click(await screen.findByRole("button", { name: "common:actions.create" }));

      // the user clicks the New Folder menu item
      await user.click(await screen.findByRole("menuitem", { name: "gallery:sidebar.createFolder" }));

      // the New Folder dialog should be visible
      const dialog = await screen.findByRole("dialog");
      expect(dialog).toBeVisible();
      expect(within(dialog).getByRole("heading", { name: "gallery:sidebar.createFolder" })).toBeVisible();

      // the user types a folder name
      await user.type(within(dialog).getByRole("textbox"), "test");

      // the user clicks the Create button in the dialog
      await user.click(within(dialog).getByRole("button", { name: "common:actions.create" }));

      // a folder creation request should be made
      await waitFor(() => {
        expect(mockAxios.history.post.some((req) => req.url?.includes("createFolder"))).toBe(true);
      });

      // submission closes the dialog
      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("Inside an S3 filestore, creates the folder via the filestore API", async () => {
      const user = userEvent.setup();
      render(<S3FilestoreSidebar />);

      await user.click(await screen.findByRole("button", { name: "common:actions.create" }));
      await user.click(await screen.findByRole("menuitem", { name: "gallery:sidebar.createFolder" }));

      const dialog = await screen.findByRole("dialog");
      // S3 gets the filestore-specific title and the "no native folders" note
      expect(within(dialog).getByRole("heading", { name: "gallery:sidebar.createFilestoreFolder" })).toBeVisible();
      expect(within(dialog).getByText("gallery:sidebar.s3FolderNote")).toBeVisible();
      await user.type(within(dialog).getByRole("textbox"), "test");
      await user.click(within(dialog).getByRole("button", { name: "common:actions.create" }));

      // the request goes to the filestore folder endpoint, not the local one
      await waitFor(() => {
        const req = mockAxios.history.post.find((r) => r.url?.includes(`filestores/${S3_FILESTORE_ID}/folder`));
        expect(req).toBeDefined();
        expect(JSON.parse(String(req?.data))).toEqual({ path: "", name: "test" });
      });
      expect(mockAxios.history.post.some((r) => r.url?.includes("createFolder"))).toBe(false);

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("Pressing enter to Submit should work", async () => {
      const user = userEvent.setup();
      render(<DefaultSidebar />);

      // the sidebar is visible
      await user.click(await screen.findByRole("button", { name: "common:actions.create" }));

      // the user clicks the New Folder menu item
      await user.click(await screen.findByRole("menuitem", { name: "gallery:sidebar.createFolder" }));

      // the New Folder dialog should be visible
      const dialog = await screen.findByRole("dialog");
      expect(dialog).toBeVisible();
      expect(within(dialog).getByRole("heading", { name: "gallery:sidebar.createFolder" })).toBeVisible();

      // the user types a folder name and presses Enter
      const textbox = within(dialog).getByRole("textbox");
      await user.type(textbox, "test{Enter}");

      // a folder creation request should be made
      await waitFor(() => {
        expect(mockAxios.history.post.some((req) => req.url?.includes("createFolder"))).toBe(true);
      });

      // submission closes the dialog
      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });
  });
});
