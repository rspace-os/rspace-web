import * as v from "valibot";

// The standard v2 paginated-list envelope; mirrors ApiV2ListResult on the backend.
export function v2ListEnvelope<TDoc extends v.BaseSchema<unknown, unknown, v.BaseIssue<unknown>>>(docSchema: TDoc) {
  return v.object({
    docs: v.array(docSchema),
    totalDocs: v.pipe(v.number(), v.integer(), v.minValue(0)),
    limit: v.pipe(v.number(), v.integer(), v.minValue(1)),
    page: v.pipe(v.number(), v.integer(), v.minValue(1)),
    pagingCounter: v.pipe(v.number(), v.integer(), v.minValue(1)),
    totalPages: v.pipe(v.number(), v.integer(), v.minValue(0)),
    hasPrevPage: v.boolean(),
    hasNextPage: v.boolean(),
    prevPage: v.nullable(v.pipe(v.number(), v.integer(), v.minValue(1))),
    nextPage: v.nullable(v.pipe(v.number(), v.integer(), v.minValue(1))),
  });
}
