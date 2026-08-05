import { beforeEach, describe, expect, test } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { expectAccessible } from "@/__tests__/accessibility";
import axios from "@/common/axios";
import { TestFolderTreeExample } from "./FolderTree.story";

/*
 * Implementation check: the "add subfolder" control is
 * rendered UNCONDITIONALLY in the DOM for non-notebook folders (see
 * FolderTree.tsx `labelContent`: it is gated only by `folder.type !== "NOTEBOOK"`,
 * never by JS hover state). The only hover styling is an inline
 * `sx={{ opacity: 0.6, "&:hover": { opacity: 1 } }}` (the button is already
 * visible at opacity 0.6), and the `StyledTreeItemContent` `opacity:0 / :hover`
 * styled component is dead code that is never rendered. So the add-folder button
 * is queryable and visible in jsdom regardless of hover.
 *
 * Note on querying: MUI's TreeItem registers its click handler on the inner
 * `.MuiTreeItem-content` element, not the `<li role="treeitem">` root, so
 * selection/expansion clicks must target the content element (see Tree.test.tsx).
 */

const mockAxios = new MockAdapter(axios);

const rootFolderRecord = (overrides: { id: number; globalId: string; name: string; type: string }) => ({
  ...overrides,
  created: "2025-01-01T10:00:00.000Z",
  lastModified: "2025-01-15T14:00:00.000Z",
  parentFolderId: null,
  _links: [],
  owner: {
    id: 1,
    username: "researcher",
    email: "researcher@example.com",
    firstName: "Research",
    lastName: "User",
    homeFolderId: 124,
    workbenchId: null,
    hasPiRole: true,
    hasSysAdminRole: false,
    _links: [],
  },
});

const subFolderRecord = (id: number, name: string) => ({
  id,
  globalId: `FL${id}`,
  name,
  created: "2025-01-01T10:00:00.000Z",
  lastModified: "2025-01-15T14:00:00.000Z",
  parentFolderId: 100,
  type: "FOLDER",
  _links: [],
  owner: {
    id: 1,
    username: "researcher",
    email: "researcher@example.com",
    firstName: "Research",
    lastName: "User",
    homeFolderId: 124,
    workbenchId: null,
    hasPiRole: true,
    hasSysAdminRole: false,
    _links: [],
  },
});

/**
 * Mirrors the `router.route(...)` stubs from FolderTree.spec.tsx, keyed on the
 * `typesToInclude` / `pageNumber` query params and the folder id in the path.
 */
const setupApiMocks = () => {
  // Root tree listing.
  mockAxios.onGet(/\/api\/v1\/folders\/tree(\?.*)?$/).reply((config) => {
    const typesToInclude = (config.params as URLSearchParams | undefined)?.get("typesToInclude") ?? "";
    if (/folder/.test(typesToInclude)) {
      return [
        200,
        {
          totalHits: 3,
          pageNumber: 0,
          _links: [],
          parentId: null,
          records: [
            rootFolderRecord({
              id: 100,
              globalId: "FL100",
              name: "Research Projects",
              type: "FOLDER",
            }),
            rootFolderRecord({
              id: 1001,
              globalId: "NB1001",
              name: "A Notebook",
              type: "NOTEBOOK",
            }),
            rootFolderRecord({
              id: 101,
              globalId: "FL101",
              name: "Lab Notebooks",
              type: "FOLDER",
            }),
          ],
        },
      ];
    }
    return [200, { totalHits: 0, pageNumber: 0, records: [] }];
  });

  // Children of folder 100 (paginated: 20 on page 0, 5 on page 1).
  mockAxios.onGet(/\/api\/v1\/folders\/tree\/100(\?.*)?$/).reply((config) => {
    const pageNumber = parseInt((config.params as URLSearchParams | undefined)?.get("pageNumber") ?? "0", 10);
    if (pageNumber === 0) {
      return [
        200,
        {
          totalHits: 25,
          pageNumber: 0,
          _links: [],
          parentId: 100,
          records: Array.from({ length: 20 }, (_, i) => subFolderRecord(200 + i, `Subfolder ${i + 1}`)),
        },
      ];
    }
    return [
      200,
      {
        totalHits: 25,
        pageNumber: 1,
        _links: [],
        parentId: 100,
        records: Array.from({ length: 5 }, (_, i) => subFolderRecord(220 + i, `Subfolder ${20 + i + 1}`)),
      },
    ];
  });

  // Children of any other folder (e.g. subfolders) - empty.
  mockAxios.onGet(/\/api\/v1\/folders\/tree\/\d+(\?.*)?$/).reply(200, {
    totalHits: 0,
    pageNumber: 0,
    _links: [],
    parentId: null,
    records: [],
  });

  // Create folder.
  mockAxios.onPost("/api/v1/folders").reply((config) => {
    const requestBody = JSON.parse(config.data as string) as {
      name: string;
      parentFolderId: number;
    };
    const newFolderId = 999;
    return [
      200,
      {
        id: newFolderId,
        globalId: `FL${newFolderId}`,
        name: requestBody.name,
        created: "2025-01-01T10:00:00.000Z",
        lastModified: "2025-01-01T10:00:00.000Z",
        parentFolderId: requestBody.parentFolderId,
        notebook: false,
        mediaType: null,
        pathToRootFolder: null,
        _links: [
          {
            link: `http://localhost:8080/api/v1/folders/${newFolderId}`,
            rel: "self",
          },
        ],
      },
    ];
  });
};

