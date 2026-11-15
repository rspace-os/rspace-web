import * as v from "valibot";
import { expect, it } from "vitest";
import * as native from "../../src/modules/common/api/v2/generated/valibot/native";

it("keeps problem extension members in native parsing", () => {
  expect(
    v.parse(native.ApiV2Problem, {
      status: 409,
      title: "Conflict",
      code: "conflict",
      conflict: { id: 1 },
    }),
  ).toMatchObject({ conflict: { id: 1 } });
});
