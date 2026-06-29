import { afterEach, beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible, render, screen, waitFor, within } from "@/__tests__/customQueries";
import axios from "@/common/axios";
import {
  DocumentThatHasBeenSharedIntoANotebook,
  MultipleDocuments,
  NoPreviousShares,
  SharedSnippetWithAGroup,
  SharedWithAControlledOpenState,
  SharedWithAGroup,
  SharedWithAnalyticsCapture,
  SharedWithAnotherUser,
} from "./ShareDialog.story";

const mockAxios = new MockAdapter(axios);

/**
 * Shared response bodies mirroring the Playwright spec's `router.route` stubs.
 *
 * `directShares` always echo the parent document's id and name, so we let the
 * factory fill those in rather than repeating them on each share entry.
 */
type ShareEntry = {
  shareId: number;
  sharerId: number;
  sharerName: string;
  permission: "READ" | "EDIT";
  recipientType: "USER" | "GROUP";
  recipientId: number;
  recipientName: string;
  parentId: number | null;
  path: string | null;
};

function shareDocument({
  id,
  name,
  directShares = [],
  notebookShares = [],
}: {
  id: number;
  name: string;
  directShares?: ReadonlyArray<ShareEntry>;
  notebookShares?: ReadonlyArray<ShareEntry>;
}) {
  return {
    sharedDocId: id,
    sharedDocName: name,
    directShares: directShares.map((share) => ({
      ...share,
      sharedDocId: id,
      sharedDocName: name,
    })),
    notebookShares,
  };
}

const aliceAndBobGroup = {
  recipientType: "GROUP" as const,
  recipientId: 1,
  recipientName: "Alice and Bob's Group",
};

const shareDocument1 = shareDocument({ id: 1, name: "Sample Document 1" });

const shareDocument2 = shareDocument({
  id: 2,
  name: "A shared document",
  directShares: [
    {
      shareId: 1,
      sharerId: 1,
      sharerName: "Alice",
      permission: "READ",
      recipientType: "USER",
      recipientId: 2,
      recipientName: "Bob",
      parentId: null,
      path: null,
    },
  ],
});

const shareDocument3 = shareDocument({
  id: 3,
  name: "Another shared document",
  directShares: [
    {
      shareId: 2,
      sharerId: 1,
      sharerName: "Alice",
      permission: "EDIT",
      ...aliceAndBobGroup,
      parentId: 1,
      path: "aliceAndBobGroup_SHARED",
    },
  ],
});

const shareDocument4 = shareDocument({
  id: 4,
  name: "A shared notebook document",
  directShares: [
    {
      shareId: 3,
      sharerId: 1,
      sharerName: "Alice",
      permission: "READ",
      ...aliceAndBobGroup,
      parentId: 2,
      path: "aliceAndBobGroup_SHARED/A notebook",
    },
  ],
  notebookShares: [
    {
      shareId: 4,
      sharerId: 2,
      sharerName: "Bob",
      permission: "EDIT",
      ...aliceAndBobGroup,
      parentId: 2,
      path: "aliceAndBobGroup_SHARED/A notebook",
    },
  ],
});

const groupsResponse = [
  {
    id: 1,
    globalId: "GP1",
    name: "Alice and Bob's Group",
    type: "LAB_GROUP",
    sharedFolderId: 1,
    members: [
      { id: 1, username: "alice", role: "PI" },
      { id: 2, username: "bob", role: "USER" },
    ],
    uniqueName: "aliceAndBobGroup",
    _links: [],
  },
];

const groupMembersResponse = [
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
];

