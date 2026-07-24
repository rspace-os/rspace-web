import * as v from "valibot";

// The standard v2 paginated-list envelope; mirrors ApiV2ListResult on the backend.
export function v2ListEnvelope<TDoc extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(docSchema: TDoc) {
  return v.object({
    docs: v.array(docSchema),
    totalDocs: v.number(),
    limit: v.number(),
    page: v.number(),
    totalPages: v.number(),
    hasPrevPage: v.boolean(),
    hasNextPage: v.boolean(),
    prevPage: v.nullable(v.number()),
    nextPage: v.nullable(v.number()),
  });
}
