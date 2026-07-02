import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import type React from "react";
import { cleanup, expectAccessible, render, screen, within } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import {
  ActionsMenuWithFolder,
  ActionsMenuWithMixedSelection,
  ActionsMenuWithMultipleSnippets,
  ActionsMenuWithNonFolder,
  ActionsMenuWithSnippet,
  ActionsMenuWithSnippetInSharedFolderOwnedByOther,
  ActionsMenuWithSnippetInSharedFolderOwnedBySelf,
  ActionsMenuWithSnippetInSystemSharedFolder,
  ActionsMenuWithSnippetMissingGlobalId,
} from "./ActionsMenu.story";

/*
 * DOM/role/state based tests: opening the menu, asserting which items appear
 * and whether they are enabled/disabled based on the props/selection, plus a
 * share flow. These run in jsdom.
 *
 * Network is mocked via the `@/common/axios` instance that every hook in the
 * tree uses (useWhoAmI, useDeploymentProperty, useCollabora, useOfficeOnline,
 * useShare, useGroups, useUserDetails, useFolders, useDocuments).
 */

const mockAxios = new MockAdapter(axios);

const whoAmIResponse = {
  id: 1,
  username: "testuser",
  email: "test@example.com",
  firstName: "Test",
  lastName: "User",
  hasPiRole: false,
  hasSysAdminRole: false,
  workbenchId: 1,
};

function stubCommonEndpoints({ netfilestoresEnabled = false }: { netfilestoresEnabled?: boolean } = {}) {
  // Bootstrap calls made on mount by the component tree.
  mockAxios.onGet("/collaboraOnline/supportedExts").reply(200, {});
  mockAxios.onGet("/officeOnline/supportedExts").reply(200, {});
  // useDeploymentProperty sends the property name as a request param, so we
  // inspect config.params to vary the boolean response per property.
  mockAxios.onGet("/deploymentproperties/ajax/property").reply((config) => {
    const params = config.params as URLSearchParams | undefined;
    const name = params?.get?.("name");
    return [200, name === "netfilestores.enabled" ? netfilestoresEnabled : false];
  });

  // MoveDialog (rendered in the tree) loads the gallery listing on mount.
  mockAxios.onGet(/\/gallery\/getUploadedFiles.*/).reply(200, {
    data: { parentId: 1, items: { results: [] } },
  });
  // Filestores endpoint hit when the netfile (iRODS/S3) actions are shown.
  mockAxios.onGet(/\/filestores.*/).reply(200, []);

  mockAxios.onGet("/api/v1/userDetails/whoami").reply(200, whoAmIResponse);

  // Share dialog data sources.
  mockAxios.onGet("/api/v1/share/document/3").reply(200, {
    sharedDocId: 3,
    sharedDocName: "My Snippet",
    directShares: [],
    notebookShares: [],
  });
  mockAxios.onGet("/api/v1/share/document/5").reply(200, {
    sharedDocId: 5,
    sharedDocName: "My Second Snippet",
    directShares: [],
    notebookShares: [],
  });
  mockAxios.onGet("/api/v1/groups").reply(200, [
    {
      id: 1,
      globalId: "GP1",
      name: "Alice and Bob's Group",
      type: "LAB_GROUP",
      sharedFolderId: 1,
      sharedSnippetFolderId: 2,
      members: [
        { id: 1, username: "alice", role: "PI" },
        { id: 2, username: "bob", role: "USER" },
      ],
      uniqueName: "aliceAndBobGroup",
      _links: [],
    },
  ]);
  mockAxios.onGet("/api/v1/userDetails/groupMembers").reply(200, [
    {
      id: 2,
      username: "bob",
      email: "bob@example.com",
      firstName: "Bob",
      lastName: "",
      homeFolderId: 2,
      workbenchId: 1,
      hasPiRole: false,
      hasSysAdminRole: false,
      _links: [],
    },
  ]);
  mockAxios.onGet(/\/api\/v1\/folders\/.*/).reply(200, {
    id: 1,
    globalId: "FL1",
    name: "alice-bob",
    created: "2025-09-09T12:05:14.109Z",
    lastModified: "2025-09-09T12:05:14.109Z",
    parentFolderId: 124,
    notebook: false,
    mediaType: null,
    pathToRootFolder: [],
    _links: [],
  });

  mockAxios.onPost("/api/v1/share").reply(200, {
    shareInfos: [],
    failedShares: [],
    _links: [],
  });
  mockAxios.onPut("/api/v1/share").reply(200, {
    shareInfo: {
      id: 1,
      sharedItemId: 3,
      shareItemName: "My Snippet",
      sharedTargetType: "USER",
      permission: "READ",
      _links: [],
    },
    _links: [],
  });
  mockAxios.onDelete(/\/api\/v1\/share\/.*/).reply(204);

  // Catch-all so any other bootstrap request resolves cleanly rather than
  // 404-ing and surfacing an error alert that would pollute role="alert".
  mockAxios.onAny().reply(200, {});
}