const getTreeItem = (name: RegExp | string): HTMLElement => screen.getByRole("treeitem", { name });

/*
 * MUI's TreeItem registers its click handler on the inner `.MuiTreeItem-content`
 * element, not on the `<li role="treeitem">` root. In a real browser clicking
 * the row hits the content; in jsdom we must target the content element directly
 * so selection/expansion handlers fire.
 */
const getTreeItemContent = (name: RegExp | string): HTMLElement => {
  const content = getTreeItem(name).querySelector(".MuiTreeItem-content");
  if (!(content instanceof HTMLElement)) {
    throw new Error(`No content element found for tree item "${String(name)}"`);
  }
  return content;
};

describe("FolderTree", () => {
  beforeEach(() => {
    mockAxios.reset();
    setupApiMocks();
  });

  test("Initially displays root folder listing", async () => {
    render(<TestFolderTreeExample />);

    expect(await screen.findByRole("treeitem", { name: "Research Projects" })).toBeVisible();
    expect(screen.getByRole("treeitem", { name: "Lab Notebooks" })).toBeVisible();
  });

  test("Allows selecting folders", async () => {
    render(<TestFolderTreeExample />);

    await screen.findByRole("treeitem", { name: "Research Projects" });

    const researchProjects = getTreeItem("Research Projects");
    const user = userEvent.setup();
    await user.click(getTreeItemContent("Research Projects"));

    // the folder should be selected
    expect(researchProjects).toHaveAttribute("aria-checked", "true");

    // the selected folder details should be displayed
    expect(await screen.findByTestId("selected-folder")).toBeVisible();
    expect(screen.getByTestId("folder-name")).toHaveTextContent("Research Projects");
    expect(screen.getByTestId("folder-id")).toHaveTextContent("100");
  });

  test("Allows expanding folder nodes to show subfolders", async () => {
    render(<TestFolderTreeExample />);

    await screen.findByRole("treeitem", { name: "Research Projects" });

    // Capture the node up front: once expanded, the treeitem's accessible name
    // absorbs its children's text, so a name-based re-query would fail.
    const researchProjects = getTreeItem("Research Projects");
    const user = userEvent.setup();
    await user.click(getTreeItemContent("Research Projects"));

    await waitFor(() => {
      expect(researchProjects).toHaveAttribute("aria-expanded", "true");
    });
    // subfolders loaded and rendered
    expect(await screen.findByText("Subfolder 1")).toBeVisible();
  });

  test("Shows Load More button for folders with more than 20 items", async () => {
    render(<TestFolderTreeExample />);

    await screen.findByRole("treeitem", { name: "Research Projects" });

    const user = userEvent.setup();
    await user.click(getTreeItemContent("Research Projects"));

    // 20 of 25 subfolders loaded -> a Load More button should be visible
    expect((await screen.findAllByRole("button", { name: "common:folderTree.loadMore" }))[0]).toBeVisible();
  });

  test("Loads additional folders when Load More is clicked", async () => {
    render(<TestFolderTreeExample />);

    await screen.findByRole("treeitem", { name: "Research Projects" });

    const user = userEvent.setup();
    await user.click(getTreeItemContent("Research Projects"));

    const loadMore = (await screen.findAllByRole("button", { name: "common:folderTree.loadMore" }))[0];
    await user.click(loadMore);

    // page 1 brings in subfolders 21-25. Query by the label text node rather
    // than the treeitem role: an ancestor treeitem's accessible name also
    // concatenates this child's text, which would match the role query twice.
    expect(await screen.findByText("Subfolder 21")).toBeVisible();
  });

  test("Shows add folder button on hover", async () => {
    render(<TestFolderTreeExample />);

    const researchProjects = await screen.findByRole("treeitem", {
      name: "Research Projects",
    });

    const user = userEvent.setup();
    await user.hover(researchProjects);

    const addButton = within(researchProjects).getByRole("button", {
      name: "common:folderTree.addSubfolder",
    });
    expect(addButton).toBeVisible();
  });

  test("Shows no add folder button on hover if notebook", async () => {
    render(<TestFolderTreeExample />);

    const notebook = await screen.findByRole("treeitem", {
      name: "A Notebook",
    });

    const user = userEvent.setup();
    await user.hover(notebook);

    expect(
      within(notebook).queryByRole("button", {
        name: "Add subfolder to A Notebook",
      }),
    ).not.toBeInTheDocument();
  });

  test("Allows creating new folders", async () => {
    render(<TestFolderTreeExample />);

    const researchProjects = await screen.findByRole("treeitem", {
      name: "Research Projects",
    });

    const user = userEvent.setup();
    // the user clicks the add folder button for a folder
    await user.click(
      within(researchProjects).getByRole("button", {
        name: "common:folderTree.addSubfolder",
      }),
    );

    // the create folder dialog should be open
    const dialog = await screen.findByRole("dialog", {
      name: "common:folderTree.createFolder.title",
    });
    expect(dialog).toBeVisible();

    // the user enters a folder name
    await user.type(within(dialog).getByLabelText("common:folderTree.createFolder.folderName"), "New Test Folder");

    // the user submits the create folder dialog
    await user.click(within(dialog).getByRole("button", { name: "common:actions.create" }));

    // the new folder should appear in the tree and be selected. The new folder
    // is created as a child of "Research Projects", so the parent treeitem's
    // accessible name now also contains "New Test Folder"; the newly created
    // folder is the one that is selected.
    const matches = await screen.findAllByRole("treeitem", {
      name: /New Test Folder/,
    });
    const newFolder = matches.find((item) => item.getAttribute("aria-checked") === "true");
    expect(newFolder).toBeDefined();
    expect(newFolder).toBeVisible();
  });

  test("Prevents creating folders with empty names", async () => {
    render(<TestFolderTreeExample />);

    const researchProjects = await screen.findByRole("treeitem", {
      name: "Research Projects",
    });

    const user = userEvent.setup();
    await user.click(
      within(researchProjects).getByRole("button", {
        name: "common:folderTree.addSubfolder",
      }),
    );

    const dialog = await screen.findByRole("dialog", {
      name: "common:folderTree.createFolder.title",
    });
    expect(dialog).toBeVisible();

    // the user clicks the create button without entering a name
    await user.click(within(dialog).getByRole("button", { name: "common:actions.create" }));

    // a validation error should be displayed
    const errorAlert = await screen.findByLabelText("common:alerts.warning");
    expect(errorAlert).toBeVisible();
    expect(screen.getByText("common:folderTree.errors.folderNameRequired")).toBeVisible();
  });

  test("Should have no axe violations", async () => {
    const { baseElement } = render(<TestFolderTreeExample />);

    await screen.findByRole("treeitem", { name: "Research Projects" });

    await expectAccessible(baseElement);
  });
});
