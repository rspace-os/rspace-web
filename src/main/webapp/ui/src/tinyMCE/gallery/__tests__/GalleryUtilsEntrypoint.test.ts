import { beforeEach, describe, expect, it, vi } from "vitest";

describe("GalleryUtilsEntrypoint", () => {
  beforeEach(() => {
    vi.resetModules();
    Reflect.deleteProperty(window, "addFromGallery");
    Reflect.deleteProperty(window, "getFieldIdFromTextFieldId");
  });

  it("exposes the legacy gallery helpers on window", async () => {
    const utilsModule = await import("@/tinyMCE/gallery/utils");
    await import("@/tinyMCE/gallery/GalleryUtilsEntrypoint");

    expect(window.addFromGallery).toBe(utilsModule.addFromGallery);
    expect(window.getFieldIdFromTextFieldId).toBe(
      utilsModule.getFieldIdFromTextFieldId,
    );
  });
});

