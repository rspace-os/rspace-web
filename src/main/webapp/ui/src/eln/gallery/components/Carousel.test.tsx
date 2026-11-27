import { afterEach, describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/useOauthToken";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import React from "react";
import { server } from "@/__tests__/mswServer";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import * as FetchingData from "@/util/fetchingData";
import { Optional } from "@/util/optional";
import { Filestore, RemoteFile, useGalleryListing } from "../useGalleryListing";
import { GallerySelection } from "../useGallerySelection";
import Carousel from "./Carousel";
import { CarouselOpenedOnFifthFile, OfficeDocumentCarousel, SimpleCarousel } from "./Carousel.story";

// pdf.js cannot load documents under jsdom; expose the loaded URL instead.
vi.mock("react-pdf", () => ({
  pdfjs: { GlobalWorkerOptions: {} },
  Document: ({ file }: { file: string }) => <div role="document" aria-label={file} />,
  Page: () => null,
}));

const PREVIEW = { name: "gallery:carousel.previewAlt" };

/*
 * Records which files had their content downloaded or were sent for Aspose
 * conversion. aspose.enabled is served asynchronously, so it is still loading
 * when the previews mount. Downloads are not answered until
 * `downloadsHeldUntil` resolves.
 */
function stubEndpoints({
  asposeEnabled = false,
  downloadsHeldUntil = Promise.resolve(),
  conversionsHeldUntil = Promise.resolve(),
}: {
  asposeEnabled?: boolean;
  downloadsHeldUntil?: Promise<void>;
  conversionsHeldUntil?: Promise<void>;
} = {}) {
  const downloads: Array<string> = [];
  const conversions: Array<string> = [];
  server.use(
    http.get("/deploymentproperties/ajax/property", ({ request }) =>
      HttpResponse.json(asposeEnabled && new URL(request.url).searchParams.get("name") === "aspose.enabled"),
    ),
    http.get("/collaboraOnline/supportedExts", () => HttpResponse.json({})),
    http.get("/officeOnline/supportedExts", () => HttpResponse.json({})),
    http.get("/api/v1/files/:id/file", async ({ params }) => {
      downloads.push(String(params.id));
      await downloadsHeldUntil;
      return new HttpResponse(new Blob(["image"]), { headers: { "Content-Type": "image/jpeg" } });
    }),
    http.get("/Streamfile/ajax/convert/:id", async ({ params }) => {
      conversions.push(String(params.id));
      await conversionsHeldUntil;
      return HttpResponse.json({ data: `converted${String(params.id)}.pdf` });
    }),
  );
  return { downloads, conversions };
}

/*
 * Serves a listing of one image, file 1 at `file.version` named `file.name`,
 * so that a test can change it between refreshes.
 */
function stubListing(file: { version: number; name: string }) {
  server.use(
    http.get("/gallery/getUploadedFiles", () =>
      HttpResponse.json({
        data: {
          items: {
            totalHits: 1,
            totalPages: 1,
            results: [
              {
                id: 1,
                oid: { idString: "GL1" },
                name: file.name,
                type: "Image",
                extension: "jpg",
                creationDate: 1729609058000,
                modificationDate: 1729609058000,
                description: null,
                thumbnailId: null,
                size: 5,
                version: file.version,
              },
            ],
          },
        },
      }),
    ),
  );
}

// The listing hook drives the carousel, as in the Gallery, with a button to soft-refresh it.
function RefreshableCarousel() {
  const listingOf = React.useMemo(() => ({ tag: "section" as const, section: "Images" as const, path: [] }), []);
  const { galleryListing, refreshListing } = useGalleryListing({
    listingOf,
    searchTerm: "",
    sortOrder: "DESC",
    orderBy: "modificationDate",
  });
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <button type="button" onClick={() => void refreshListing()}>
          {"Refresh"}
        </button>
        {FetchingData.getSuccessValue(galleryListing)
          .map((listing) =>
            listing.tag === "list" ? (
              <>
                <p>{listing.list.map(({ name }) => name).join(", ")}</p>
                <Carousel listing={listing} />
              </>
            ) : null,
          )
          .orElse(null)}
      </LandmarksProvider>
    </ThemeProvider>
  );
}

