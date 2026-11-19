import { describe, expect, expectTypeOf, it } from "vitest";
import { ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import { type ApiV2ReadDocument, type ApiV2Selected, apiV2Fields, assertApiV2Selected } from "./apiV2Selected";
import type { InstrumentsRead } from "./generated/models/instrumentsRead";

const FIELDS = ["id", "name", "parentContainer", "customFields"] as const;

describe("ApiV2Selected", () => {
  it("keeps requested fields optional unless the resource declares them read-guaranteed", () => {
    type Selected = ApiV2Selected<"instruments", typeof FIELDS>;
    expectTypeOf<Selected["id"]>().toEqualTypeOf<number>();
    expectTypeOf<Pick<Selected, "name" | "parentContainer" | "customFields">>().toEqualTypeOf<
      Pick<InstrumentsRead, "name" | "parentContainer" | "customFields">
    >();
  });

  it("leaves the plain read document for a selection that is not a literal tuple", () => {
    expectTypeOf<ApiV2Selected<"instruments", ("id" | "name")[]>>().toEqualTypeOf<ApiV2ReadDocument<"instruments">>();
    expectTypeOf<ApiV2Selected<"instruments", string[]>>().toEqualTypeOf<InstrumentsRead>();
    expectTypeOf<ApiV2Selected<"instruments", readonly ["id" | "name"]>>().toEqualTypeOf<InstrumentsRead>();
  });
});

describe("assertApiV2Selected", () => {
  it("accepts a document without a requested field that is not guaranteed", () => {
    const document: InstrumentsRead = { id: 1 };
    expect(() => assertApiV2Selected("instruments", document, FIELDS)).not.toThrow();
  });

  it("rejects a missing guaranteed field as an invalid body", () => {
    const document = JSON.parse('{"name":"Microscope"}') as InstrumentsRead;
    const error = (() => {
      try {
        assertApiV2Selected("instruments", document, FIELDS);
      } catch (caught) {
        return caught;
      }
    })();
    expect(error).toBeInstanceOf(ApiV2UnexpectedResponseError);
    expect(error).toMatchObject({ reason: "invalidBody", message: expect.stringContaining("id") });
  });

  it("builds the fields query value from the tuple", () => {
    expect(apiV2Fields(FIELDS)).toBe("id,name,parentContainer,customFields");
  });
});
