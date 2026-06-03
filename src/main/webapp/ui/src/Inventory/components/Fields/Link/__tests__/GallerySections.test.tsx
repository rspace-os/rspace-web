import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";
import { type WorkspaceRecordInformation } from "@/modules/workspace/schema";

const getLinkedDocuments = vi.fn();
const uploadNewGalleryVersion = vi.fn();

vi.mock("@/modules/workspace/linkedRecords", () => ({
  getLinkedDocuments: (...args: Array<unknown>) =>
    getLinkedDocuments(...args) as unknown,
}));
vi.mock("@/modules/workspace/galleryUpload", () => ({
  uploadNewGalleryVersion: (...args: Array<unknown>) =>
    uploadNewGalleryVersion(...args) as unknown,
}));

import GallerySections from "../GallerySections";

const imageInfo: WorkspaceRecordInformation = {
  id: 21,
  oid: { idString: "GL21" },
  name: "Microscope.png",
  type: "Image",
  ownerFullName: "Ada Lovelace",
  ownerUsername: "ada",
  creationDateWithClientTimezoneOffset: "2026-05-01 10:00 +0000",
  modificationDateWithClientTimezoneOffset: "2026-05-02 10:00 +0000",
  version: 2,
  size: 1024,
  extension: "png",
  thumbnailId: 301,
  status: "VIEW_MODE",
  description: "A microscope image",
};

beforeEach(() => {
  getLinkedDocuments.mockReset();
  uploadNewGalleryVersion.mockReset();
  getLinkedDocuments.mockResolvedValue({ readable: [], privateByOwner: [] });
  uploadNewGalleryVersion.mockResolvedValue(imageInfo);
});

afterEach(cleanup);

function renderGallery(
  props: Partial<React.ComponentProps<typeof GallerySections>> = {},
) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <GallerySections info={imageInfo} onRecordChanged={vi.fn()} {...props} />
    </ThemeProvider>,
  );
}

describe("GallerySections", () => {
  it("renders the gallery metadata rows", () => {
    renderGallery();
    expect(screen.getByText("Microscope.png")).toBeInTheDocument();
    expect(screen.getByText("Ada Lovelace")).toBeInTheDocument();
    // file size formatted
    expect(screen.getByText(/1\.02 kB|1024 B|1 kB/i)).toBeInTheDocument();
    // extension shown as its own row value (exact match, not the filename)
    expect(screen.getByText(/^png$/i)).toBeInTheDocument();
  });

  it("renders an image thumbnail preview for Image targets", () => {
    renderGallery();
    const img = screen.getByRole("img", { name: /microscope\.png|preview/i });
    expect(img).toHaveAttribute(
      "src",
      expect.stringContaining("/gallery/getThumbnail/21/"),
    );
  });

  it("links Download to the Streamfile endpoint for the media id", () => {
    renderGallery();
    const download = screen.getByRole("link", { name: /download/i });
    expect(download).toHaveAttribute("href", "/Streamfile/21");
  });

  it("lazily shows linked documents when 'Show linked docs' is clicked", async () => {
    getLinkedDocuments.mockResolvedValue({
      readable: [{ globalId: "SD7", name: "Linked doc", ownerFullName: "Ada" }],
      privateByOwner: [],
    });
    const user = userEvent.setup();
    renderGallery();

    await user.click(screen.getByRole("button", { name: /show linked docs/i }));

    expect(getLinkedDocuments).toHaveBeenCalledWith(21);
    expect(await screen.findByRole("link", { name: /SD7/ })).toHaveAttribute(
      "href",
      "/globalId/SD7",
    );
  });

  it("shows the upload-new-version control when the file is editable (VIEW_MODE)", () => {
    renderGallery();
    expect(
      screen.getByRole("button", { name: /upload new version/i }),
    ).toBeInTheDocument();
  });

  it("hides the upload-new-version control for a revision (historical) view", () => {
    renderGallery({ info: { ...imageInfo, revision: 5 } });
    expect(
      screen.queryByRole("button", { name: /upload new version/i }),
    ).not.toBeInTheDocument();
  });

  it("uploads the picked file and notifies the parent on success", async () => {
    const onRecordChanged = vi.fn();
    uploadNewGalleryVersion.mockResolvedValue({ ...imageInfo, version: 3 });
    const user = userEvent.setup();
    renderGallery({ onRecordChanged });

    const file = new File(["new bytes"], "Microscope.png", {
      type: "image/png",
    });
    // The visible button proxies to a hidden file input.
    const input = screen.getByTestId("gallery-upload-input");
    await user.upload(input, file);

    expect(uploadNewGalleryVersion).toHaveBeenCalledWith({
      mediaId: 21,
      file,
    });
    await vi.waitFor(() => {
      expect(onRecordChanged).toHaveBeenCalledTimes(1);
    });
  });
});
