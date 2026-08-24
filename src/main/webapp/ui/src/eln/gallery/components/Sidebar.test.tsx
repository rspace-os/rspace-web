import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible } from "@/__tests__/accessibility";
import axios from "@/common/axios";
import allIntegrationsAreDisabled from "../../apps/__tests__/allIntegrationsAreDisabled.json";
import { DefaultSidebar, queryClient, S3_FILESTORE_ID, S3FilestoreSidebar } from "./Sidebar.story";

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

  // DmpMenuSection derives the DMP menu from /allIntegrations
  mockAxios.onGet("/integration/allIntegrations").reply(200, {
    ...allIntegrationsAreDisabled,
    data: {
      ...allIntegrationsAreDisabled.data,
      DMPTOOL: {
        ...allIntegrationsAreDisabled.data.DMPTOOL,
        available: true,
      },
      DMPONLINE: {
        ...allIntegrationsAreDisabled.data.DMPONLINE,
        available: true,
        enabled: true,
      },
    },
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
    queryClient.clear();
  });

  afterEach(() => {
    mockAxios.reset();
    vi.clearAllMocks();
  });

  test("Reopening the create menu reuses the cached integration states", async () => {
    const user = userEvent.setup();
    render(<DefaultSidebar />);

    const allIntegrationsCalls = () => mockAxios.history.get.filter((req) => req.url?.includes("allIntegrations"));
    const integrationInfoCalls = () => mockAxios.history.get.filter((req) => req.url?.includes("integrationInfo"));

    await user.click(await screen.findByRole("button", { name: "common:actions.create" }));
    await screen.findByRole("menuitem", { name: /dmpIntegrations.dmponline/ });
    expect(allIntegrationsCalls()).toHaveLength(1);
    expect(integrationInfoCalls()).toHaveLength(0);

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("menuitem", { name: /dmpIntegrations.dmponline/ })).not.toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "common:actions.create" }));
    await screen.findByRole("menuitem", { name: /dmpIntegrations.dmponline/ });
    expect(allIntegrationsCalls()).toHaveLength(1);
    expect(integrationInfoCalls()).toHaveLength(0);
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
