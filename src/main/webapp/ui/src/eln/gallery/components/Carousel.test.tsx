import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import "@/__tests__/__mocks__/matchMedia";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw/http";
import type React from "react";
import { server } from "@/__tests__/mswServer";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
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
}: {
  asposeEnabled?: boolean;
  downloadsHeldUntil?: Promise<void>;
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
    http.get("/Streamfile/ajax/convert/:id", ({ params }) => {
      conversions.push(String(params.id));
      return HttpResponse.json({ data: `converted${String(params.id)}.pdf` });
    }),
  );
  return { downloads, conversions };
}

function renderCarousel(carousel: React.ReactElement) {
  return render(<DeploymentPropertyContext.Provider value={new Map()}>{carousel}</DeploymentPropertyContext.Provider>);
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
});
