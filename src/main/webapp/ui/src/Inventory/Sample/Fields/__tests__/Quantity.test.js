/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockRootStore } from "../../../../stores/stores/__tests__/RootStore/mocking";
import Quantity from "../Quantity";
import { makeMockSample } from "../../../../stores/models/__tests__/SampleModel/mocking";
import { storesContext } from "../../../../stores/stores-context";
import fc from "fast-check";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({
      id: 1,
      label: "foo",
      category: "mass",
      description: "foo is mass",
    }),
  },
}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Quantity", () => {
  describe("Helper text should render correctly.", () => {
    test("Whole units of quantity should have zero decimal places.", () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.nat(1000), fc.nat(100)),
          ([quantity, count]) => {
            fc.pre(count >= 2);
            const sample = makeMockSample({
              id: null,
              quantity: {
                numericValue: quantity,
                unitId: 1,
              },
            });
            sample.setAttributes({
              newSampleSubSamplesCount: count,
            });
            const rootStore = makeMockRootStore({
              uiStore: {},
              unitStore: {
                units: [
                  {
                    id: 1,
                    label: "foo",
                    category: "mass",
                    description: "foo is mass",
                  },
                ],
                unitsOfCategory: () => [],
              },
            });
            const { container } = render(
              <storesContext.Provider value={rootStore}>
                <Quantity onErrorStateChange={() => {}} sample={sample} />
              </storesContext.Provider>
            );
            expect(container).toHaveTextContent(/\d+ foo/);
          }
        )
      );
    });

    test("Fractional units of quantity should have zero or two decimal places.", () => {
      fc.assert(
        fc.property(
          fc.tuple(fc.float({ min: 0, max: 1000 }), fc.nat(100)),
          ([quantity, count]) => {
            fc.pre(count >= 2);
            const sample = makeMockSample({
              id: null,
              quantity: {
                numericValue: quantity,
                unitId: 1,
              },
            });
            sample.setAttributes({
              newSampleSubSamplesCount: count,
            });
            const rootStore = makeMockRootStore({
              uiStore: {},
              unitStore: {
                units: [
                  {
                    id: 1,
                    label: "foo",
                    category: "mass",
                    description: "foo is mass",
                  },
                ],
                unitsOfCategory: () => [],
              },
            });
            const { container } = render(
              <storesContext.Provider value={rootStore}>
                <Quantity onErrorStateChange={() => {}} sample={sample} />
              </storesContext.Provider>
            );
            expect(container).toHaveTextContent(/\d+(\.\d\d)? foo/);
          }
        )
      );
    });
  });
});
