import type * as v from "valibot";
import { expectTypeOf, it } from "vitest";
import { apiV2ReadSchemas } from "./generated/apiV2ReadSchemas";

it("maps each readable resource to an object schema, which projection can pick from", () => {
  expectTypeOf(apiV2ReadSchemas).toExtend<
    Record<keyof typeof apiV2ReadSchemas, v.ObjectSchema<v.ObjectEntries, v.ErrorMessage<v.ObjectIssue> | undefined>>
  >();
});
