import * as v from "valibot";

export const InstrumentSchema = v.object({
  id: v.number(),
  name: v.string(),
  globalId: v.string(),
});

export const RelationshipSearchResponseSchema = v.object({ docs: v.array(v.unknown()) });