function withProviders(carousel: React.ReactNode, queryClient: QueryClient) {
  return (
    <QueryClientProvider client={queryClient}>
      <DeploymentPropertyContext.Provider value={new Map()}>{carousel}</DeploymentPropertyContext.Provider>
    </QueryClientProvider>
  );
}

function renderCarousel(carousel: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(withProviders(carousel, queryClient));
  return {
    ...result,
    rerender: (nextCarousel: React.ReactNode) => result.rerender(withProviders(nextCarousel, queryClient)),
  };
}

// Waits until the carousel is at `position` and every mounted image preview shows its downloaded content.
async function waitForPreviewsToLoad(position: string, mountedCount: number) {
  expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent(position);
  await waitFor(() => {
    expect(screen.getByRole("img", PREVIEW)).toHaveAttribute("src", expect.stringMatching(/^blob:/));
    const previews = screen.getAllByRole("img", { ...PREVIEW, hidden: true });
    expect(previews).toHaveLength(mountedCount);
    for (const preview of previews) expect(preview).toHaveAttribute("src", expect.stringMatching(/^blob:/));
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("Carousel", () => {
  test("Should have no axe violations", async () => {
    stubEndpoints();
    const { baseElement } = renderCarousel(<SimpleCarousel />);
    await screen.findByRole("img", PREVIEW);

    /*
     * The original Playwright spec filtered out the `landmark-one-main`,
     * `page-has-heading-one` and `region` violations, which are expected when
     * testing an isolated component rather than a full page. Under jsdom the
     * sa11y default ruleset does not flag those structural rules for an isolated
     * component, and colour-contrast rules cannot run, so a plain check passes.
     */
    await expectAccessible(baseElement);
  });

  test("Should show an indicator of progress through listing.", async () => {
    stubEndpoints();
    const user = userEvent.setup();
    renderCarousel(<SimpleCarousel />);
    await screen.findByRole("img", PREVIEW);

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("1 / 8");

    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));

    expect(screen.getByRole("status", { name: "gallery:carousel.currentFileIndex" })).toHaveTextContent("2 / 8");
  });

  test("Moving to a different file resets the zoom level", async () => {
    stubEndpoints();
    const user = userEvent.setup();
    renderCarousel(<SimpleCarousel />);
    await screen.findByRole("img", PREVIEW);

    await user.click(screen.getByRole("button", { name: "common:actions.zoomIn" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));

    expect(screen.getByRole("button", { name: "common:actions.resetZoom" })).toBeDisabled();
  });

  test("Only the visible file and its neighbour are rendered when the carousel opens", async () => {
    stubEndpoints();
    renderCarousel(<SimpleCarousel />);

    await waitFor(() => {
      expect(screen.getAllByRole("img", { ...PREVIEW, hidden: true })).toHaveLength(2);
    });
  });

  test("Only the visible file and its neighbours download their content", async () => {
    const { downloads } = stubEndpoints();
    const user = userEvent.setup();
    renderCarousel(<SimpleCarousel />);
    await waitForPreviewsToLoad("1 / 8", 2);
    expect([...downloads].sort()).toEqual(["1", "2"]);

    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));

    await waitForPreviewsToLoad("3 / 8", 3);
    expect([...downloads].sort()).toEqual(["1", "2", "3", "4"]);
  });

  test("Opening on a selected file downloads only that file and its neighbours", async () => {
    const { downloads } = stubEndpoints();
    renderCarousel(<CarouselOpenedOnFifthFile />);

    await waitForPreviewsToLoad("5 / 8", 3);
    expect([...downloads].sort()).toEqual(["4", "5", "6"]);
  });

  test("A file that is remounted while its download is in flight is downloaded once", async () => {
    let releaseDownloads = () => {};
    const { downloads } = stubEndpoints({
      downloadsHeldUntil: new Promise((resolve) => {
        releaseDownloads = resolve;
      }),
    });
    const user = userEvent.setup();
    renderCarousel(<SimpleCarousel />);
    await waitFor(() => {
      expect(downloads).toContain("1");
    });

    // two steps forward unmounts file 1, one step back remounts it as a neighbour
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.previous" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.previous" }));
    releaseDownloads();

    await waitForPreviewsToLoad("1 / 8", 2);
    expect([...downloads].sort()).toEqual(["1", "2", "3", "4"]);
  });

  test("Office documents are converted only when visible, and only once each", async () => {
    const { conversions } = stubEndpoints({ asposeEnabled: true });
    const user = userEvent.setup();
    renderCarousel(<OfficeDocumentCarousel />);
    await screen.findByRole("document", { name: "/Streamfile/direct/1?fileName=converted1.pdf" });

    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await screen.findByRole("document", { name: "/Streamfile/direct/2?fileName=converted2.pdf" });
    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await screen.findByRole("document", { name: "/Streamfile/direct/3?fileName=converted3.pdf" });
    await user.click(screen.getByRole("button", { name: "gallery:carousel.previous" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.previous" }));
    await screen.findByRole("document", { name: "/Streamfile/direct/1?fileName=converted1.pdf" });

    expect(conversions).toEqual(["1", "2", "3"]);
  });

  test("Stepping past an Office document and back while it converts reuses the conversion in flight", async () => {
    let releaseConversions = () => {};
    const { conversions } = stubEndpoints({
      asposeEnabled: true,
      conversionsHeldUntil: new Promise((resolve) => {
        releaseConversions = resolve;
      }),
    });
    const user = userEvent.setup();
    renderCarousel(<OfficeDocumentCarousel />);
    await screen.findByText("gallery:carousel.generatingPdf");

    await user.click(screen.getByRole("button", { name: "gallery:carousel.next" }));
    await user.click(screen.getByRole("button", { name: "gallery:carousel.previous" }));
    releaseConversions();

    await screen.findByRole("document", { name: "/Streamfile/direct/1?fileName=converted1.pdf" });
    expect(conversions.filter((id) => id === "1")).toEqual(["1"]);
  });

  test("A failed conversion is not retried when the window regains focus or the network reconnects", async () => {
    stubEndpoints({ asposeEnabled: true });
    const conversions: Array<string> = [];
    server.use(
      http.get("/Streamfile/ajax/convert/:id", ({ params }) => {
        conversions.push(String(params.id));
        return new HttpResponse(null, { status: 500 });
      }),
    );
    renderCarousel(<OfficeDocumentCarousel />);
    await waitFor(() => {
      expect(conversions).toEqual(["1"]);
    });
    await waitFor(() => {
      expect(screen.queryByText("gallery:carousel.generatingPdf")).not.toBeInTheDocument();
    });

    fireEvent(window, new Event("visibilitychange"));
    fireEvent(window, new Event("offline"));
    fireEvent(window, new Event("online"));
    await act(() => new Promise((resolve) => setTimeout(resolve, 100)));

    expect(conversions).toEqual(["1"]);
  });

  test("Reopening the carousel does not convert an Office document again", async () => {
    const { conversions } = stubEndpoints({ asposeEnabled: true });
    const { rerender } = renderCarousel(<OfficeDocumentCarousel />);
    await screen.findByRole("document", { name: "/Streamfile/direct/1?fileName=converted1.pdf" });

    rerender(null);
    expect(screen.queryByRole("region", { name: "gallery:carousel.label" })).not.toBeInTheDocument();
    rerender(<OfficeDocumentCarousel />);

    await screen.findByRole("document", { name: "/Streamfile/direct/1?fileName=converted1.pdf" });
    expect(conversions).toEqual(["1"]);
  });

  test("Refreshing the listing while a preview downloads leaves it showing a live URL, downloaded once", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    let releaseDownloads = () => {};
    const { downloads } = stubEndpoints({
      downloadsHeldUntil: new Promise((resolve) => {
        releaseDownloads = resolve;
      }),
    });
    stubListing({ version: 1, name: "before.jpg" });
    const user = userEvent.setup();
    renderCarousel(
      <GallerySelection>
        <RefreshableCarousel />
      </GallerySelection>,
    );
    await waitFor(() => {
      expect(downloads).toEqual(["1"]);
    });

    // a rename does not change the version, and shows when the refreshed listing is in place
    stubListing({ version: 1, name: "after.jpg" });
    await user.click(screen.getByRole("button", { name: "Refresh" }));
    await screen.findByText("after.jpg");
    releaseDownloads();

    await waitForPreviewsToLoad("1 / 1", 1);
    expect(revokeObjectURL).not.toHaveBeenCalledWith(screen.getByRole("img", PREVIEW).getAttribute("src"));
    expect(downloads).toEqual(["1"]);
  });

  test("Replacing a filestore file that changed outside RSpace reloads its preview", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    stubEndpoints();
    server.use(
      http.get(
        "/api/v1/gallery/filestores/:id/download",
        () => new HttpResponse(new Blob(["image"]), { headers: { "Content-Type": "image/jpeg" } }),
      ),
    );
    const filestore = new Filestore({
      id: 42,
      name: "Filestore",
      filesystemId: 10,
      filesystemName: "S3",
      filesystemType: "S3",
      canWrite: true,
      ownerName: "gallery:unknownOwner",
    });
    const remoteImage = (modifiedAt: string) =>
      new RemoteFile({
        nfsId: 7,
        name: "image.jpg",
        folder: false,
        fileSize: 5,
        modificationDate: new Date(modifiedAt),
        path: [filestore],
        logicPath: "42:/image.jpg",
        token: "",
        ownerName: "gallery:unknownOwner",
      });
    const carouselOf = (file: RemoteFile) => (
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <LandmarksProvider>
          <GallerySelection>
            <Carousel
              listing={{ tag: "list", list: [file], totalHits: 1, loadMore: Optional.empty(), refreshing: false }}
            />
          </GallerySelection>
        </LandmarksProvider>
      </ThemeProvider>
    );
    const previous = remoteImage("2026-01-01T00:00:00Z");
    const { rerender } = renderCarousel(carouselOf(previous));
    await waitForPreviewsToLoad("1 / 1", 1);
    const firstSrc = screen.getByRole("img", PREVIEW).getAttribute("src");

    const next = remoteImage("2026-02-01T00:00:00Z");
    expect(next.takeOverDownloadOf(previous)).toBe(false);
    previous.deconstructor();
    rerender(carouselOf(next));

    await waitFor(() => {
      expect(screen.getByRole("img", PREVIEW)).not.toHaveAttribute("src", firstSrc);
    });
    await waitForPreviewsToLoad("1 / 1", 1);
    expect(revokeObjectURL).toHaveBeenCalledWith(firstSrc);
  });

  test("Refreshing the listing onto a new version of the file reloads its preview", async () => {
    const revokeObjectURL = vi.spyOn(URL, "revokeObjectURL");
    const { downloads } = stubEndpoints();
    stubListing({ version: 1, name: "image.jpg" });
    const user = userEvent.setup();
    renderCarousel(
      <GallerySelection>
        <RefreshableCarousel />
      </GallerySelection>,
    );
    await screen.findByText("image.jpg");
    await waitForPreviewsToLoad("1 / 1", 1);
    const firstSrc = screen.getByRole("img", PREVIEW).getAttribute("src");

    stubListing({ version: 2, name: "image.jpg" });
    await user.click(screen.getByRole("button", { name: "Refresh" }));

    await waitFor(() => {
      expect(screen.getByRole("img", PREVIEW)).not.toHaveAttribute("src", firstSrc);
    });
    await waitForPreviewsToLoad("1 / 1", 1);
    expect(revokeObjectURL).toHaveBeenCalledWith(firstSrc);
    expect(revokeObjectURL).not.toHaveBeenCalledWith(screen.getByRole("img", PREVIEW).getAttribute("src"));
    expect(downloads).toEqual(["1", "1"]);
  });
});
