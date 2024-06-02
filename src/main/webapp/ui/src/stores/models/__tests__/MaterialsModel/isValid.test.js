/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { containerAttrs } from "../ContainerModel/mocking";
import {
  ListOfMaterials,
  type ListOfMaterialsAttrs,
} from "../../MaterialsModel";

function generateListOfMaterials(attrs: Partial<ListOfMaterialsAttrs>) {
  return new ListOfMaterials({
    id: null,
    name: "foo",
    description: "bar",
    elnFieldId: 0,
    materials: [
      {
        invRec: containerAttrs(),
        usedQuantity: null,
      },
    ],
    ...attrs,
  });
}

describe("isValid", () => {
  describe("Name is checked", () => {
    test("Name of length 255 is valid", () => {
      const mat = generateListOfMaterials({
        name: new Array<" ">(255).fill(" ").join(""),
      });

      expect(mat.isValid).toBe(true);
    });
    test("Name of length 256 is invalid", () => {
      const mat = generateListOfMaterials({
        name: new Array<" ">(256).fill(" ").join(""),
      });

      expect(mat.isValid).toBe(false);
    });
  });
  describe("Description is checked", () => {
    test("Description of length 255 is valid", () => {
      const mat = generateListOfMaterials({
        description: new Array<" ">(255).fill(" ").join(""),
      });

      expect(mat.isValid).toBe(true);
    });
    test("Description of length 256 is invalid", () => {
      const mat = generateListOfMaterials({
        description: new Array<" ">(256).fill(" ").join(""),
      });

      expect(mat.isValid).toBe(false);
    });
  });
});
