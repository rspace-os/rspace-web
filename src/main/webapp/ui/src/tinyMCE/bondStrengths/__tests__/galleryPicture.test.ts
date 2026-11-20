import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import axios from "@/common/axios";
import { galleryPictureHtml } from "../galleryPicture";

const template =
  '<img id="{{fieldId}}-{{itemId}}" src="/thumbnail/data?sourceId={{itemId}}&time={{milliseconds}}" alt="image {{name}}" width="{{width}}" height="{{height}}">';

/** Fills `{{name}}` placeholders, which is all the real Mustache does for this template. */
function stubMustache() {
  Object.assign(globalThis, {
    Mustache: {
      render: (text: string, data: Record<string, unknown>) =>
        text.replaceAll(/\{\{(\w+)\}\}/g, (_, key: string) => String(data[key])),
    },
  });
}

function respondWithUpload(data: unknown) {
  server.use(
    http.post("/gallery/ajax/uploadFile/", () => HttpResponse.json({ data })),
    http.get("/fieldTemplates/ajax/insertedImageTemplate", () => HttpResponse.text(template)),
  );
}

describe("galleryPictureHtml", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    delete (globalThis as { Mustache?: unknown }).Mustache;
  });

  it("uploads the picture to the field's Gallery and returns the image markup at structure height", async () => {
    stubMustache();
    const post = vi.spyOn(axios, "post");
    respondWithUpload({
      id: 7,
      name: "bond-strengths-42.png",
      widthResized: 960,
      heightResized: 720,
      modificationDate: 1234,
    });

    const html = await galleryPictureHtml("data:image/png;base64,AAAA", "12", "bond-strengths-42.png");

    const form = post.mock.calls[0][1] as FormData;
    expect(form.get("fieldId")).toBe("12");
    expect((form.get("xfile") as File).name).toBe("bond-strengths-42.png");
    expect(html).toBe(
      '<img id="12-7" src="/thumbnail/data?sourceId=7&time=1234" alt="image bond-strengths-42.png" width="333" height="250">',
    );
  });

  it("does not enlarge a picture that is stored smaller than a structure", async () => {
    stubMustache();
    respondWithUpload({ id: 7, name: "small.png", widthResized: 200, heightResized: 100, modificationDate: 1234 });

    const html = await galleryPictureHtml("data:image/png;base64,AAAA", "12", "small.png");

    expect(html).toContain('width="200" height="100"');
  });

  it("fails when the Gallery stores nothing", async () => {
    stubMustache();
    respondWithUpload(null);

    await expect(galleryPictureHtml("data:image/png;base64,AAAA", "12", "x.png")).rejects.toThrow(
      "The picture could not be stored in the Gallery",
    );
  });
});
