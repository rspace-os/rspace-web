import * as v from "valibot";
import { expect, it } from "vitest";
import * as native from "../../src/modules/common/api/v2/generated/valibot/native";
import { apiV2Operations } from "../../src/modules/common/api/v2/generated/valibot/operations";

it.skipIf(!native.BookingsRead)("accepts expanded relationships and sparse selected responses", () => {
  expect(
    v.safeParse(native.BookingConfigurationsRead, {
      id: 1,
      location: { relationTo: "containers", globalId: "IC1", value: { id: 1 } },
    }).success,
  ).toBe(true);
  expect(
    v.safeParse(native.ListBookingsResponse, {
      docs: [{ id: 1, "target.customFields": { foo: { bar: 1 } } }],
      limit: 10,
      page: 1,
    }).success,
  ).toBe(true);
  const selected = v.required(v.pick(native.BookingsRead, ["purpose"]));
  expect(v.safeParse(selected, { purpose: 42 }).success).toBe(false);
  expect(v.safeParse(selected, {}).success).toBe(false);
  expect(v.parse(native.BookingsRead, { id: 1, "target.customFields": { foo: 1 } })).toEqual({
    id: 1,
    "target.customFields": { foo: 1 },
  });
});
it.skipIf(!apiV2Operations.createBooking)("validates constrained requests through the generated registry", () => {
  const body = {
    start: "2026-11-07T09:00:00Z",
    end: "2026-11-07T10:00:00+01:00",
    target: { relationTo: "instruments", value: 1, globalId: "IN1" },
  };
  const schema = apiV2Operations.createBooking.request;
  expect(v.safeParse(schema, body).success).toBe(true);
  expect(v.safeParse(schema, { ...body, purpose: "a".repeat(1001) }).success).toBe(false);
  expect(v.safeParse(schema, { ...body, start: "yesterday" }).success).toBe(false);
});
it("keeps problem extension members in native parsing", () => {
  expect(
    v.parse(native.ApiV2Problem, { status: 409, title: "Conflict", code: "conflict", conflict: { id: 1 } }),
  ).toMatchObject({ conflict: { id: 1 } });
});
