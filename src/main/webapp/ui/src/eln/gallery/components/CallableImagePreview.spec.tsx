import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  CallableImagePreviewStory,
  CallableImagePreviewWithEmptyCaption,
  CallableImagePreviewWithError,
  CallableImagePreviewWithLargeImage,
} from "./CallableImagePreview.story";
import { CallableImagePreviewPage } from "./pageObjects/CallableImagePreviewPage";

/*
 * IMAGE MOCKING: PhotoSwipe loads the image URL via a real <img> fetch. We
 * serve every https://via.placeholder.com/** request with a minimal 1×1
 * transparent SVG so the onLoad event fires and PhotoSwipe can open.
 * The invalid-URL case returns a 404, which means the image never loads and
 * PhotoSwipe is never opened (matching the Playwright spec's expectation that
 * the trigger button remains visible with no modal).
 *
 * analyticsProperties, preference*, and property* endpoints are already
 * covered by appShellHandlers (analyticsProperties) and galleryAppShellHandlers
 * (preference*, property*) — we do NOT re-mock them here.
 */

const placeholderImageHandler = () =>
  http.get(
    "https://via.placeholder.com/*",
    () =>
      /*
       * Return a minimal SVG as the image body. The component only needs the
       * onLoad event to fire (to get image dimensions and open PhotoSwipe); it
       * does not inspect the actual pixel data. Using an SVG avoids the need for
       * `atob`/binary decoding, which is unreliable inside MSW handler scope
       * when running under Vitest browser mode's Playwright provider.
       */
      new HttpResponse(`<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"></svg>`, {
        status: 200,
        headers: {
          "Content-Type": "image/svg+xml",
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET",
        },
      }),
  );

const invalidImageHandler = () =>
  http.get("https://invalid-url-that-should-fail.example.com/*", () => new HttpResponse("Not Found", { status: 404 }));

const preview = new CallableImagePreviewPage();

beforeEach(() => {
  worker.use(...galleryAppShellHandlers(), placeholderImageHandler(), invalidImageHandler());
});

afterEach(() => {
  cleanup();
});

describe("CallableImagePreview", () => {
  describe("Component mounting and rendering", () => {
    test("Should render the component without errors", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
    });

    test("Should render all interactive buttons", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
      await expect.element(preview.openImageButton).toBeEnabled();
      await expect.element(preview.openImageWithCaptionButton).toBeVisible();
      await expect.element(preview.openImageWithCaptionButton).toBeEnabled();
      await expect.element(preview.openSmallImageButton).toBeVisible();
      await expect.element(preview.openSmallImageButton).toBeEnabled();
    });
  });

  describe("PhotoSwipe modal functionality", () => {
    test("Should open PhotoSwipe modal when image preview is triggered", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
      await preview.clickOpenImage();
      await expect.element(preview.openModal).toBeVisible();
    });

    test("Should display image in the modal", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
      await preview.clickOpenImage();
      await expect.element(preview.openModal).toBeVisible();
      await expect.element(preview.modalImage).toBeVisible();
    });

    test("Should close modal when close button is clicked", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
      await preview.clickOpenImage();
      await expect.element(preview.openModal).toBeVisible();
      await preview.closeModal();
      await expect.element(preview.openModal).not.toBeInTheDocument();
    });
  });

  describe("Caption functionality", () => {
    test("Should display caption in the modal", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageWithCaptionButton).toBeVisible();
      await preview.clickOpenImageWithCaption();
      await expect.element(preview.openModal).toBeVisible();
      /*
       * The caption plugin is only activated when the caption array is non-empty.
       * We verify the caption container is visible and contains the expected text.
       */
      await expect.element(preview.caption).toBeVisible();
      await expect.element(preview.caption).toHaveTextContent("Test Image Caption");
    });

    test("Should handle empty captions", async () => {
      render(<CallableImagePreviewWithEmptyCaption />);
      await expect.element(preview.openImageWithEmptyCaptionButton).toBeVisible();
      await preview.clickOpenImageWithEmptyCaption();
      await expect.element(preview.openModal).toBeVisible();
    });
  });

  describe("Different image sizes", () => {
    test("Should handle small images", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openSmallImageButton).toBeVisible();
      await preview.clickOpenSmallImage();
      await expect.element(preview.openModal).toBeVisible();
    });

    test("Should handle large images", async () => {
      render(<CallableImagePreviewWithLargeImage />);
      await expect.element(preview.openLargeImageButton).toBeVisible();
      await preview.clickOpenLargeImage();
      await expect.element(preview.openModal).toBeVisible();
    });
  });

  describe("Error handling", () => {
    test("Should handle invalid image URLs gracefully", async () => {
      /*
       * When the image request returns 404 the <img> onLoad never fires so
       * PhotoSwipe is never opened — the trigger button stays visible with no
       * modal. This matches the Playwright spec's expectation.
       */
      render(<CallableImagePreviewWithError />);
      await expect.element(preview.openInvalidImageButton).toBeVisible();
      await preview.clickOpenInvalidImage();
      /*
       * Give the browser a moment to attempt the fetch and confirm it failed —
       * the button must remain visible and no modal must appear.
       */
      await expect.element(preview.openInvalidImageButton).toBeVisible();
      await expect.element(preview.openModal).not.toBeInTheDocument();
    });
  });

  describe("Accessibility", () => {
    test("Should have no axe violations", async () => {
      render(<CallableImagePreviewStory />);
      await expect.element(preview.openImageButton).toBeVisible();
      await expectNoAxeViolations();
    });
  });
});
