import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

const AUDIT_SCRIPT = resolve(__dirname, "../../../scripts/pages/audit.js");

// Shaped like GET /audit/domains, with a domain the page does not know about.
const ENDPOINT_DOMAINS = [
  "MESSAGING",
  "RECORD",
  "FOLDER",
  "NOTEBOOK",
  "MEDIA",
  "USER",
  "GROUP",
  "COMMUNITY",
  "FORM",
  "AUDIT",
  "INV_SAMPLE",
  "INV_SUBSAMPLE",
  "INV_CONTAINER",
  "INV_INSTRUMENT",
  "REQUEST",
  "WORKSPACE",
  "UNKNOWN",
  "SOMETHING_NEW",
];

type Field = { name: string; value: unknown };
type DomainsRequest = "answered" | "pending" | "failed";

const FALLBACK_OTHER = ["COMMUNITY", "GROUP", "MESSAGING", "UNKNOWN", "USER"];

function submittedDomains(checkedAreas: Array<string>, domainsRequest: DomainsRequest = "answered") {
  const element = {
    append: () => {},
    html: () => "",
    length: 0,
    ready: () => {},
    serializeArray: (): Array<Field> => checkedAreas.map((value) => ({ name: "domains", value })),
  };
  const $ = Object.assign((selector: unknown) => (typeof selector === "function" ? undefined : element), {
    get: (url: string, onSuccess: (xhr: unknown) => void) => {
      if (url === "/audit/domains" && domainsRequest === "answered")
        onSuccess({ data: ENDPOINT_DOMAINS, errorMsg: null });
      const request = {
        fail: (onFailure: () => void) => {
          if (url === "/audit/domains" && domainsRequest === "failed") onFailure();
          return request;
        },
      };
      return request;
    },
  });
  const warn = vi.fn();
  const sandbox: Record<string, unknown> = {
    $,
    console: { log: () => {}, warn },
    document: {},
    Mustache: { render: () => "" },
    setUpAutoCompleteGroupBox: () => {},
    setUpAutoCompleteUsernameBox: () => {},
  };
  runInNewContext(readFileSync(AUDIT_SCRIPT, "utf8"), sandbox);
  (sandbox.init as () => void)();

  const requestData = (sandbox.doSerializeForm as (forDownload: boolean) => Array<Field>)(false);
  const domains = requestData.filter((field) => field.name === "domains");
  expect(domains.length).toBeLessThanOrEqual(1);
  return {
    domains: domains.length === 0 ? [] : String(domains[0].value).split(",").sort(),
    warn,
  };
}

describe("audit page activity areas", () => {
  it("sends every domain the server knows when all areas are checked", () => {
    const { domains } = submittedDomains(["ELN", "INV", "REQUEST", "OTHER"]);
    expect(domains).toEqual([...ENDPOINT_DOMAINS].sort());
  });

  it("includes instruments in the Inventory area", () => {
    const { domains } = submittedDomains(["INV"]);
    expect(domains).toEqual(["INV_CONTAINER", "INV_INSTRUMENT", "INV_SAMPLE", "INV_SUBSAMPLE"]);
  });

  it("puts unfamiliar domains under Other, but not Requests or Inventory domains", () => {
    const { domains } = submittedDomains(["OTHER"]);
    expect(domains).toContain("SOMETHING_NEW");
    expect(domains).toContain("UNKNOWN");
    expect(domains).not.toContain("REQUEST");
    expect(domains).not.toContain("INV_INSTRUMENT");
    expect(domains).not.toContain("RECORD");
  });

  it("uses the built-in Other list while the domains request is pending, still covering every known domain", () => {
    expect(submittedDomains(["OTHER"], "pending").domains).toEqual(FALLBACK_OTHER);
    const { domains, warn } = submittedDomains(["ELN", "INV", "REQUEST", "OTHER"], "pending");
    expect(domains).toEqual(ENDPOINT_DOMAINS.filter((domain) => domain !== "SOMETHING_NEW").sort());
    expect(warn).not.toHaveBeenCalled();
  });

  it("keeps the named areas and the built-in Other list when the domains request fails", () => {
    const { domains, warn } = submittedDomains(["INV", "OTHER"], "failed");
    expect(domains).toEqual(
      ["INV_SAMPLE", "INV_SUBSAMPLE", "INV_CONTAINER", "INV_INSTRUMENT", ...FALLBACK_OTHER].sort(),
    );
    expect(warn).toHaveBeenCalledOnce();
  });
});
