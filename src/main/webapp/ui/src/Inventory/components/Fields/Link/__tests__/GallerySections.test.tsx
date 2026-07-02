import { ThemeProvider } from "@mui/material/styles";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@/__tests__/customQueries";
import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";
import materialTheme from "../../../../../theme";

const getLinkedDocuments = vi.fn();
const uploadNewGalleryVersion = vi.fn();
const useReferencingInventoryItems = vi.fn();

vi.mock("@/modules/workspace/linkedRecords", () => ({
  getLinkedDocuments: (...args: Array<unknown>) => getLinkedDocuments(...args) as unknown,
}));
vi.mock("@/modules/workspace/galleryUpload", () => ({
  uploadNewGalleryVersion: (...args: Array<unknown>) => uploadNewGalleryVersion(...args) as unknown,
}));
vi.mock("@/eln/gallery/useReferencingInventoryItems", () => ({
  default: (...args: Array<unknown>) => useReferencingInventoryItems(...args) as unknown,
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
  useReferencingInventoryItems.mockReset();
  getLinkedDocuments.mockResolvedValue({ readable: [], privateByOwner: [] });
  uploadNewGalleryVersion.mockResolvedValue(imageInfo);
  useReferencingInventoryItems.mockReturnValue({
    items: [],
    loading: false,
    errorMessage: null,
  });
});

afterEach(cleanup);

function renderGallery(props: Partial<React.ComponentProps<typeof GallerySections>> = {}) {
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
    expect(img).toHaveAttribute("src", expect.stringContaining("/gallery/getThumbnail/21/"));
  });

  it("links Download to the Streamfile endpoint for the media id", () => {
    renderGallery();
    const download = screen.getByRole("link", { name: /fields\.link\.download/i });
    expect(download).toHaveAttribute("href", "/Streamfile/21");
  });

  it("lazily shows linked documents when 'Show linked docs' is clicked", async () => {
    getLinkedDocuments.mockResolvedValue({
      readable: [{ globalId: "SD7", name: "Linked doc", ownerFullName: "Ada" }],
      privateByOwner: [],
    });
    const user = userEvent.setup();
    renderGallery();

    await user.click(screen.getByRole("button", { name: /fields\.link\.gallerySections\.showLinkedDocs/i }));

    expect(getLinkedDocuments).toHaveBeenCalledWith(21);
    expect(await screen.findByRole("link", { name: /SD7/ })).toHaveAttribute("href", "/globalId/SD7");
  });

  it("hides the related inventory items until 'Show linked docs' is clicked", () => {
    renderGallery();
    expect(screen.queryByText("inventory:fields.link.relatedInventoryItems.title")).not.toBeInTheDocument();
  });

  it("shows the Inventory items that link to this file on 'Show linked docs'", async () => {
    // the Gallery's own info panel shows these back-references; this dialog must
    // too, since the ELN linked-docs lookup does not cover inventory links
    useReferencingInventoryItems.mockReturnValue({
      items: [
        {
          globalId: "SA42",
          name: "My sample",
          type: "SAMPLE",
          relationType: "References",
          permalinkHref: "/globalId/SA42",
          linkableRecord: {},
        },
      ],
      loading: false,
      errorMessage: null,
    });
    const user = userEvent.setup();
    renderGallery();

    await user.click(screen.getByRole("button", { name: /fields\.link\.gallerySections\.showLinkedDocs/i }));

    expect(useReferencingInventoryItems).toHaveBeenCalledWith("GL21");
    expect(await screen.findByText("inventory:fields.link.relatedInventoryItems.title")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /SA42/ })).toHaveAttribute("href", "/globalId/SA42");
    expect(screen.getByText(/\(References\)/)).toBeInTheDocument();
  });

  it("shows an empty message when no Inventory items link to this file", async () => {
    const user = userEvent.setup();
    renderGallery();

    await user.click(screen.getByRole("button", { name: /fields\.link\.gallerySections\.showLinkedDocs/i }));

    expect(await screen.findByText("inventory:fields.link.relatedInventoryItems.none")).toBeInTheDocument();
  });

  it("shows the upload-new-version control when the file is editable (VIEW_MODE)", () => {
    renderGallery();
    expect(
      screen.getByRole("button", { name: /inventory:fields\.link\.gallerySections\.uploadNewVersion/i }),
    ).toBeInTheDocument();
  });

  it("hides the upload-new-version control for a revision (historical) view", () => {
    renderGallery({ info: { ...imageInfo, revision: 5 } });
    expect(
      screen.queryByRole("button", { name: /inventory:fields\.link\.gallerySections\.uploadNewVersion/i }),
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
