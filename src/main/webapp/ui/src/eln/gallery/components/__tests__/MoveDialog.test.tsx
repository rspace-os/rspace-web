import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen } from "@/__tests__/customQueries";
import { MoveDialogStory } from "../MoveDialog.story";
import type { GalleryFileListing } from "../../useGalleryListing";

const mockUseGalleryListing = vi.fn();
const mockUseGalleryActions = vi.fn();

vi.mock("@/hooks/browser/useViewportDimensions", () => ({
  __esModule: true,
  default: () => ({ isViewportVerySmall: false }),
}));

vi.mock("../../useGalleryListing", () => ({
  __esModule: true,
  useGalleryListing: (...args: unknown[]) => mockUseGalleryListing(...args),
}));

vi.mock("../../useGalleryActions", () => ({
  __esModule: true,
  rootDestination: () => ({ type: "root" }),
  folderDestination: (folder: unknown) => ({ type: "folder", folder }),
  useGalleryActions: () => mockUseGalleryActions(),
}));

vi.mock("../../useGallerySelection", () => ({
  __esModule: true,
  GallerySelection: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useGallerySelection: () => ({
    asSet: () => ({
      isEmpty: true,
      size: 0,
      only: {
        toResult: () => ({ elseThrow: () => null }),
      },
      map: () => new Set<number>(),
    }),
  }),
}));

vi.mock("@/stores/contexts/Analytics", () => ({
  __esModule: true,
  default: {
    Provider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
    Consumer: ({ children }: { children: (value: unknown) => React.ReactNode }) =>
      children({ trackEvent: () => {} }),
    _currentValue: { trackEvent: () => {} },
  },
}));

beforeEach(() => {
  const listing: GalleryFileListing = {
    parentId: 1,
    items: { totalHits: 0, results: [] },
  } as GalleryFileListing;
  mockUseGalleryListing.mockReturnValue({
    galleryListing: { tag: "success", value: listing },
    refreshListing: vi.fn(),
  });
  mockUseGalleryActions.mockReturnValue({ moveFiles: vi.fn() });
});

describe("gallery MoveDialog", () => {
  test("requests folder-only listings", () => {
    render(<MoveDialogStory />);

    expect(mockUseGalleryListing).toHaveBeenCalledWith(
      expect.objectContaining({
        foldersOnly: true,
      }),
    );
    expect(screen.getByRole("dialog")).toBeVisible();
  });
});
