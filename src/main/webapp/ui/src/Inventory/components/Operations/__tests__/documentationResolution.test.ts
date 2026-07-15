import { describe, expect, it } from "vitest";
import { docDefaultsAfterPerform, normalizeDocumentation } from "../documentationResolution";

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

describe("docDefaultsAfterPerform", () => {
  it("stores the chosen document under the key when remember is on", () => {
    expect(docDefaultsAfterPerform({}, "derive dna", doc, true)).toEqual({ "derive dna": doc });
  });

  it("forgets any previous document for the key when remember is off", () => {
    expect(docDefaultsAfterPerform({ "derive dna": doc, cryopreserve: doc }, "derive dna", doc, false)).toEqual({
      cryopreserve: doc,
    });
  });

  it("forgets the key when remember is on but no document is chosen", () => {
    expect(docDefaultsAfterPerform({ "derive dna": doc }, "derive dna", null, true)).toEqual({});
  });

  it("does not mutate the input map", () => {
    const current = { "derive dna": doc };
    docDefaultsAfterPerform(current, "derive dna", null, false);
    expect(current).toEqual({ "derive dna": doc });
  });
});
