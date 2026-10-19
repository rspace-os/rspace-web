import { describe, expect, it, vi } from "vitest";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import TemplateModel from "../../TemplateModel";
import { templateAttrs } from "./mocking";

const UNITS = [
  { id: 3, label: "ml", category: "volume", description: "" },
  { id: 5, label: "g", category: "mass", description: "" },
  { id: 8, label: "celsius", category: "temperature", description: "" },
];
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: (id: number) => UNITS.find((u) => u.id === id) } }),
}));

describe("TemplateModel.quantityCategory", () => {
  const categoryOf = (defaultUnitId: number) =>
    new TemplateModel(new AlwaysNewFactory(), templateAttrs({ defaultUnitId })).quantityCategory;

  it("comes from the template's own default unit, not the quantity it does not have", () => {
    expect(categoryOf(5)).toBe("mass");
    expect(categoryOf(3)).toBe("volume");
  });
});
