import { test, describe, expect, vi } from "vitest";
import {
  DmpSummary,
  DmpListing,
  importDmpsIntoGallery,
} from "../useDmpAssistantEndpoint";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";

const aPlan = (identifier: string) => ({
  title: "A plan",
  dmp_id: { identifier },
  created: "2026-01-01T00:00:00Z",
  modified: "2026-01-02T00:00:00Z",
});

describe("DmpSummary", () => {
  describe("id", () => {
    test("extracts the trailing path segment from a URL identifier", () => {
      const dmp = new DmpSummary(
        aPlan("https://dmp-pgd.ca/api/v2/plans/19142")
      );
      expect(dmp.id).toBe("19142");
    });

    test("ignores a trailing slash on a URL identifier", () => {
      const dmp = new DmpSummary(
        aPlan("https://dmp-pgd.ca/api/v2/plans/19142/")
      );
      expect(dmp.id).toBe("19142");
    });

    test("returns a plain non-URL identifier unchanged", () => {
      const dmp = new DmpSummary(aPlan("19142"));
      expect(dmp.id).toBe("19142");
    });
  });
});

describe("DmpListing", () => {
  test("getById returns undefined for an unknown or stale id", () => {
    const listing = new DmpListing(
      {
        items: [{ dmp: aPlan("https://dmp-pgd.ca/api/v2/plans/19142") }],
        total_items: 1,
      },
      0,
      20,
      () => {}
    );

    expect(listing.getById("19142")).toBeInstanceOf(DmpSummary);
    expect(listing.getById("no-such-id")).toBeUndefined();
  });
});

describe("importDmpsIntoGallery", () => {
  test("posts to the absolute importPlans path so SPA routes cannot derail it", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios
      .onPost("/apps/dmpassistant/importPlans")
      .reply(200, { data: [], error: null });
    const addAlert = vi.fn();

    await importDmpsIntoGallery(
      [new DmpSummary(aPlan("https://dmp-pgd.ca/api/v2/plans/19142"))],
      addAlert
    );

    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].url).toBe(
      "/apps/dmpassistant/importPlans"
    );
    expect(addAlert).toHaveBeenCalledWith(
      expect.objectContaining({ variant: "success" })
    );
    mockAxios.restore();
  });
});
