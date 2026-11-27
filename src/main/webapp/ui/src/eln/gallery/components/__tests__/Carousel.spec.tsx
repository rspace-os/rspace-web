import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserMocks";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { PdfCarousel } from "../Carousel.story";
import multiPageSamplePdf from "./fixtures/multi-page-sample.pdf?url";
import { CarouselPage } from "./pageObjects/CarouselPage";

const MULTI_PAGE_SAMPLE_PAGE_COUNT = 5;

/*
 * PdfCarousel lists an image (file 1) and then two PDFs (files 2 and 3). Every
 * file downloads from the same endpoint, so the PDFs get the bytes of a real
 * multi-page sample (served by Vite, which MSW lets through) and the image gets
 * placeholder bytes.
 */
let pdfBytes: Promise<ArrayBuffer> | undefined;

beforeEach(() => {
  worker.use(
    ...galleryAppShellHandlers(),
    http.get("/api/v1/files/:id/file", async ({ params }) => {
      if (params.id === "1")
        return new HttpResponse(new Blob(["image"]), { headers: { "Content-Type": "image/jpeg" } });
      pdfBytes ??= fetch(multiPageSamplePdf).then((response) => response.arrayBuffer());
      return new HttpResponse(await pdfBytes, { headers: { "Content-Type": "application/pdf" } });
    }),
  );
});

afterEach(() => {
  cleanup();
});

const carousel = new CarouselPage();

describe("Carousel", () => {
  test("Mounts PDF pages only for the PDF that is shown, not for hidden neighbours", async () => {
    render(<PdfCarousel />);

    // The image is shown; the first PDF is its hidden neighbour and loads in the background.
    await expect.poll(() => carousel.slotCount(0), { timeout: 15000 }).toBe(MULTI_PAGE_SAMPLE_PAGE_COUNT);
    await carousel.settle();
    expect(carousel.mountedPageCount(0)).toBe(0);

    await carousel.nextButton.click();

    // The first PDF is shown; the second is now its hidden neighbour.
    await expect.poll(() => carousel.mountedPageCount(0), { timeout: 15000 }).toBeGreaterThan(0);
    await expect.poll(() => carousel.slotCount(1), { timeout: 15000 }).toBe(MULTI_PAGE_SAMPLE_PAGE_COUNT);
    await carousel.settle();
    expect(carousel.mountedPageCount(1)).toBe(0);
  });
});