const folder1Response = {
  id: 129,
  globalId: "FL129",
  name: "alice-bob",
  created: "2025-09-09T12:05:14.109Z",
  lastModified: "2025-09-09T12:05:14.109Z",
  parentFolderId: 124,
  notebook: false,
  mediaType: null,
  pathToRootFolder: [
    {
      id: 128,
      globalId: "FL128",
      name: "IndividualShareItems",
      created: "2025-09-09T12:05:13.716Z",
      lastModified: "2025-09-09T12:05:13.716Z",
      parentFolderId: 125,
      notebook: false,
      mediaType: null,
      pathToRootFolder: null,
      _links: [],
    },
    {
      id: 125,
      globalId: "FL125",
      name: "Shared",
      created: "2025-09-09T12:05:13.691Z",
      lastModified: "2025-09-09T12:05:13.691Z",
      parentFolderId: 124,
      notebook: false,
      mediaType: null,
      pathToRootFolder: null,
      _links: [],
    },
    {
      id: 124,
      globalId: "FL124",
      name: "alice",
      created: "2025-09-09T12:05:13.223Z",
      lastModified: "2025-09-09T12:05:13.223Z",
      parentFolderId: null,
      notebook: false,
      mediaType: null,
      pathToRootFolder: null,
      _links: [],
    },
  ],
  _links: [{ link: "http://localhost:8080/api/v1/folders/154", rel: "self" }],
};

const folderTreeResponse = {
  totalHits: 1,
  pageNumber: 0,
  parentId: null,
  _links: [],
  records: [
    {
      id: 2,
      globalId: "FL2",
      name: "alice-bob",
      created: "2025-09-09T12:05:14.109Z",
      lastModified: "2025-09-09T12:05:14.109Z",
      parentFolderId: null,
      type: "folder",
      _links: [],
      owner: {
        id: 1,
        username: "alice",
        email: "alice@example.com",
        firstName: "Alice",
        lastName: "Smith",
        homeFolderId: 1,
        workbenchId: null,
        hasPiRole: true,
        hasSysAdminRole: false,
        _links: [],
      },
    },
  ],
};

beforeEach(() => {
  mockAxios.reset();

  mockAxios.onGet("/api/v1/userDetails/whoami").reply(200, {
    id: 1,
    username: "testuser",
    email: "test@example.com",
    firstName: "Test",
    lastName: "User",
  });
  mockAxios.onGet("/api/v1/share/document/1").reply(200, shareDocument1);
  mockAxios.onGet("/api/v1/share/document/2").reply(200, shareDocument2);
  mockAxios.onGet("/api/v1/share/document/3").reply(200, shareDocument3);
  mockAxios.onGet("/api/v1/share/document/4").reply(200, shareDocument4);
  mockAxios.onGet("/api/v1/groups").reply(200, groupsResponse);
  mockAxios.onGet(/\/api\/v1\/userDetails\/groupMembers.*/).reply(200, groupMembersResponse);
  mockAxios.onGet("/api/v1/folders/1").reply(200, folder1Response);
  mockAxios.onGet(/\/api\/v1\/folders\/tree.*/).reply(200, folderTreeResponse);
  // UserDetails / GroupDetails lazily fetch mini-profiles on popover open; stub
  // so any such request resolves cleanly.
  mockAxios.onGet(/\/userform\/ajax\/miniprofile\/.*/).reply(200, { data: {} });

  mockAxios.onPost("/api/v1/documents/move").reply(200, { success: true });
  mockAxios.onPost("/api/v1/share").reply((config) => {
    const body = JSON.parse(config.data as string) as {
      itemsToShare?: number[];
      users?: Array<{ permission?: string }>;
    };
    const itemId = body.itemsToShare?.[0] ?? 0;
    const permission = body.users?.[0]?.permission ?? "READ";
    return [
      200,
      {
        shareInfos: [
          {
            id: 294912,
            sharedItemId: itemId,
            shareItemName: "Untitled document",
            sharedTargetType: "USER",
            permission,
            _links: [],
          },
        ],
        failedShares: [],
        _links: [],
      },
    ];
  });
  mockAxios.onPut("/api/v1/share").reply((config) => {
    const body = JSON.parse(config.data as string) as {
      shareId?: number;
      permission?: string;
    };
    const shareId = body.shareId ?? 0;
    const permission = body.permission ?? "READ";
    return [
      200,
      {
        shareInfo: {
          id: shareId,
          sharedItemId: 2,
          shareItemName: "A shared document",
          sharedTargetType: "USER",
          permission,
          _links: [],
        },
        _links: [],
      },
    ];
  });
  mockAxios.onDelete(/\/api\/v1\/share\/\d+/).reply(204);
});

afterEach(() => {
  const w = window as Window & {
    __trackedEvents?: string[];
    getAndDisplayWorkspaceResults?: unknown;
    workspaceSettings?: unknown;
  };
  delete w.__trackedEvents;
  delete w.getAndDisplayWorkspaceResults;
  delete w.workspaceSettings;
});

