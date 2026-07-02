import { beforeEach, describe, expect, it, vi } from "vitest";
import { uploadNewGalleryVersion } from "@/modules/workspace/galleryUpload";

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

function makeFile(): File {
  return new File(["new bytes"], "replacement.png", { type: "image/png" });
}

describe("uploadNewGalleryVersion", () => {
  it("posts multipart form data with the file and selectedMediaId to the upload endpoint", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: { id: 55, oid: { idString: "GL55" }, name: "replacement.png", type: "Image" },
        error: null,
        success: true,
      }),
    );

    await uploadNewGalleryVersion({ mediaId: 55, file: makeFile() });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/gallery/ajax/uploadFile");
    expect(init).toMatchObject({ method: "POST" });
    const body = (init as RequestInit).body as FormData;
    expect(body).toBeInstanceOf(FormData);
    expect(body.get("selectedMediaId")).toBe("55");
    expect(body.get("xfile")).toBeInstanceOf(File);
  });

  it("returns the updated record information on success", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: { id: 55, oid: { idString: "GL55" }, name: "replacement.png", type: "Image", version: 3 },
        error: null,
        success: true,
      }),
    );

    const result = await uploadNewGalleryVersion({ mediaId: 55, file: makeFile() });

    expect(result.oid.idString).toBe("GL55");
    expect(result.version).toBe(3);
  });

  it("throws the endpoint error message when the upload fails", async () => {
    fetchMock.mockResponseOnce(
      JSON.stringify({
        data: null,
        error: { errorMessages: ["File too large"] },
        success: false,
      }),
      { status: 400, statusText: "Bad Request" },
    );

    await expect(uploadNewGalleryVersion({ mediaId: 55, file: makeFile() })).rejects.toThrow("File too large");
  });
});
