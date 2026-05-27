import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen } from "@/__tests__/customQueries";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { ThemeProvider } from "@mui/material/styles";
import MoveDialog from "../MoveDialog";
import { LandmarksProvider } from "@/components/LandmarksContext";

const mockUseGalleryListing = vi.fn();
const mockUseGalleryActions = vi.fn();

vi.mock("@/hooks/browser/useViewportDimensions", () => ({
  __esModule: true,
  default: () => ({ isViewportVerySmall: false }),
}));

vi.mock("../TreeView", () => ({
  __esModule: true,
  default: () => <div>Mocked tree view</div>,
}));

vi.mock("../../useGalleryListing", () => ({
  __esModule: true,
  // eslint-disable-next-line @typescript-eslint/no-unsafe-return
  useGalleryListing: (args: unknown) => mockUseGalleryListing(args),
}));

vi.mock("../../useGalleryActions", () => ({
  __esModule: true,
  rootDestination: () => ({ type: "root" }),
  folderDestination: (folder: unknown) => ({ type: "folder", folder }),
  // eslint-disable-next-line @typescript-eslint/no-unsafe-return
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

vi.mock("@/stores/contexts/Analytics", async () => {
  const react = await import("react");
  return {
    __esModule: true,
    default: react.createContext({
      trackEvent: () => {},
    }),
  };
});

function MoveDialogStory() {
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <LandmarksProvider>
        <MoveDialog
          open={true}
          onClose={() => {}}
          section="Images"
          refreshListing={() => Promise.resolve()}
        />
      </LandmarksProvider>
    </ThemeProvider>
  );
}

beforeEach(() => {
  mockUseGalleryListing.mockReturnValue({
    galleryListing: {
      tag: "success",
      value: {
        parentId: 1,
        items: { totalHits: 0, results: [] },
      },
    },
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
