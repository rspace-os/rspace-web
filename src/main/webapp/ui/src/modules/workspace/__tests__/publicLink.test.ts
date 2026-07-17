import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getPublicLink } from "@/modules/workspace/publicLink";

describe("getPublicLink", () => {
  it("requests the public link endpoint with the global id", async () => {
    const requests: Request[] = [];
    server.use(
      http.get("/public/publishedView/publiclink", ({ request }) => {
        requests.push(request);
        return new HttpResponse(null);
      }),
    );

    await getPublicLink("SD123");

    expect(requests).toHaveLength(1);
    expect(new URL(requests[0].url).searchParams.get("globalId")).toBe("SD123");
    expect(requests[0].headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  });

  it("returns the public link when the record is published", async () => {
    server.use(
      http.get("/public/publishedView/publiclink", () => new HttpResponse("/public/publishedView/document/abc-123")),
    );

    const result = await getPublicLink("SD123");

    expect(result).toBe("/public/publishedView/document/abc-123");
  });

  it("returns null when the endpoint returns an empty body", async () => {
    server.use(http.get("/public/publishedView/publiclink", () => new HttpResponse(null)));

    const result = await getPublicLink("SD123");

    expect(result).toBeNull();
  });

  it("throws when the response is not OK", async () => {
    server.use(
      http.get(
        "/public/publishedView/publiclink",
        () => new HttpResponse("nope", { status: 500, statusText: "Server Error" }),
      ),
    );

    await expect(getPublicLink("SD123")).rejects.toThrow();
  });
});
