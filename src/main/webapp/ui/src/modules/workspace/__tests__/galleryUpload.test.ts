import { HttpResponse, http } from "msw/http";
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
  // The body is asserted as the FormData handed to fetch: Node's fetch cannot
  // serialise a jsdom File (content-length mismatch), so the handler must not
  // read the request body in this environment.
  it("posts multipart form data with the file and selectedMediaId to the upload endpoint", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    let contentType: string | null = null;
    server.use(
      http.post("/gallery/ajax/uploadFile", ({ request }) => {
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
    const body = fetchSpy.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("selectedMediaId")).toBe("55");
    const file = (body as FormData).get("xfile");
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
