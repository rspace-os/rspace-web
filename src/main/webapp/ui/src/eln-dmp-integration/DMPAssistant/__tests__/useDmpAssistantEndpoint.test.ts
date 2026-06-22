import MockAdapter from "axios-mock-adapter";
import { describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import { DmpListing, DmpSummary, importDmpsIntoGallery } from "../useDmpAssistantEndpoint";

const aPlan = (identifier: string) => ({
  title: "A plan",
  dmp_id: { identifier },
  created: "2026-01-01T00:00:00Z",
  modified: "2026-01-02T00:00:00Z",
});

describe("DmpSummary", () => {
  describe("id", () => {
    test("extracts the trailing path segment from a URL identifier", () => {
      const dmp = new DmpSummary(aPlan("https://dmp-pgd.ca/api/v2/plans/19142"));
      expect(dmp.id).toBe("19142");
    });

    test("ignores a trailing slash on a URL identifier", () => {
      const dmp = new DmpSummary(aPlan("https://dmp-pgd.ca/api/v2/plans/19142/"));
      expect(dmp.id).toBe("19142");
    });

    test("returns a plain non-URL identifier unchanged", () => {
      const dmp = new DmpSummary(aPlan("19142"));
      expect(dmp.id).toBe("19142");
    });

    test("returns a DOI-style identifier containing slashes unchanged", () => {
      const dmp = new DmpSummary(aPlan("doi:10.1234/abcd.5678"));
      expect(dmp.id).toBe("doi:10.1234/abcd.5678");
    });

    test("returns a URN identifier unchanged", () => {
      const dmp = new DmpSummary(aPlan("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6"));
      expect(dmp.id).toBe("urn:uuid:f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
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
      () => {},
    );

    expect(listing.getById("19142")).toBeInstanceOf(DmpSummary);
    expect(listing.getById("no-such-id")).toBeUndefined();
  });
});

describe("importDmpsIntoGallery", () => {
  test("posts to the absolute importPlans path so SPA routes cannot derail it", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onPost("/apps/dmpassistant/importPlans").reply(200, { data: [], error: null });
    const addAlert = vi.fn();

    await importDmpsIntoGallery([new DmpSummary(aPlan("https://dmp-pgd.ca/api/v2/plans/19142"))], addAlert);

    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].url).toBe("/apps/dmpassistant/importPlans");
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    mockAxios.restore();
  });
});
