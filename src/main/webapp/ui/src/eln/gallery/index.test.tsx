import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import "@/__tests__/__mocks__/useWebSocketNotifications";
import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import fc from "fast-check";
import { MemoryRouter } from "react-router";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { Gallery } from ".";
import { emptyShareListingHandler, raidIntegrationInfoHandler } from "./__tests__/mocks/raidIntegrationMocks";
import type { GallerySection } from "./common";

/**
 * Equivalent to `GalleryStory` from `./index.story`: wraps the Gallery in a
 * `MemoryRouter` so its `Routes`/`useParams` resolve. MemoryRouter is sourced
 * from `react-router` (the same package the Gallery imports its routing from)
 * so they share a single router context.
 */
function GalleryStory({
  urlSuffix,
}: {
  urlSuffix?: `?mediaType=${GallerySection}` | `/${number}` | `/item/${number}` | `/item/${number}/${string}`;
}) {
  return (
    <LandmarksProvider>
      <MemoryRouter initialEntries={[`/gallery${urlSuffix ?? ""}`]}>
        <Gallery />
      </MemoryRouter>
    </LandmarksProvider>
  );
}

const mockAxios = new MockAdapter(axios);

/**
 * The folder-details payload returned by `/api/v1/folders/*`. The Gallery
 * derives the page title for folder views from the last entry of the resolved
 * path, which is the folder itself.
 *
 * The ancestor chain (Images -> Gallery -> ...) is generated from a compact
 * list rather than repeated as inline objects.
 */
const FOLDER_TIMESTAMP = "2025-07-07T11:09:18.126Z";

type AncestorSpec = {
  id: number;
  name: string;
  mediaType: string | null;
  parentFolderId: number;
};

const defaultAncestors: ReadonlyArray<AncestorSpec> = [
  { id: 131, name: "Images", mediaType: "Images", parentFolderId: 130 },
  { id: 130, name: "Gallery", mediaType: null, parentFolderId: 124 },
];

const ancestor = ({ id, name, mediaType, parentFolderId }: AncestorSpec) => ({
  id,
  globalId: `GF${id}`,
  name,
  created: FOLDER_TIMESTAMP,
  lastModified: FOLDER_TIMESTAMP,
  parentFolderId,
  notebook: false,
  systemFolder: false,
  sharedFolder: false,
  mediaType,
  pathToRootFolder: null,
  _links: [],
});

function folderDetails({
  id = 123,
  name = "Examples",
  ancestors = defaultAncestors,
}: {
  id?: number;
  name?: string;
  ancestors?: ReadonlyArray<AncestorSpec>;
} = {}) {
  return {
    id,
    globalId: `GF${id}`,
    name,
    created: FOLDER_TIMESTAMP,
    lastModified: FOLDER_TIMESTAMP,
    parentFolderId: ancestors[0]?.id ?? 131,
    notebook: false,
    systemFolder: false,
    sharedFolder: false,
    mediaType: "Images",
    pathToRootFolder: ancestors.map(ancestor),
  };
}

/**
 * Mirror the network stubs the Playwright spec installed via `router.route`,
 * for the endpoints the Gallery (and its providers) hit on mount. All of these
 * calls go through `@/common/axios`, so they are mocked via MockAdapter rather
 * than the global fetch mock.
 */
function mockNetwork() {
  // OAuth token (also covered by the useOauthToken mock, but the analytics
  // instance and others may still hit this endpoint directly).
  mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, {
    data: "token",
  });

  // Analytics provider (axios.create with baseURL /session/ajax).
  mockAxios.onGet("/session/ajax/analyticsProperties").reply(200, { analyticsEnabled: false });

  // AppBar live chat properties (axios.create with baseURL /session/ajax).
  mockAxios.onGet("/session/ajax/livechatProperties").reply(200, { livechatEnabled: false });

  // Deployment property lookups (e.g. netfilestores.enabled).
  mockAxios.onGet("/deploymentproperties/ajax/property").reply(200, false);

  // UiPreferences provider.
  mockAxios.onGet("/userform/ajax/preference").reply(200, {});
  mockAxios.onPost("/userform/ajax/preference").reply(200, {});

  // AppBar navigation data.
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, {
    bannerImgSrc: "/public/banner",
    visibleTabs: {
      inventory: true,
      myLabGroups: true,
      published: true,
      system: true,
    },
    userDetails: {
      username: "user1a",
      fullName: "user user",
      email: "user@user.com",
      orcidId: null,
      orcidAvailable: false,
      profileImgSrc: null,
      lastSession: "2025-03-25T15:45:57.000Z",
    },
    operatedAs: false,
    nextMaintenance: null,
  });

  // Collabora / OfficeOnline supported extensions.
  mockAxios.onGet(/\/supportedExts$/).reply(200, {});

  // Sidebar integration lookups (DmpMenuSection, filesystems).
  mockAxios.onGet("/integration/integrationInfo").reply(200, {
    data: {
      name: "DMPTOOL",
      displayName: "DMPtool",
      available: false,
      enabled: false,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });
  mockAxios.onGet("/integration/allIntegrations").reply(200, {
    success: true,
    data: {},
    error: null,
  });
  mockAxios.onGet("/api/v1/gallery/filesystems").reply(200, []);

  // Default folder details lookup.
  mockAxios.onGet(/\/api\/v1\/folders\//).reply(200, folderDetails());

  // Default empty gallery listing.
  mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
    data: {
      parentId: 1,
      items: {
        results: [],
      },
    },
  });
}

