import * as v from "valibot";
import { expect, it } from "vitest";
import { v2ListEnvelope as responseEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { v2ListEnvelope, v2PageEnvelope } from "../v2Pagination";

it("accepts count-free pages while keeping counted lists strict", () => {
  const page = responseEnvelope(["one"]);
  expect(v.safeParse(v2PageEnvelope(v.string()), page).success).toBe(true);
  expect(v.safeParse(v2ListEnvelope(v.string()), page).success).toBe(false);
  expect(v.safeParse(v2ListEnvelope(v.string()), responseEnvelope(["one"], true)).success).toBe(true);
});

it.each([-1, 1.5, null, "1"])("rejects invalid optional totals %s", (totalDocs) => {
  expect(v.safeParse(v2PageEnvelope(v.string()), { ...responseEnvelope([]), totalDocs }).success).toBe(false);
});
