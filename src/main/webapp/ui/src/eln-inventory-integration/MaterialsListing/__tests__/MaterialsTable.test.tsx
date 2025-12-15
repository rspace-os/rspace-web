/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { sampleAttrs } from "../../../stores/models/__tests__/SampleModel/mocking";
import { subsampleAttrs } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import { ListOfMaterials } from "../../../stores/models/MaterialsModel";
import MaterialsTable from "../MaterialsTable";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../theme";

jest.mock("../../../common/InvApiService", () => {});
jest.mock("../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({
      id: 1,
      label: "foo",
      category: "mass",
      description: "foo is mass",
    }),
    unitsOfCategory: () => [],
  },
  materialsStore: { canEdit: true },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

const sample1 = sampleAttrs({
  id: 1,
  globalId: "SA1",
});
const subsample1 = subsampleAttrs({
  id: 1,
  globalId: "SS1",
  sample: sample1,
  deleted: true,
});
const material1 = {
  invRec: subsample1,
  usedQuantity: {
    unitId: 3,
    numericValue: 0,
  },
};

describe("MaterialsTable", () => {
  describe("Location column", () => {
    test("When the record is deleted, In Trash should be shown.", () => {
      const mockList = new ListOfMaterials({
        materials: [material1],
        id: 1,
        name: "List 1",
        description: "Test List Description",
        elnFieldId: 11,
      });
      render(
        <ThemeProvider theme={materialTheme}>
          <MaterialsTable
            list={mockList}
            isSingleColumn={false}
            onRemove={() => {}}
            canEdit={false}
          />
        </ThemeProvider>
      );

      // second cell because the MaterialTable row has only 2 cells in a row, despite what would be visually intuitive
      expect(screen.getAllByRole("cell")[1]).toHaveTextContent("In Trash");
    });
  });
});
