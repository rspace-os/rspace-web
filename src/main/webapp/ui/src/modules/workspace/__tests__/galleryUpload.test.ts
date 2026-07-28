import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { uploadNewGalleryVersion } from "@/modules/workspace/galleryUpload";

afterEach(() => {
  vi.restoreAllMocks();
});

function makeFile(): File {
  return new File(["new bytes"], "replacement.png", { type: "image/png" });
}

describe("uploadNewGalleryVersion", () => {
  // request.formData() can't be used here: undici's multipart parser fails
  // to decode a jsdom-constructed File part in this test environment, so the
  // request body is asserted as raw multipart bytes instead.
  it("posts multipart form data with the file and selectedMediaId to the upload endpoint", async () => {
    const setSpy = vi.spyOn(FormData.prototype, "set");
    let multipartBody = "";
    let contentType: string | null = null;
    server.use(
      http.post("/gallery/ajax/uploadFile", async ({ request }) => {
        multipartBody = await request.text();
        contentType = request.headers.get("Content-Type");
        return HttpResponse.json({
          data: { id: 55, oid: { idString: "GL55" }, name: "replacement.png", type: "Image" },
          error: null,
          success: true,
        });
      }),
    );

    await uploadNewGalleryVersion({ mediaId: 55, file: makeFile() });

    expect(String(contentType).startsWith("multipart/form-data; boundary=")).toBe(true);
    expect(multipartBody).toContain('name="xfile"; filename=');
    expect(multipartBody).toContain('name="selectedMediaId"\r\n\r\n55');
    expect(setSpy).toHaveBeenCalledWith("selectedMediaId", "55");
    const file = setSpy.mock.calls.find(([name]) => name === "xfile")?.[1];
    expect(file).toBeInstanceOf(File);
    expect((file as File).name).toBe("replacement.png");
  });

  it("returns the updated record information on success", async () => {
    server.use(
      http.post("/gallery/ajax/uploadFile", () =>
        HttpResponse.json({
          data: { id: 55, oid: { idString: "GL55" }, name: "replacement.png", type: "Image", version: 3 },
          error: null,
          success: true,
        }),
      ),
    );

    const result = await uploadNewGalleryVersion({ mediaId: 55, file: makeFile() });

    expect(result.oid.idString).toBe("GL55");
    expect(result.version).toBe(3);
  });

  it("throws the endpoint error message when the upload fails", async () => {
    server.use(
      http.post("/gallery/ajax/uploadFile", () =>
        HttpResponse.json(
          {
            data: null,
            error: { errorMessages: ["File too large"] },
            success: false,
          },
          { status: 400, statusText: "Bad Request" },
        ),
      ),
    );

    await expect(uploadNewGalleryVersion({ mediaId: 55, file: makeFile() })).rejects.toThrow("File too large");
  });
});