beforeEach(() => {
  mockAxios.reset();
  stubCommonEndpoints();
});

afterEach(() => {
  cleanup();
  mockAxios.reset();
});

/*
 * useDeploymentProperty caches results in a module-level Map that is the
 * default value of DeploymentPropertyContext, so without a fresh provider the
 * cache would leak between tests (e.g. netfilestores.enabled fetched as false
 * in one test would be served from cache in the next). We wrap each render in
 * a provider holding a new Map to keep tests isolated.
 */
function renderStory(ui: React.ReactElement) {
  return render(<DeploymentPropertyContext.Provider value={new Map()}>{ui}</DeploymentPropertyContext.Provider>);
}

async function openMenu(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: /actions/i }));
}

/*
 * MUI renders disabled menu items as `<li role="menuitem" aria-disabled>`.
 * jest-dom's `toBeDisabled()`/`toBeEnabled()` only recognise the native
 * `disabled` attribute on form controls, not `aria-disabled` on a list item
 * (Playwright's matchers did honour aria-disabled), so we assert the attribute
 * directly to preserve the spec's intent.
 */
function expectMenuItemDisabled(item: HTMLElement) {
  expect(item).toHaveAttribute("aria-disabled", "true");
}
function expectMenuItemEnabled(item: HTMLElement) {
  expect(item).not.toHaveAttribute("aria-disabled", "true");
}

