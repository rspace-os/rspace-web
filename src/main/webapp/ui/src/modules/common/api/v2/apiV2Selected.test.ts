import { describe, expect, expectTypeOf, it } from "vitest";
import { ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import { type ApiV2ReadDocument, type ApiV2Selected, apiV2Fields, assertApiV2Selected } from "./apiV2Selected";
import type { InstrumentsRead } from "./generated/models/instrumentsRead";
import type { InstrumentsReadParentContainer } from "./generated/models/instrumentsReadParentContainer";

const FIELDS = ["id", "name", "parentContainer", "customFields"] as const;

describe("ApiV2Selected", () => {
  it("makes requested guaranteed fields present and keeps the rest optional", () => {
    type Selected = ApiV2Selected<"instruments", typeof FIELDS>;
    expectTypeOf<Selected["name"]>().toEqualTypeOf<string>();
    expectTypeOf<Selected["parentContainer"]>().toEqualTypeOf<InstrumentsReadParentContainer>();
    expectTypeOf<Selected>().toHaveProperty("customFields");
    expectTypeOf<Pick<Selected, "customFields" | "globalId">>().toEqualTypeOf<
      Pick<InstrumentsRead, "customFields" | "globalId">
    >();
  });

  it("keeps null where the schema allows it", () => {
    type Selected = ApiV2Selected<"users", readonly ["createdAt"]>;
    expectTypeOf<Selected["createdAt"]>().toEqualTypeOf<string | null>();
  });

  it("leaves the plain read document for a selection that is not a literal tuple", () => {
    expectTypeOf<ApiV2Selected<"instruments", ("id" | "name")[]>>().toEqualTypeOf<ApiV2ReadDocument<"instruments">>();
    expectTypeOf<ApiV2Selected<"instruments", string[]>>().toEqualTypeOf<InstrumentsRead>();
    expectTypeOf<ApiV2Selected<"instruments", readonly ["id" | "name"]>>().toEqualTypeOf<InstrumentsRead>();
  });
});

describe("assertApiV2Selected", () => {
  it("narrows a document with every requested guaranteed field", () => {
    const document: InstrumentsRead = { id: 1, name: "Microscope", parentContainer: null };
    assertApiV2Selected("instruments", document, FIELDS);
    expectTypeOf(document.name).toEqualTypeOf<string>();
    expect(document.name).toBe("Microscope");
  });

  it("rejects a missing guaranteed field as an invalid body", () => {
    const document: InstrumentsRead = { id: 1, parentContainer: null };
    const error = (() => {
      try {
        assertApiV2Selected("instruments", document, FIELDS);
      } catch (caught) {
        return caught;
      }
    })();
    expect(error).toBeInstanceOf(ApiV2UnexpectedResponseError);
    expect(error).toMatchObject({ reason: "invalidBody", message: expect.stringContaining("name") });
  });

  it("accepts a missing field the resource does not guarantee, and null", () => {
    const document: InstrumentsRead = { id: 1, name: "Microscope", parentContainer: null };
    expect(() => assertApiV2Selected("instruments", document, FIELDS)).not.toThrow();
  });

  it("builds the fields query value from the tuple", () => {
    expect(apiV2Fields(FIELDS)).toBe("id,name,parentContainer,customFields");
  });
});
