import { describe, expect, it } from "vitest";
import AlwaysNewFactory from "../../Factory/AlwaysNewFactory";
import TemplateModel from "../../TemplateModel";
import { templateAttrs } from "./mocking";

describe("TemplateModel.quantityCategory", () => {
  const categoryOf = (defaultUnitId: number) =>
    new TemplateModel(new AlwaysNewFactory(), templateAttrs({ defaultUnitId })).quantityCategory;

  it("comes from the template's own default unit, not the quantity it does not have", () => {
    expect(categoryOf(5)).toBe("mass");
    expect(categoryOf(3)).toBe("volume");
  });
});
