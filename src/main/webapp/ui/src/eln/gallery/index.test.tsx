import { describe, test, expect, beforeEach, afterEach, vi } from "vitest";
/*
 * Under vitest's `--conditions=require` resolution, `react-router` and
 * `react-router-dom` resolve to two separate module copies (the dom package's
 * CJS `main` entry is broken, so it falls back to its ESM build which bundles a
 * second copy of `react-router`). That gives two distinct router-context
 * objects: a `<MemoryRouter>` from one package cannot satisfy hooks from the
 * other (`useRoutes`/`useLocation` throw "may be used only in the context of a
 * <Router>"). React Router 7 merged the DOM bindings into the core package, so
 * everything the Gallery imports from `react-router-dom` is also exported by
 * `react-router`. Redirecting the dom package to the core package collapses the
 * two copies into one shared context. This only affects this test file.
 */
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router");
  return actual;
});
import "@/__tests__/__mocks__/useOauthToken";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/resizeObserver";
import React from "react";
import {
  render,
  screen,
  waitFor,
  cleanup,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import fc from "fast-check";
import { MemoryRouter } from "react-router";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { Gallery } from ".";
import { type GallerySection } from "./common";

/**
 * Equivalent to `GalleryStory` from `./index.story`, but pulling `MemoryRouter`
 * from `react-router` (the same package that the Gallery's `Routes`/`useParams`
 * come from). Under vitest's `--conditions=require` resolution, importing
 * `MemoryRouter` from `react-router-dom` (as the Playwright story does) yields a
 * separate router-context instance, so `useRoutes` cannot see it. Sourcing the
 * router from `react-router` keeps a single shared context.
 */
function GalleryStory({
  urlSuffix,
}: {
  urlSuffix?:
    | `?mediaType=${GallerySection}`
    | `/${number}`
    | `/item/${number}`;
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
  mockAxios
    .onGet("/session/ajax/analyticsProperties")
    .reply(200, { analyticsEnabled: false });

  // AppBar live chat properties (axios.create with baseURL /session/ajax).
  mockAxios
    .onGet("/session/ajax/livechatProperties")
    .reply(200, { livechatEnabled: false });

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
 * `document.title` is normalised by the DOM: runs of whitespace collapse to a
 * single space and leading/trailing whitespace is stripped (this matches the
 * HTML spec, and is what a real browser — and Playwright's `toHaveTitle` —
 * report too). The fast-check arbitraries can generate names containing
 * whitespace, so the expected title must be normalised the same way before
 * comparing.
 */
function normalizeTitle(title: string): string {
  return title.replace(/\s+/g, " ").trim();
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
        expect(document.title).toBe("Images | RSpace Gallery");
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

            /*
             * For some of the pages, we keep the old URL for backwards
             * compatibility but use a more descriptive title
             */
            let expectedTitle: string;
            if (section === "PdfDocuments") {
              expectedTitle = "Exports | RSpace Gallery";
            } else if (section === "Audios") {
              expectedTitle = "Audio | RSpace Gallery";
            } else {
              expectedTitle = `${section} | RSpace Gallery`;
            }
            await waitFor(() => {
              expect(document.title).toBe(expectedTitle);
            });
          },
        ),
        { numRuns: 5 },
      );
    });

    test("On '/{id}', the title should be '{folder name} | RSpace Gallery'", async () => {
      await fc.assert(
        fc.asyncProperty(
          fc.nat(1000),
          fc.string({ minLength: 1, maxLength: 20 }),
          async (id, folderName) => {
            cleanup();
            document.title = "";
            mockAxios.reset();
            mockNetwork();
            mockAxios
              .onGet(/\/api\/v1\/folders\//)
              .reply(200, folderDetails({ id, name: folderName }));

            render(<GalleryStory urlSuffix={`/${id}`} />);

            await waitFor(() => {
              expect(document.title).toBe(
                normalizeTitle(`${folderName} | RSpace Gallery`),
              );
            });
          },
        ),
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
              expect(document.title).toBe(
                normalizeTitle(`${filename}.jpg | RSpace Gallery`),
              );
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
      await user.click(
        await screen.findByRole("button", { name: "Chemistry" }),
      );

      // the breadcrumbs reflect the new section
      await waitFor(() => {
        expect(
          within(
            screen.getByRole("navigation", { name: "Breadcrumbs" }),
          ).getByRole("button", { name: "Chemistry" }),
        ).toBeVisible();
      });

      // The initial listing and one follow-up request after section change.
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toEqual(["Images", "Chemistry"]);
      });
    });

    test("Should handle simultaneous change in path and section", async () => {
      const user = userEvent.setup();
      mockAxios
        .onGet(/\/api\/v1\/folders\//)
        .reply(200, folderDetails({ id: 123, name: "some folder" }));

      render(<GalleryStory urlSuffix="/123" />);

      // wait for the initial folder (Images section) listing to load
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toContain("Images");
      });

      // the user taps on the 'Chemistry' section
      await user.click(
        await screen.findByRole("button", { name: "Chemistry" }),
      );

      // the breadcrumbs reflect the new section
      await waitFor(() => {
        expect(
          within(
            screen.getByRole("navigation", { name: "Breadcrumbs" }),
          ).getByRole("button", { name: "Chemistry" }),
        ).toBeVisible();
      });

      // The initial listing and one follow-up request after section change.
      await waitFor(() => {
        expect(getUploadedFilesMediaTypes()).toEqual(["Images", "Chemistry"]);
      });
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
      await user.click(
        await screen.findByRole("gridcell", { name: "My Snippet" }),
      );

      // open the actions menu and choose Share
      await user.click(screen.getByRole("button", { name: /actions/i }));
      await user.click(
        await screen.findByRole("menuitem", { name: /share/i }),
      );

      // the share dialog for the selected snippet is shown
      const shareDialog = await screen.findByRole("dialog", {
        name: /Share My Snippet/i,
      });

      // pick Bob from the recipient dropdown
      await user.click(
        within(shareDialog).getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        }),
      );
      await user.click(await screen.findByRole("option", { name: /^Bob/ }));

      // save the share
      await user.click(
        within(shareDialog).getByRole("button", { name: /Save/i }),
      );

      // the success toast appears
      expect(
        await screen.findByText(/Shares updated successfully\./i),
      ).toBeVisible();
    });
  });
});
