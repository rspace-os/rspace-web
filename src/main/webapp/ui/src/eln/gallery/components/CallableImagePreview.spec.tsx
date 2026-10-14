import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserMocks";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  CallableImagePreviewStory,
  CallableImagePreviewWithEmptyCaption,
  CallableImagePreviewWithError,
  CallableImagePreviewWithLargeImage,
} from "./CallableImagePreview.story";
import { CallableImagePreviewPage } from "./pageObjects/CallableImagePreviewPage";

const preview = new CallableImagePreviewPage();

beforeEach(() => {
  worker.use(...galleryAppShellHandlers());
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
