import * as v from "valibot";
import { expect, it } from "vitest";
import { expandedValueKeys } from "../schema";

const reference = v.object({ relationTo: v.literal("instruments"), value: v.number() });
const expanded = v.object({
  relationTo: v.literal("instruments"),
  value: v.object({ id: v.number(), name: v.string() }),
});

it.each([
  [
    "an optional, nullable union of a reference and an expanded document",
    v.optional(v.union([reference, expanded, v.null()])),
    ["id", "name"],
  ],
  [
    "an intersection whose members add keys to the expanded document",
    v.intersect([expanded, v.object({ value: v.object({ globalId: v.string() }) })]),
    ["globalId", "id", "name"],
  ],
  ["a reference whose value is not a document", reference, []],
  [
    "a reference intersected with a member that has no value",
    v.intersect([reference, v.object({ globalId: v.string() })]),
    [],
  ],
  [
    "an expanded document intersected with a member that has no value",
    v.intersect([v.object({ relationTo: v.literal("instruments") }), expanded]),
    ["id", "name"],
  ],
  [
    "a union whose first arm declares fewer keys",
    v.object({
      relationTo: v.literal("instruments"),
      value: v.union([v.object({ id: v.number() }), v.object({ id: v.number(), name: v.string() })]),
    }),
    ["id"],
  ],
  ["a union whose first arm has no value", v.union([v.object({ relationTo: v.literal("instruments") }), expanded]), []],
  [
    "a value pipeline that transforms the document",
    v.object({
      relationTo: v.literal("instruments"),
      value: v.pipe(
        v.object({ id: v.number(), globalId: v.string() }),
        v.transform(({ id }) => ({ id })),
      ),
    }),
    [],
  ],
] as const)("reads the expanded keys of %s", (_, schema, keys) => {
  expect([...expandedValueKeys(schema)].sort()).toEqual(keys);
});
