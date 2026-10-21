import type { Attributes, Span } from "@opentelemetry/api";
import { beforeEach, describe, expect, it } from "vitest";
import { currentPageKey } from "../../util/pageBranding";
import {
  MALFORMED_URL_SENTINEL,
  sanitizeSpanUrlAttributes,
  sanitizeUrl,
  setUiPage,
  webVitalAttributes,
} from "../otelImpl";

const MARKER = "sensitive-marker";

function spanWith(attributes: Attributes): Span {
  return {
    attributes,
    setAttribute(key: string, value: NonNullable<Attributes[string]>) {
      attributes[key] = value;
      return this;
    },
  } as unknown as Span;
}

describe("browser telemetry URL sanitization", () => {
  it.each([
    ["same-origin absolute", `${window.location.origin}/records?token=${MARKER}`, `${window.location.origin}/records`],
    ["cross-origin absolute", `https://example.com/records?token=${MARKER}`, "https://example.com/records"],
    ["relative", `/records?token=${MARKER}`, `${window.location.origin}/records`],
    ["query", `/records?token=${MARKER}`, `${window.location.origin}/records`],
    ["fragment", `/records#${MARKER}`, `${window.location.origin}/records`],
    ["URL credentials", `https://${MARKER}:${MARKER}@example.com/records`, "https://example.com/records"],
    ["malformed", `http://[${MARKER}`, MALFORMED_URL_SENTINEL],
  ])("sanitizes %s URLs", (_case, rawUrl, expected) => {
    const sanitized = sanitizeUrl(rawUrl);

    expect(sanitized).toBe(expected);
    expect(sanitized).not.toContain(MARKER);
  });

  it.each(["url.full", "http.url"])("overwrites the %s span attribute", (attribute) => {
    const attributes = { [attribute]: `https://${MARKER}:${MARKER}@example.com/path?token=${MARKER}#${MARKER}` };

    sanitizeSpanUrlAttributes(spanWith(attributes));

    expect(attributes).toEqual({ [attribute]: "https://example.com/path" });
    expect(JSON.stringify(attributes)).not.toContain(MARKER);
  });

  it("overwrites both URL conventions when both are present", () => {
    const attributes = {
      "url.full": `/path?token=${MARKER}`,
      "http.url": `/path?token=${MARKER}`,
    };

    sanitizeSpanUrlAttributes(spanWith(attributes));

    expect(attributes).toEqual({
      "url.full": `${window.location.origin}/path`,
      "http.url": `${window.location.origin}/path`,
    });
    expect(JSON.stringify(attributes)).not.toContain(MARKER);
  });
});

describe("web vital labels", () => {
  beforeEach(() => setUiPage("apps"));

  it.each(["private-marker", "constructor", "__proto__"])("bounds unknown app-bar paths: %s", (path) => {
    window.history.replaceState({}, "", `/${path}`);
    setUiPage(currentPageKey());
    expect(webVitalAttributes()).toEqual({ "rspace.ui.page": "unknown" });
  });

  it("uses the bounded UI trace page instead of the browser path", () => {
    window.history.replaceState({}, "", `/records?token=${MARKER}`);

    expect(webVitalAttributes()).toEqual({ "rspace.ui.page": "apps" });
    expect(JSON.stringify(webVitalAttributes())).not.toContain(MARKER);
  });
});
