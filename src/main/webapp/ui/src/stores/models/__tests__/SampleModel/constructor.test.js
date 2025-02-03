/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";

import SampleModel from "../../SampleModel";
import SubSampleModel from "../../SubSampleModel";
import { sampleAttrs } from "./mocking";
import { subsampleAttrs } from "../SubSampleModel/mocking";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../definitions/Factory";
import { type InventoryRecord } from "../../../definitions/InventoryRecord";

jest.mock("../../../stores/RootStore", () => () => ({
  peopleStore: {},
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
})); // break import cycle

function mockSampleWithTwoSubsamples(factory: Factory) {
  const attrs = sampleAttrs();
  attrs.subSamples = [
    {
      ...subsampleAttrs(),
      id: 2,
      globalId: "SS2",
    },
    {
      ...subsampleAttrs(),
      id: 3,
      globalId: "SS3",
    },
  ];

  return factory.newRecord(attrs);
}

describe("constructor", () => {
  /*
   * The SampleModel's constructor is passed a Factory which is used to
   * instantiate all the its subsamples. These tests assert that SampleModel
   * passes around the Factory it is given correctly.
   */
  describe("Factory argument", () => {
    test("should be used in the instantiation of all child records.", () => {
      let factory: Factory;
      const mockNewRecord = jest
        .fn<[any], InventoryRecord>()
        .mockImplementation((attrs) =>
          /^SA\d+/.test(attrs.globalId)
            ? new SampleModel(factory, attrs)
            : new SubSampleModel(factory, attrs)
        );
      const f: () => Factory = () =>
        mockFactory({
          newRecord: mockNewRecord,
          newFactory: jest.fn<[], Factory>().mockImplementation(f),
        });
      factory = f();
      mockSampleWithTwoSubsamples(factory);

      expect(mockNewRecord).toHaveBeenCalledTimes(3);

      // the root sample
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        1,
        expect.objectContaining({ globalId: "SA1" })
      );

      // both the subsamples (sample is passed manually)
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        2,
        expect.objectContaining({ globalId: "SS2" })
      );
      expect(mockNewRecord).toHaveBeenNthCalledWith(
        3,
        expect.objectContaining({ globalId: "SS3" })
      );
    });
  });
});
