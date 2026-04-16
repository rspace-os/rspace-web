import { beforeEach, describe, expect, it, vi } from "vitest";
import { waitFor } from "@testing-library/react";
import type { WorkspaceRecordInformation } from "@/modules/workspace/schema";
import { addFromGallery } from "@/tinyMCE/gallery/utils";

type MockedRS = {
  tinymceInsertInternalLink: ReturnType<typeof vi.fn>;
  insertTemplateIntoTinyMCE: ReturnType<typeof vi.fn>;
  blockPage: ReturnType<typeof vi.fn>;
  unblockPage: ReturnType<typeof vi.fn>;
  trackEvent: ReturnType<typeof vi.fn>;
  tinymceInsertContent: ReturnType<typeof vi.fn>;
};

function setGlobal(name: string, value: unknown) {
  Object.defineProperty(window, name, {
    configurable: true,
    writable: true,
    value,
  });
  Object.defineProperty(globalThis, name, {
    configurable: true,
    writable: true,
    value,
  });
}

function makeRecord(
  overrides: Partial<WorkspaceRecordInformation> = {},
): WorkspaceRecordInformation {
  return {
    id: 21,
    oid: {
      idString: "GL21",
    },
    name: "Gallery asset",
    type: "Document",
    extension: "pdf",
    thumbnailId: 301,
    widthResized: 640,
    heightResized: 480,
    modificationDate: 1712755200000,
    ...overrides,
  };
}

describe("addFromGallery", () => {
  let RS: MockedRS;
  let generateIconSrc: ReturnType<typeof vi.fn>;
  let isPlayableOnJWPlayer: ReturnType<typeof vi.fn>;
  let setUpJWMediaPlayer: ReturnType<typeof vi.fn>;
  let insertChemElement: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock.resetMocks();
    vi.clearAllMocks();

    RS = {
      tinymceInsertInternalLink: vi.fn(),
      insertTemplateIntoTinyMCE: vi.fn(),
      blockPage: vi.fn(),
      unblockPage: vi.fn(),
      trackEvent: vi.fn(),
      tinymceInsertContent: vi.fn(),
    };
    generateIconSrc = vi.fn(() => "/icons/file.svg");
    isPlayableOnJWPlayer = vi.fn(() => false);
    setUpJWMediaPlayer = vi.fn(() => "<video />");
    insertChemElement = vi.fn(() => Promise.resolve(undefined));

    setGlobal("RS", RS);
    setGlobal("tinymce", {
      activeEditor: {
        id: "rtf_11",
      },
    });
    setGlobal("generateIconSrc", generateIconSrc);
    setGlobal("isPlayableOnJWPlayer", isPlayableOnJWPlayer);
    setGlobal("setUpJWMediaPlayer", setUpJWMediaPlayer);
    setGlobal("insertChemElement", insertChemElement);
    window.chemistryAvailable = false;
    vi.spyOn(window, "alert").mockImplementation(() => {});
  });

  it("inserts folders as TinyMCE internal links", () => {
    addFromGallery(makeRecord({ type: "Folder" }));

    expect(RS.tinymceInsertInternalLink).toHaveBeenCalledWith(
      21,
      "GL21",
      "Gallery asset",
      expect.objectContaining({ id: "rtf_11" }),
    );
  });

  it("inserts generic documents using the TinyMCE template bridge", () => {
    addFromGallery(makeRecord({ type: "Document", extension: "docx" }));

    expect(generateIconSrc).toHaveBeenCalledWith("Documents", "docx", 301, 21);
    expect(RS.insertTemplateIntoTinyMCE).toHaveBeenCalledWith(
      "#insertedDocumentTemplate",
      {
        id: 21,
        name: "Gallery asset",
        iconPath: "/icons/file.svg",
      },
    );
  });

  it("posts snippet insertion with fetch and inserts returned HTML", async () => {
    fetchMock.mockResponseOnce("<p>Inserted snippet</p>");

    addFromGallery(makeRecord({ type: "Snippet" }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        "/snippet/insertIntoField",
        expect.objectContaining({
          method: "POST",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With": "XMLHttpRequest",
          },
          body: "snippetId=21&fieldId=11",
        }),
      );
      expect(RS.tinymceInsertContent).toHaveBeenCalledWith(
        "<p>Inserted snippet</p>",
      );
    });
  });

  it("alerts when snippet insertion fails", async () => {
    fetchMock.mockResponseOnce("failure", { status: 500, statusText: "Boom" });

    addFromGallery(makeRecord({ type: "Snippet" }));

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        "An error occurred while inserting the snippet 21",
      );
    });
    expect(RS.tinymceInsertContent).not.toHaveBeenCalled();
  });

  it("uses the chemistry insertion flow when chemistry is available", async () => {
    window.chemistryAvailable = true;

    addFromGallery(makeRecord({ type: "Chemistry" }));

    await waitFor(() => {
      expect(RS.blockPage).toHaveBeenCalledWith("Inserting Chemical...");
      expect(insertChemElement).toHaveBeenCalledWith(21, "11", "Gallery asset");
      expect(RS.trackEvent).toHaveBeenCalledWith(
        "user:add:chemistry_object:document",
        { from: "gallery" },
      );
      expect(RS.unblockPage).toHaveBeenCalled();
    });
  });

  it("throws for legacy filestore insertion attempts", () => {
    expect(() => addFromGallery(makeRecord({ type: "NetworkFile" }))).toThrow(
      "Legacy filestore link insertion detected, please remove",
    );
  });
});

