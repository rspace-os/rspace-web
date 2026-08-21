import { describe, expect, it } from "vitest";
import { normalizeDocumentation } from "../documentationResolution";

const doc = { globalId: "SD1", name: "My SOP" };

describe("normalizeDocumentation", () => {
  it("passes through a well-formed document", () => {
    expect(normalizeDocumentation(doc)).toEqual(doc);
  });

  it("rejects malformed / missing stored values", () => {
    expect(normalizeDocumentation(undefined)).toBeNull();
    expect(normalizeDocumentation(null)).toBeNull();
    expect(normalizeDocumentation("SD1")).toBeNull();
    expect(normalizeDocumentation({ globalId: "SD1" })).toBeNull();
    expect(normalizeDocumentation({ name: "My SOP" })).toBeNull();
  });
});