/**
 * Returns the sequence of `mediatype` query params for every
 * `/gallery/getUploadedFiles` request that has been made so far.
 */
function getUploadedFilesMediaTypes(): Array<string | null> {
  return mockAxios.history.get
    .filter(({ url }) => /getUploadedFiles/.test(url ?? ""))
    .map(({ params }) => {
      const searchParams = params as URLSearchParams;
      return searchParams.get("mediatype");
    });
}

describe("Gallery", () => {
  beforeEach(() => {
    mockAxios.reset();
    mockNetwork();
    server.use(oauthTokenHandler(), raidIntegrationInfoHandler(), emptyShareListingHandler());
    document.title = "";
  });

  afterEach(() => {
    mockAxios.reset();
    vi.clearAllMocks();
  });

  describe("Should have a title that describes the current page", () => {
    /*
     * This an a11y requirement, under WCAG 2.2 criteria 2.4.2 Page Titled (A),
     * see https://www.w3.org/WAI/WCAG21/Understanding/page-titled.html
     */
    test("On `/gallery', the title should be 'Images | RSpace Gallery'", async () => {
      render(<GalleryStory />);
      /*
       * The images is the default gallery section.
       */
      await waitFor(() => {
        expect(document.title).toBe("gallery:pageTitleWithContext");
      });
    });

    test("A theme-color meta tag should be present in the document head", async () => {
      render(<GalleryStory />);
      await waitFor(() => {
        expect(document.head.querySelector('meta[name="theme-color"]')).not.toBeNull();
      });
    });

    test("On '?mediaType={section}', the title should be '{section} | RSpace Gallery'", async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.constantFrom<GallerySection>(
            "Images",
            "Audios",
            "Videos",
            "Documents",
            "Chemistry",
            "DMPs",
            "Snippets",
            "Miscellaneous",
            "PdfDocuments",
          ),
          async (section) => {
            cleanup();
            document.title = "";
            render(<GalleryStory urlSuffix={`?mediaType=${section}`} />);

            await waitFor(() => {
              expect(document.title).toBe("gallery:pageTitleWithContext");
            });
          },
        ),
        { numRuns: 5 },
      );
    });

    test("On '/{id}', the title should be '{folder name} | RSpace Gallery'", async () => {
      await fc.assert(
        fc.asyncProperty(fc.nat(1000), fc.string({ minLength: 1, maxLength: 20 }), async (id, folderName) => {
          cleanup();
          document.title = "";
          mockAxios.reset();
          mockNetwork();
          mockAxios.onGet(/\/api\/v1\/folders\//).reply(200, folderDetails({ id, name: folderName }));

          render(<GalleryStory urlSuffix={`/${id}`} />);

          await waitFor(() => {
            expect(document.title).toBe("gallery:pageTitleWithContext");
          });
        }),
        { numRuns: 5 },
      );
    });

    test("On '/item/{id}', the title should be '{filename} | RSpace Gallery'", async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.integer({ min: 1, max: 10000 }),
          fc.string({ minLength: 1, maxLength: 20 }),
          async (id, filename) => {
            cleanup();
            document.title = "";
            mockAxios.reset();
            mockNetwork();
            mockAxios.onGet(`/api/v1/files/${id}`).reply(200, {
              id,
              globalId: `GL${id}`,
              name: `${filename}.jpg`,
              caption: null,
              contentType: "image/jpeg",
              created: "2025-07-07T11:09:18.312Z",
              size: 40721,
              version: 1,
              parentFolderId: 123,
            });

            render(<GalleryStory urlSuffix={`/item/${id}`} />);

            await waitFor(() => {
              expect(document.title).toBe("gallery:pageTitleWithContext");
            });
          },
        ),
        { numRuns: 5 },
      );
    });
  });

  describe("Network calls on state change", () => {
    test("Changing section should only make one request to the server", async () => {
      const user = userEvent.setup();
      render(<GalleryStory urlSuffix="?mediaType=Images" />);

      // wait for the initial Images listing to load
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toContain("Images");
      });

      // the user taps on the 'Chemistry' section
      await user.click(await screen.findByRole("button", { name: "gallery:sections.chemistry" }));

      // the breadcrumbs reflect the new section
      await waitFor(() => {
        expect(
          within(screen.getByRole("navigation", { name: "gallery:mainPanel.breadcrumbsLabel" })).getByRole("button", {
            name: "gallery:sections.chemistry",
          }),
        ).toBeVisible();
      });

      // The initial listing and one follow-up request after section change.
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toEqual(["Images", "Chemistry"]);
      });
    });

    test("Should handle simultaneous change in path and section", async () => {
      const user = userEvent.setup();
      mockAxios.onGet(/\/api\/v1\/folders\//).reply(200, folderDetails({ id: 123, name: "some folder" }));

      render(<GalleryStory urlSuffix="/123" />);

      // wait for the initial folder (Images section) listing to load
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toContain("Images");
      });

      // the user taps on the 'Chemistry' section
      await user.click(await screen.findByRole("button", { name: "gallery:sections.chemistry" }));

      // the breadcrumbs reflect the new section
      await waitFor(() => {
        expect(
          within(screen.getByRole("navigation", { name: "gallery:mainPanel.breadcrumbsLabel" })).getByRole("button", {
            name: "gallery:sections.chemistry",
          }),
        ).toBeVisible();
      });

      // The initial listing and one follow-up request after section change.
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toEqual(["Images", "Chemistry"]);
      });
    });
  });

  describe("Pinned version view", () => {
    const PINNED_ITEM_ID = 42;

    /** The live item, at version 3. */
    function mockLiveItem() {
      mockAxios.onGet(`/api/v1/files/${PINNED_ITEM_ID}`).reply(200, {
        id: PINNED_ITEM_ID,
        globalId: `GL${PINNED_ITEM_ID}`,
        name: "assay.png",
        contentType: "image/png",
        created: "2025-07-07T11:09:18.312Z",
        size: 40721,
        version: 3,
        parentFolderId: 123,
      });
    }

    /**
     * Its version history: versions 1 and 3, newest last. Version 1 was a
     * differently named file, as a new version may be.
     */
    function mockVersionHistory() {
      mockAxios.onGet(`/gallery/ajax/versionHistory/${PINNED_ITEM_ID}`).reply(200, {
        data: {
          revisions: [
            {
              revisionId: 10,
              revisionType: "MOD",
              record: {
                version: 1,
                lastModified: "2026-06-11T09:30:00Z",
                modifiedByFullName: "Alice Smith",
                size: 918,
                name: "first-draft.png",
                description: "A rough cut",
              },
            },
            {
              revisionId: 20,
              revisionType: "MOD",
              record: {
                version: 3,
                lastModified: "2026-06-12T09:30:00Z",
                modifiedByFullName: "Alice Smith",
                size: 40721,
                name: "assay.png",
                description: "A draft assay",
              },
            },
          ],
          revisionsCount: 2,
        },
        error: null,
        success: true,
      });
    }

    /** A listing containing the pinned item, so it can be selected. */
    function mockListingWithItem() {
      mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
        data: {
          parentId: 123,
          items: {
            totalHits: 1,
            totalPages: 1,
            results: [
              {
                id: PINNED_ITEM_ID,
                oid: { idString: `GL${PINNED_ITEM_ID}` },
                name: "assay.png",
                ownerId: 1,
                ownerFullName: "Test User",
                ownerUsername: "testuser",
                description: "A draft assay",
                creationDate: 1672531200,
                modificationDate: 1672531200,
                type: "Image",
                systemFolder: false,
                sharedFolder: false,
                extension: "png",
                thumbnailId: null,
                size: 40721,
                version: 3,
                originalImageOid: null,
              },
            ],
          },
        },
        error: null,
        success: true,
        errorMsg: null,
      });
    }

    test("says which version is shown, and that it is locked", async () => {
      mockLiveItem();
      mockVersionHistory();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      expect(await screen.findByText("gallery:pinnedVersion.notice")).toBeVisible();
    });

    /**
     * The notice, and so its link, appears both in the banner and in the
     * InfoPanel, hence every one of them is checked rather than a single match.
     */
    async function findWayBackLinks() {
      return await screen.findAllByRole("link", {
        name: "gallery:pinnedVersion.viewLatest",
      });
    }

    test("offers a way back to the live item beside the notice", async () => {
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      const links = await findWayBackLinks();
      // real hrefs, so they can be copied or opened in a new tab
      for (const link of links) {
        expect(link).toHaveAttribute("href", `/gallery/item/${PINNED_ITEM_ID}`);
      }
    });

    test("following that link leaves the locked view for the editable one", async () => {
      const user = userEvent.setup();
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      const [link] = await findWayBackLinks();
      await user.click(link);

      await waitFor(() => {
        expect(screen.queryByText("gallery:pinnedVersion.notice")).not.toBeInTheDocument();
      });
      /*
       * The selection keys on the item id, which the decorator delegates, so the
       * live object replaces the pinned one rather than joining it. Matched
       * loosely because the tile's version badge, shown from version 2 onwards,
       * contributes its own label to the accessible name.
       */
      expect(await screen.findByRole("gridcell", { name: /assay\.png/ })).toBeInTheDocument();
      for (const field of await screen.findAllByDisplayValue("A draft assay")) {
        expect(field).not.toHaveAttribute("readonly");
      }
    });

    test("shows the notice outside the info panel, which is closed on a small viewport", async () => {
      mockLiveItem();
      mockVersionHistory();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      /*
       * The banner sits above the listing rather than inside the InfoPanel
       * drawer, so it is not inside the complementary landmark the panel uses.
       */
      const notice = await screen.findByText("gallery:pinnedVersion.notice");
      expect(notice.closest('[role="complementary"]')).toBeNull();
    });

    test("refuses to edit the pinned item, while still allowing a download", async () => {
      const user = userEvent.setup();
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();
      mockAxios.onGet(/\/gallery\/ajax\/getLinkedDocuments\//).reply(200, {
        data: [],
        error: null,
        success: true,
      });

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      // the item named in the URL is selected automatically
      await user.click(await screen.findByRole("button", { name: "gallery:actionsMenu.actions" }));

      /*
       * The decoration happens in the listing, so the Actions menu sees the
       * pinned object and refuses through the predicates it already consults.
       */
      expect(await screen.findByRole("menuitem", { name: /common:actions.delete/ })).toHaveAttribute(
        "aria-disabled",
        "true",
      );
      expect(screen.getByRole("menuitem", { name: /common:actions.export/ })).toHaveAttribute("aria-disabled", "true");
      expect(screen.getByRole("menuitem", { name: /common:actions.download/ })).not.toHaveAttribute("aria-disabled");
    });

    test("shows the pinned version's filename and image, not the live item's", async () => {
      /*
       * The reported bug: version 1 was a different picture under a different
       * name, and both came out as version 3's. The name comes from the audit
       * row, and the image from /Streamfile, which is version-aware where
       * /gallery/getThumbnail is not.
       */
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      const tile = await screen.findByRole("gridcell", { name: "first-draft.png" });
      // the tile's image is decorative, so it is queried by tag rather than role
      expect(tile.querySelector("img")).toHaveAttribute("src", `/Streamfile/${PINNED_ITEM_ID}?version=1`);
      expect(screen.queryByRole("gridcell", { name: "assay.png" })).not.toBeInTheDocument();
    });

    test("shows the pinned version's description, not the live item's", async () => {
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      expect(await screen.findAllByDisplayValue("A rough cut")).not.toHaveLength(0);
      expect(screen.queryAllByDisplayValue("A draft assay")).toHaveLength(0);
    });

    test("the description cannot be edited on a pinned version", async () => {
      // an edit here would have altered the live item while the page says "locked"
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/1`} />);

      for (const field of await screen.findAllByDisplayValue("A rough cut")) {
        expect(field).toHaveAttribute("readonly");
      }
    });

    test("the description is still editable on the live item", async () => {
      mockLiveItem();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}`} />);

      for (const field of await screen.findAllByDisplayValue("A draft assay")) {
        expect(field).not.toHaveAttribute("readonly");
      }
    });

    test("a version the item does not have is reported, not quietly replaced with the live one", async () => {
      mockLiveItem();
      mockVersionHistory();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/2`} />);

      expect(await screen.findByText("gallery:landingPage.error")).toBeVisible();
      expect(screen.queryByText("gallery:pinnedVersion.notice")).not.toBeInTheDocument();
    });

    test("a version that is not a number is reported", async () => {
      mockLiveItem();
      mockVersionHistory();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/latest`} />);

      expect(await screen.findByText("gallery:landingPage.error")).toBeVisible();
    });

    test("pinning the live version shows the ordinary editable view, not a locked one", async () => {
      mockLiveItem();
      mockVersionHistory();
      mockListingWithItem();

      render(<GalleryStory urlSuffix={`/item/${PINNED_ITEM_ID}/3`} />);

      await waitFor(() => {
        expect(document.title).toBe("gallery:pageTitleWithContext");
      });
      expect(screen.queryByText("gallery:pinnedVersion.notice")).not.toBeInTheDocument();
    });
  });

  describe("Sharing integration", () => {
    test("Saving a snippet share from Gallery should surface the success alert", async () => {
      const user = userEvent.setup();

      // A single snippet in the listing.
      mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
        data: {
          parentId: 1,
          items: {
            totalHits: 1,
            totalPages: 1,
            results: [
              {
                id: 3,
                oid: { idString: "SD3" },
                name: "My Snippet",
                ownerId: 1,
                ownerFullName: "Test User",
                ownerUsername: "testuser",
                description: null,
                creationDate: 1672531200,
                modificationDate: 1672531200,
                type: "Snippet",
                systemFolder: false,
                sharedFolder: false,
                extension: "txt",
                thumbnailId: null,
                size: 512,
                version: 1,
                originalImageOid: { idString: "SD3" },
              },
            ],
          },
        },
        error: null,
        success: true,
        errorMsg: null,
      });
      mockAxios.onGet(/\/gallery\/ajax\/getLinkedDocuments\//).reply(200, {
        data: [],
        error: null,
        success: true,
        errorMsg: null,
      });
      mockAxios.onGet("/api/v1/userDetails/whoami").reply(200, {
        id: 1,
        username: "testuser",
        email: "test@example.com",
        firstName: "Test",
        lastName: "User",
        hasPiRole: false,
        hasSysAdminRole: false,
        workbenchId: 1,
      });
      mockAxios.onGet("/api/v1/share/document/3").reply(200, {
        sharedDocId: 3,
        sharedDocName: "My Snippet",
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
      mockAxios.onGet(/\/api\/v1\/folders\/1/).reply(200, {
        id: 1,
        globalId: "FL1",
        name: "alice-bob",
        created: "2025-09-09T12:05:14.109Z",
        lastModified: "2025-09-09T12:05:14.109Z",
        parentFolderId: 124,
        notebook: false,
        systemFolder: false,
        sharedFolder: false,
        mediaType: null,
        pathToRootFolder: [],
        _links: [],
      });
      mockAxios.onPost("/api/v1/share").reply(200, {
        shareInfos: [],
        failedShares: [],
        _links: [],
      });

      render(<GalleryStory urlSuffix="?mediaType=Images" />);

      // the snippet appears in the listing
      await user.click(await screen.findByRole("gridcell", { name: "My Snippet" }));

      // open the actions menu and choose Share
      await user.click(screen.getByRole("button", { name: "gallery:actionsMenu.actions" }));
      await user.click(await screen.findByRole("menuitem", { name: "common:actions.share" }));

      // the share dialog for the selected snippet is shown
      const shareDialog = await screen.findByRole("dialog", {
        name: "common:shareDialog.titleSingle",
      });

      // pick Bob from the recipient dropdown
      await user.click(
        within(shareDialog).getByRole("combobox", {
          name: "common:shareDialog.autocomplete.label",
        }),
      );
      await user.click(await screen.findByRole("option", { name: /^Bob/ }));

      // save the share
      await user.click(within(shareDialog).getByRole("button", { name: "common:actions.save" }));

      // the success toast appears
      expect(await screen.findByText("common:shareDialog.updatedSuccessfully")).toBeVisible();
    });
  });
});
