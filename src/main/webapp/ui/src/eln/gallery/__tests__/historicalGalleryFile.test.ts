import { describe, expect, test } from "vitest";
import { galleryFile } from "../components/VersionHistoryDialog.story";
import { HistoricalGalleryFile } from "../historicalGalleryFile";
import { Description, type GalleryFile } from "../useGalleryListing";

const pinned = (
  overrides: Partial<GalleryFile> = {},
  name: string | null = "first-draft.txt",
  description: string | null = "a rough cut",
) =>
  new HistoricalGalleryFile({
    file: galleryFile({
      version: 3,
      size: 2048,
      name: "final.txt",
      description: Description.Present("the final take"),
      ...overrides,
    }),
    version: 1,
    size: 918,
    modificationDate: new Date("2026-06-11T09:30:00Z"),
    name,
    description,
  });

describe("HistoricalGalleryFile", () => {
  test("reports the version it is showing, not the live one", () => {
    const file = pinned();

    expect(file.version).toBe(1);
    expect(file.pinnedVersion).toBe(1);
  });

  test("reports that version's size and modification date", () => {
    // without these the panel would describe version 1 using version 3's numbers
    const file = pinned();

    expect(file.size).toBe(918);
    expect(file.modificationDate?.toISOString()).toBe("2026-06-11T09:30:00.000Z");
  });

  test("downloads that version's bytes", async () => {
    const file = pinned();

    await expect(file.downloadHref()).resolves.toBe("/Streamfile/42?version=1");
  });

  test("refuses every mutating action", () => {
    const file = pinned();

    for (const predicate of [
      file.canDelete,
      file.canRename,
      file.canDuplicate,
      file.canBeMoved,
      file.canUploadNewVersion,
      file.canMoveToIrods,
      file.canMoveToS3,
      file.canBeExported,
    ]) {
      expect(predicate.isError).toBe(true);
    }
  });

  test("permits the version history, so a pinned view is not a dead end", () => {
    expect(pinned().canViewVersionHistory.isOk).toBe(true);
  });

  test("gives one reason for every refusal, so the menu reads consistently", () => {
    const file = pinned();

    expect(file.canDelete.orElseGet(([e]) => e.message)).toBe("A past version cannot be edited.");
    expect(file.canRename.orElseGet(([e]) => e.message)).toBe("A past version cannot be edited.");
  });

  test("keeps the global id unversioned, so backlink lookups are unaffected", () => {
    // ELN and inventory reference lookups read this; neither records a version
    expect(pinned().globalId).toBe("GL42");
  });

  test("delegates the id and type, which do not vary between versions", () => {
    const file = pinned({ isImage: false });

    expect(file.id).toBe(42);
    expect(file.key).toBe("GL42");
    expect(file.isFolder).toBe(false);
  });

  test("reports that version's filename, not the live one", () => {
    // uploading a new version can replace the file with a differently named one
    const file = pinned({ name: "final.txt" }, "first-draft.txt");

    expect(file.name).toBe("first-draft.txt");
  });

  test("takes its extension from that version's name", () => {
    // the extension decides the icon and which previewers apply
    const file = pinned({ name: "final.png", extension: "png" }, "first-draft.tiff");

    expect(file.extension).toBe("tiff");
  });

  test("an extensionless version name yields no extension, rather than being its own", () => {
    expect(pinned({}, "README").extension).toBeNull();
  });

  test("transforms that version's filename, keeping its extension", () => {
    const file = pinned({ name: "final.txt" }, "first-draft.tiff");

    expect(file.transformFilename((n) => `${n}_copy`)).toBe("first-draft_copy.tiff");
  });

  test("falls back to the live name when the audit row carried none", () => {
    const file = pinned({ name: "final.txt" }, null);

    expect(file.name).toBe("final.txt");
  });

  test("reports that version's description, not the live one", () => {
    const file = pinned({}, "first-draft.txt", "a rough cut");

    expect(file.description.toString().orElse(null)).toBe("a rough cut");
  });

  test("a version with no recorded description shows none, never the live one", () => {
    /*
     * Empty rather than missing: an empty description renders as a blank field,
     * whereas a missing one hides the row, and a local item always has the field.
     */
    const file = pinned({ description: Description.Present("the final take") }, "first-draft.txt", null);

    expect(file.description.toString().orElse(null)).toBe("");
  });

  test("an image thumbnails from its own version's bytes, not the live thumbnail", () => {
    /*
     * /gallery/getThumbnail always serves the live image, so delegating showed
     * the newest picture beside an older version's metadata.
     */
    const file = pinned({ isImage: true, name: "final.png" }, "first-draft.png");

    expect(file.thumbnailUrl).toBe("/Streamfile/42?version=1");
  });

  test("a non-image falls back to its type icon rather than a live content thumbnail", () => {
    // no thumbnail endpoint for documents or chemistry is version-aware
    const file = pinned({ isImage: false }, "first-draft.txt");

    expect(file.thumbnailUrl).not.toMatch(/getThumbnail|docThumbnail|getChemThumbnail/);
  });
});
