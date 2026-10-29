import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { DmpSummary } from "../useDmpOnlineEndpoint";

const aPlan = {
  title: "A plan",
  dmp_id: { identifier: "https://doi.org/10.1234/abcd" },
  created: "2026-01-01T00:00:00Z",
  modified: "2026-01-02T00:00:00Z",
};

describe("DmpSummary.importIntoGallery", () => {
  test("reports an error returned in the response body instead of success", async () => {
    server.use(
      http.post("*/apps/dmponline/importPlan", () =>
        HttpResponse.json({ data: null, error: { errorMessages: ["Not a plan"] } }),
      ),
    );
    const addAlert = vi.fn();

    await new DmpSummary(aPlan, addAlert).importIntoGallery();

    expect(addAlert).toHaveBeenCalledTimes(1);
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "error", message: "Not a plan" }));
  });

  test("reports success when the response has no error", async () => {
    server.use(http.post("*/apps/dmponline/importPlan", () => HttpResponse.json({ data: {}, error: null })));
    const addAlert = vi.fn();

    await new DmpSummary(aPlan, addAlert).importIntoGallery();

    expect(addAlert).toHaveBeenCalledTimes(1);
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
  });

  test("sends a title with URL characters as the whole filename", async () => {
    let filename: string | null = null;
    server.use(
      http.post("*/apps/dmponline/importPlan", ({ request }) => {
        filename = new URL(request.url).searchParams.get("filename");
        return HttpResponse.json({ data: {}, error: null });
      }),
    );

    await new DmpSummary({ ...aPlan, title: "R&D #1 100%" }, vi.fn()).importIntoGallery();

    expect(filename).toBe("R&D #1 100%");
  });
});