describe("ActionsMenu", () => {
  describe("Should have no axe violations", () => {
    test("Should have no axe violations", async () => {
      const { baseElement } = renderStory(<ActionsMenuWithNonFolder />);
      // Wait for the bootstrap state to settle so the rendered tree is stable.
      await screen.findByRole("button", { name: /actions/i });
      await expectAccessible(baseElement);
    });
  });

  describe("File menu options", () => {
    test("When the selected file isn't a folder, open should not be visible", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithNonFolder />);
      await openMenu(user);
      await screen.findByRole("menu", { name: /actions/i });
      expect(screen.queryByRole("menuitem", { name: /open/i })).not.toBeInTheDocument();
    });

    test("When the selected file is a folder, open should be visible", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithFolder />);
      await openMenu(user);
      expect(await screen.findByRole("menuitem", { name: /open/i })).toBeVisible();
    });

    test("When the selected file is a snippet, download should be disabled", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippet />);
      await openMenu(user);
      expectMenuItemDisabled(await screen.findByRole("menuitem", { name: /common:actions\.download/i }));
    });

    test("Share should always be visible and enabled when only snippets are selected", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippet />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      await waitFor(() => expectMenuItemEnabled(share));
    });

    test("Share should be disabled when snippets and non-snippets are selected together", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithMixedSelection />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      expectMenuItemDisabled(share);
    });

    test("Share should open dialog for a single snippet", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippet />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      await waitFor(() => expectMenuItemEnabled(share));
      await user.click(share);
      expect(await screen.findByRole("dialog", { name: "common:shareDialog.titleSingle" })).toBeVisible();
    });

    test("Share should pass all selected snippets to the dialog", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithMultipleSnippets />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      await waitFor(() => expectMenuItemEnabled(share));
      await user.click(share);
      expect(await screen.findByRole("dialog", { name: "common:shareDialog.titleMultiple" })).toBeVisible();

      // Share info should be requested for both selected snippets.
      await waitFor(() => {
        const requestedPaths = mockAxios.history.get.map((req) => req.url);
        expect(requestedPaths).toContain("/api/v1/share/document/3");
        expect(requestedPaths).toContain("/api/v1/share/document/5");
      });
    });

    test("Share should be disabled when snippet global ID is missing", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippetMissingGlobalId />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      await waitFor(() => expectMenuItemDisabled(share));
      expect(share).toHaveTextContent(/Cannot share snippets that are missing global IDs\./i);
    });

    test("Share should be enabled when the current user owns a snippet in a shared folder", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippetInSharedFolderOwnedBySelf />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      await waitFor(() => expectMenuItemEnabled(share));
    });

    test("Share should be disabled when another user owns a snippet in a shared folder", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippetInSharedFolderOwnedByOther />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      await waitFor(() => expectMenuItemDisabled(share));
      expect(share).toHaveTextContent(/Only owners of the snippet can change its share settings\./i);
    });

    test("Share should not be enabled for a snippet in a system shared folder", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippetInSystemSharedFolder />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      await waitFor(() => expectMenuItemDisabled(share));
      expect(share).toHaveTextContent(/Only owners of the snippet can change its share settings\./i);
    });

    test("Share should be disabled while the current user details are still loading", async () => {
      // Never resolve whoami so the hook stays in the "loading" state.
      mockAxios.onGet("/api/v1/userDetails/whoami").reply(
        () =>
          new Promise(() => {
            /* intentionally never resolves */
          }),
      );

      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippet />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      expect(share).toBeVisible();
      expectMenuItemDisabled(share);
      expect(share).toHaveTextContent(/Loading user information\.\.\./i);
    });

    test("Saving a gallery share should show success alert and close dialog", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithSnippet />);
      await openMenu(user);
      const share = await screen.findByRole("menuitem", { name: /common:actions\.share/i });
      await waitFor(() => expectMenuItemEnabled(share));
      await user.click(share);

      const dialog = await screen.findByRole("dialog", {
        name: "common:shareDialog.titleSingle",
      });

      // Select Bob from the recipient dropdown.
      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: "common:shareDialog.autocomplete.label",
      });
      await user.click(recipientDropdown);
      const bobOption = await screen.findByRole("option", { name: /^Bob/ });
      await user.click(bobOption);

      // Once a recipient is added the action button changes to "Save".
      const saveButton = await within(dialog).findByRole("button", {
        name: /Save/i,
      });
      await user.click(saveButton);

      // Success alert appears...
      expect(await screen.findByRole("alert", undefined, { timeout: 5000 })).toHaveTextContent(
        "common:shareDialog.updatedSuccessfully",
      );

      // ...and the share dialog closes.
      await waitFor(() => {
        expect(screen.queryByRole("dialog", { name: "common:shareDialog.titleSingle" })).not.toBeInTheDocument();
      });
    });
  });

  describe("S3 menu item", () => {
    test("Opening the actions menu for an S3-capable file should not produce MUI styling errors", async () => {
      mockAxios.reset();
      stubCommonEndpoints({ netfilestoresEnabled: true });

      const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

      const user = userEvent.setup();
      renderStory(<ActionsMenuWithNonFolder />);
      await openMenu(user);
      expect(await screen.findByRole("menuitem", { name: /gallery:actionsMenu\.moveToS3/i })).toBeVisible();

      const muiErrors = errorSpy.mock.calls
        .map((args) => args.map((a) => String(a)).join(" "))
        .filter((msg) => /MUI.*Unsupported|MUI error #9/i.test(msg));
      expect(muiErrors).toHaveLength(0);

      errorSpy.mockRestore();
    });

    test("Move to iRODS and Move to S3 should be hidden when netfilestores is disabled", async () => {
      const user = userEvent.setup();
      renderStory(<ActionsMenuWithNonFolder />);
      await openMenu(user);
      await screen.findByRole("menu", { name: /actions/i });
      expect(screen.queryByRole("menuitem", { name: /gallery:actionsMenu\.moveToIrods/i })).not.toBeInTheDocument();
      expect(screen.queryByRole("menuitem", { name: /gallery:actionsMenu\.moveToS3/i })).not.toBeInTheDocument();
    });
  });
});
