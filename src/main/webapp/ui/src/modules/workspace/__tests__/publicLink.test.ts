import { beforeEach, describe, expect, it, vi } from "vitest";
import { getPublicLink } from "@/modules/workspace/publicLink";

beforeEach(() => {
  fetchMock.resetMocks();
  vi.clearAllMocks();
});

describe("getPublicLink", () => {
  it("requests the public link endpoint with the global id", async () => {
    fetchMock.mockResponseOnce("");

    await getPublicLink("SD123");

    expect(fetchMock).toHaveBeenCalledWith(
      "/public/publishedView/publiclink?globalId=SD123",
      expect.objectContaining({
        method: "GET",
        headers: { "X-Requested-With": "XMLHttpRequest" },
      }),
    );
  });

  it("returns the public link when the record is published", async () => {
    fetchMock.mockResponseOnce("/public/publishedView/document/abc-123");

    const result = await getPublicLink("SD123");

    expect(result).toBe("/public/publishedView/document/abc-123");
  });

  it("returns null when the endpoint returns an empty body", async () => {
    fetchMock.mockResponseOnce("");

    const result = await getPublicLink("SD123");

    expect(result).toBeNull();
  });

  it("throws when the response is not OK", async () => {
    fetchMock.mockResponseOnce("nope", { status: 500, statusText: "Server Error" });

    await expect(getPublicLink("SD123")).rejects.toThrow();
  });
});