/**
 * Returns the recorded request bodies, mirroring the spec's `networkRequests`
 * inspection but reading from axios-mock-adapter's history.
 */
function findRequest(method: "post" | "put" | "delete", matcher: (entry: { url?: string; data?: unknown }) => boolean) {
  return mockAxios.history[method].find((entry) => matcher({ url: entry.url, data: entry.data }));
}

describe("ShareDialog", () => {
  describe("Renders correctly", () => {
    test("Renders a dialog", async () => {
      const { baseElement } = render(<NoPreviousShares />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });
      expect(dialog).toBeVisible();
      await waitFor(() => {
        expect(dialog).toHaveTextContent(/This document is not directly shared with anyone./i);
      });

      await expectAccessible(baseElement);
    });

    test("When a document has been shared with another user, there's a table", async () => {
      const { baseElement } = render(<SharedWithAnotherUser />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share A shared document/i,
      });
      expect(dialog).toBeVisible();
      const table = await within(dialog).findByRole("table");
      expect(table).toBeVisible();
      const row = within(table).getAllByRole("row")[1];
      expect(row).toBeVisible();
      expect(
        within(within(row).getAllByRole("cell")[0]).getByRole("button", {
          name: "Bob",
        }),
      ).toBeVisible();
      expect(within(row).getByRole("combobox")).toHaveTextContent(/READ/i);
      expect(within(row).getAllByRole("cell")[3]).toHaveTextContent("—");

      await expectAccessible(baseElement);
    });

    test("When a document has been shared with another group, there's a table", async () => {
      const { baseElement } = render(<SharedWithAGroup />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Another shared document/i,
      });
      expect(dialog).toBeVisible();
      const table = await within(dialog).findByRole("table");
      expect(table).toBeVisible();
      const row = within(table).getAllByRole("row")[1];
      expect(row).toBeVisible();
      expect(
        within(within(row).getAllByRole("cell")[0]).getByRole("button", {
          name: /^Alice and Bob's Group$/,
        }),
      ).toBeVisible();
      expect(within(row).getByRole("combobox")).toHaveTextContent(/EDIT/i);
      // Folder name is resolved asynchronously after groups + folder fetches.
      await waitFor(() => {
        expect(within(row).getAllByRole("cell")[3]).toHaveTextContent(/aliceAndBobGroup_SHARED/);
      });

      await expectAccessible(baseElement);
    });

    /*
     * This is because the UI became too complex to show a table for each document
     */
    test("When multiple documents are selected, no table is shown", async () => {
      const { baseElement } = render(<MultipleDocuments />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share \d+ documents/i,
      });
      expect(dialog).toBeVisible();
      expect(within(dialog).queryByRole("table")).toBeNull();
      expect(
        within(dialog).getByRole("heading", {
          name: /adding shares to 2 documents/i,
        }),
      ).toBeVisible();

      await waitFor(() => expect(screen.queryByRole("progressbar")).not.toBeInTheDocument());
      await expectAccessible(baseElement);
    });

    test("When a document has been shared into a notebook, the implicit shares are shown", async () => {
      const { baseElement } = render(<DocumentThatHasBeenSharedIntoANotebook />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share A shared notebook document/i,
      });
      expect(dialog).toBeVisible();
      await waitFor(() => {
        expect(within(dialog).getAllByRole("table")).toHaveLength(2);
      });
      const tables = within(dialog).getAllByRole("table");
      const directShareTable = tables[0];
      const notebookShareTable = tables[1];
      expect(directShareTable).toBeVisible();
      expect(notebookShareTable).toBeVisible();

      const directShareRow = within(directShareTable).getAllByRole("row")[1];
      expect(directShareRow).toBeVisible();
      expect(
        within(within(directShareRow).getAllByRole("cell")[0]).getByRole("button", { name: /^Alice and Bob's Group$/ }),
      ).toBeVisible();
      expect(within(directShareRow).getByRole("combobox")).toHaveTextContent(/READ/i);
      expect(within(directShareRow).getAllByRole("cell")[3]).toHaveTextContent(/A notebook/);

      const notebookShareRow = within(notebookShareTable).getAllByRole("row")[1];
      expect(notebookShareRow).toBeVisible();
      expect(
        within(within(notebookShareRow).getAllByRole("cell")[1]).getByRole("button", {
          name: /^Alice and Bob's Group$/,
        }),
      ).toBeVisible();
      expect(within(notebookShareRow).getByRole("combobox")).toHaveTextContent(/EDIT/i);
      expect(within(notebookShareRow).getByRole("combobox")).toHaveAttribute("aria-disabled", "true");

      await expectAccessible(baseElement);
    });
  });

  describe("Creating new shares", () => {
    test("An unshared document should be sharable with a member of the same group", async () => {
      const user = userEvent.setup();
      render(<NoPreviousShares />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });

      // the user selects Bob from the recipient dropdown
      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });
      await user.click(recipientDropdown);
      await user.click(await screen.findByRole("option", { name: /^Bob/ }));

      // the user saves the new share
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      // the Save button should have changed to Done
      expect(await within(dialog).findByRole("button", { name: /Done/i })).toBeVisible();

      // a POST request should have been made to create the share
      expect(
        findRequest("post", ({ url, data }) => url === "/api/v1/share" && data !== null && data !== undefined),
      ).toBeDefined();
    });

    test("Multiple documents should be sharable with a group", async () => {
      const user = userEvent.setup();
      render(<MultipleDocuments />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share \d+ documents/i,
      });

      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });
      await user.click(recipientDropdown);
      await user.click(await screen.findByRole("option", { name: /^Alice and Bob's Group/ }));

      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      expect(await within(dialog).findByRole("button", { name: /Done/i })).toBeVisible();

      expect(findRequest("post", ({ url }) => url === "/api/v1/share")).toBeDefined();
    });

    test("The same document shouldn't be shareable twice with the same user", async () => {
      const user = userEvent.setup();
      render(<SharedWithAnotherUser />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share A shared document/i,
      });
      // ensure share data has loaded so the option's disabled state is computed
      await within(dialog).findByRole("table");

      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });
      await user.click(recipientDropdown);
      const bobOption = await screen.findByRole("option", { name: /^Bob/ });
      expect(bobOption).toHaveAttribute("aria-disabled", "true");
    });

    test("When sharing multiple documents, choosing a recipient who already has access to one of them will share both with default read permission", async () => {
      const user = userEvent.setup();
      render(<MultipleDocuments />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share \d+ documents/i,
      });

      // Alice and Bob's Group is chosen in the recipient dropdown
      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });
      await user.click(recipientDropdown);
      await user.click(await screen.findByRole("option", { name: /Alice and Bob's Group/i }));

      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      expect(await within(dialog).findByRole("button", { name: /Done/i })).toBeVisible();

      // a POST request should have been made to create the share
      expect(findRequest("post", ({ url }) => url === "/api/v1/share")).toBeDefined();
      // a PUT request should have been made to update the existing share
      const updateRequest = findRequest(
        "put",
        ({ url, data }) => url === "/api/v1/share" && data !== null && data !== undefined,
      );
      expect(updateRequest).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts updateRequest is defined above
      const body = JSON.parse(updateRequest!.data as string) as {
        shareId: number;
        permission: "EDIT" | "READ";
      };
      expect(body).toHaveProperty("shareId");
      expect(body.shareId).toEqual(2);
      expect(body).toHaveProperty("permission");
      expect(body.permission).toEqual("READ");
    });
  });

  describe("Removing shares", () => {
    test("When the user chooses unshare from the permission menu, it should make a DELETE request", async () => {
      const user = userEvent.setup();
      render(<SharedWithAnotherUser />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share A shared document/i,
      });
      const table = await within(dialog).findByRole("table");

      // the user chooses unshare from the permission menu for Bob
      const bobRow = within(table)
        .getAllByRole("row")
        .find((row) => within(row).queryByRole("button", { name: "Bob" }));
      expect(bobRow).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts bobRow is defined above
      await user.click(within(bobRow!).getByRole("combobox"));
      await user.click(await screen.findByRole("option", { name: /unshare/i }));

      // the user saves the new share
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      // a DELETE request should have been made to remove the share
      await waitFor(() => {
        expect(findRequest("delete", ({ url }) => (url ?? "").startsWith("/api/v1/share/"))).toBeDefined();
      });
    });
  });

  describe("Updating shares", () => {
    test("When the user changes a user's permission from READ to EDIT, it should make a PUT request", async () => {
      const user = userEvent.setup();
      render(<SharedWithAnotherUser />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share A shared document/i,
      });
      const table = await within(dialog).findByRole("table");

      // the user changes Bob's permission from READ to EDIT
      const bobRow = within(table)
        .getAllByRole("row")
        .find((row) => within(row).queryByRole("button", { name: "Bob" }));
      expect(bobRow).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts bobRow is defined above
      await user.click(within(bobRow!).getByRole("combobox"));
      await user.click(await screen.findByRole("option", { name: /^Edit$/i }));

      // the user saves the new share
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      // a PUT request should have been made to update Bob's permission to EDIT
      await waitFor(() => {
        expect(findRequest("put", ({ url }) => url === "/api/v1/share")).toBeDefined();
      });
      const updateRequest = findRequest(
        "put",
        ({ url, data }) => url === "/api/v1/share" && data !== null && data !== undefined,
      );
      expect(updateRequest).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts updateRequest is defined above
      const body = JSON.parse(updateRequest!.data as string) as {
        shareId: number;
        permission: "EDIT" | "READ";
      };
      expect(body).toHaveProperty("shareId");
      expect(body.shareId).toEqual(1);
      expect(body).toHaveProperty("permission");
      expect(body.permission).toEqual("EDIT");
    });

    test("When the user changes a group's permission from EDIT to read, it should make a PUT request", async () => {
      const user = userEvent.setup();
      render(<SharedWithAGroup />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Another shared document/i,
      });
      const table = (await within(dialog).findAllByRole("table"))[0];

      // the user changes Alice and Bob's Group permission from EDIT to read
      const groupRow = within(table)
        .getAllByRole("row")
        .find((row) =>
          within(row).queryByRole("button", {
            name: /Alice and Bob's Group/i,
          }),
        );
      expect(groupRow).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts groupRow is defined above
      await user.click(within(groupRow!).getByRole("combobox"));
      await user.click(await screen.findByRole("option", { name: /^Read$/i }));

      // the user saves the new share
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      // a PUT request should have been made to update the group's permission to READ
      await waitFor(() => {
        expect(
          findRequest("put", ({ url, data }) => url === "/api/v1/share" && data !== null && data !== undefined),
        ).toBeDefined();
      });
      const updateRequest = findRequest(
        "put",
        ({ url, data }) => url === "/api/v1/share" && data !== null && data !== undefined,
      );
      expect(updateRequest).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts updateRequest is defined above
      const body = JSON.parse(updateRequest!.data as string) as {
        shareId: number;
        permission: "EDIT" | "READ";
      };
      expect(body).toHaveProperty("shareId");
      expect(body).toHaveProperty("permission");
      expect(body.permission).toEqual("READ");
    });

    test("When the user changes the folder location for a group share, it should make a POST request to move the document", async () => {
      const user = userEvent.setup();
      render(<SharedWithAGroup />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Another shared document/i,
      });
      const table = (await within(dialog).findAllByRole("table"))[0];

      // the user clicks the Change button for Alice and Bob's Group folder
      const groupRow = within(table)
        .getAllByRole("row")
        .find((row) =>
          within(row).queryByRole("button", {
            name: /Alice and Bob's Group/i,
          }),
        );
      expect(groupRow).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts groupRow is defined above
      await user.click(within(groupRow!).getByRole("button", { name: /Change/i }));

      // the user selects a different folder in the folder selection dialog
      const folderDialog = await screen.findByRole("dialog", {
        name: /Select Shared Folder Location/i,
      });
      const treeItem = await within(folderDialog).findByText("alice-bob");
      await user.click(treeItem);
      const selectButton = within(folderDialog).getByRole("button", {
        name: /^Select$/i,
      });
      await waitFor(() => expect(selectButton).toBeEnabled());
      await user.click(selectButton);
      await waitFor(() => {
        expect(
          screen.queryByRole("dialog", {
            name: /Select Shared Folder Location/i,
          }),
        ).toBeNull();
      });

      // the user saves the new share
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      // a POST request should have been made to move the document to the new folder
      await waitFor(() => {
        expect(findRequest("post", ({ url }) => url === "/api/v1/documents/move")).toBeDefined();
      });
      const moveRequest = findRequest(
        "post",
        ({ url, data }) => url === "/api/v1/documents/move" && data !== null && data !== undefined,
      );
      expect(moveRequest).toBeDefined();
      // biome-ignore lint/style/noNonNullAssertion: test asserts moveRequest is defined above
      const body = JSON.parse(moveRequest!.data as string) as {
        docId: number;
        sourceFolderId: number;
        targetFolderId: number;
      };
      expect(body).toHaveProperty("docId");
      expect(body.docId).toEqual(3);
      expect(body).toHaveProperty("sourceFolderId");
      expect(body.sourceFolderId).toEqual(1);
      expect(body).toHaveProperty("targetFolderId");
      expect(body.targetFolderId).toEqual(129);
    });
  });

  describe("Dialog lifecycle and analytics", () => {
    test("The snippet share dialog story should render successfully", async () => {
      render(<SharedSnippetWithAGroup />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Another shared snippet/i,
      });
      expect(dialog).toBeVisible();
      expect(within(dialog).getByText("Another shared snippet")).toBeVisible();
      expect(within(screen.getByRole("alert")).getByText(/SNIPPETS_Shared/i)).toBeVisible();
    });

    test("Closing and reopening should reset transient dialog state", async () => {
      const user = userEvent.setup();
      render(<SharedWithAControlledOpenState />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });
      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });

      await user.click(recipientDropdown);
      await user.click(await screen.findByRole("option", { name: /^Bob/ }));
      expect(await within(dialog).findByRole("table")).toBeVisible();

      await user.keyboard("{Escape}");
      await waitFor(() => {
        expect(screen.queryByRole("dialog", { name: /Share Sample Document 1/i })).toBeNull();
      });

      await user.click(screen.getByRole("button", { name: /Open share dialog/i }));
      const reopenedDialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });
      expect(reopenedDialog).toBeVisible();
      expect(within(reopenedDialog).getByText("This document is not directly shared with anyone.")).toBeVisible();
      expect(within(reopenedDialog).queryByRole("table")).toBeNull();
      expect(
        within(reopenedDialog).getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        }),
      ).toHaveValue("");
    });

    test("Saving outside workspace should still close and show success alert", async () => {
      const user = userEvent.setup();
      const w = window as Window & {
        getAndDisplayWorkspaceResults?: unknown;
        workspaceSettings?: unknown;
      };
      delete w.getAndDisplayWorkspaceResults;
      delete w.workspaceSettings;

      render(<SharedWithAControlledOpenState />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });
      const recipientDropdown = within(dialog).getByRole("combobox", {
        name: /Add RSpace users or groups/i,
      });
      await user.click(recipientDropdown);
      await user.click(await screen.findByRole("option", { name: /^Bob/ }));
      await user.click(within(dialog).getByRole("button", { name: /Save/i }));

      await waitFor(
        () => {
          expect(screen.queryByRole("dialog", { name: /Share Sample Document 1/i })).toBeNull();
        },
        { timeout: 5000 },
      );
      await waitFor(() => {
        expect(screen.getByRole("alert")).toHaveTextContent(/Shares updated successfully\./i);
      });
    });

    test("Saving with no changes should track the close event with the expected name", async () => {
      const user = userEvent.setup();
      (window as Window & { __trackedEvents?: string[] }).__trackedEvents = [];

      render(<SharedWithAnalyticsCapture />);

      const dialog = await screen.findByRole("dialog", {
        name: /Share Sample Document 1/i,
      });
      // With no changes the submit button reads "Done".
      const doneButton = within(dialog).getByRole("button", { name: /Done/i });
      await user.click(doneButton);
      await waitFor(() => {
        expect(screen.queryByRole("dialog", { name: /Share Sample Document 1/i })).toBeNull();
      });

      const trackedEvents = (window as Window & { __trackedEvents?: string[] }).__trackedEvents ?? [];
      expect(trackedEvents).toContain("user:close:share_dialog");
    });
  });
});
