import { describe, expect, it, vi } from "vitest";
import { setupRealAppI18n } from "@/__tests__/helpers/realI18n";
import { makeMockField } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }), assertValidUnitId: () => {} } }),
}));

setupRealAppI18n();

describe("FieldModel.validateDisplayCondition", () => {
  it("gives the reason Save is refused, naming the field or calling it unnamed", () => {
    const reason = (name: string) => {
      const field = makeMockField({ type: "string", name });
      field.setDisplayConditionIncomplete(true);
      return field.validateDisplayCondition().orElseGet((errors) => errors.map((error) => error.message).join(" "));
    };

    expect(reason("GMO permit")).toBe("Complete the display condition of GMO permit, or choose Always, before saving.");
    expect(reason("")).toBe("Complete the display condition of Unnamed field, or choose Always, before saving.");
  });
});
