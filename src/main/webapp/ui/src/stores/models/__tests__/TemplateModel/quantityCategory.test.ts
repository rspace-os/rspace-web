import { describe, expect, it, vi } from "vitest";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import TemplateModel from "../../TemplateModel";
import { templateAttrs } from "./mocking";

// Units as the store serves them; only id and category matter here.
const UNITS = [
  { id: 3, label: "ml", category: "volume", description: "" },
  { id: 5, label: "g", category: "mass", description: "" },
  { id: 8, label: "celsius", category: "temperature", description: "" },
];
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({ unitStore: { getUnit: (id: number) => UNITS.find((u) => u.id === id) } }),
}));

describe("TemplateModel.quantityCategory", () => {
  // A template carries no quantity of its own (quantity is always null); it declares the unit its
  // samples are made in as defaultUnitId. The inherited HasQuantity getter reads `quantity` and
  // falls back to unit 3, so every template used to report "volume" (Copilot review, PR #1090).
  const categoryOf = (defaultUnitId: number) =>
    new TemplateModel(new AlwaysNewFactory(), templateAttrs({ defaultUnitId })).quantityCategory;

  it("comes from the template's own default unit, not the quantity it does not have", () => {
    expect(categoryOf(5)).toBe("mass");
    expect(categoryOf(3)).toBe("volume");
  });
});
