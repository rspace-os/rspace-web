/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import SampleModel, { type SampleAttrs } from "../../SampleModel";
import SubSampleModel, { type SubSampleAttrs } from "../../SubSampleModel";
import { sampleAttrs } from "./mocking";
import { subsampleAttrs } from "../SubSampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    getUnit: () => ({ label: "ml" }),
  },
}));

describe("action: populateFromJson", () => {
  describe("When called, it should", () => {
    test("not use the factory with which it was instantiated.", () => {
      const factory = mockFactory();
      const newRecordSpy = jest
        .spyOn(factory, "newRecord")
        .mockImplementation((attrs: any) =>
          attrs.type === "SAMPLE"
            ? new SampleModel(factory, attrs as SampleAttrs)
            : new SubSampleModel(factory, attrs as SubSampleAttrs)
        );

      const attrs = () => ({
        ...sampleAttrs(),
        subSamples: [subsampleAttrs()],
      });

      const sample = factory.newRecord(attrs());
      expect(newRecordSpy).toHaveBeenCalled();

      newRecordSpy.mockClear();
      const factory2 = mockFactory();
      jest.spyOn(factory2, "newRecord").mockImplementation((a: any) => {
        return a.type === "SAMPLE"
          ? new SampleModel(factory, a as SampleAttrs)
          : new SubSampleModel(factory, a as SubSampleAttrs);
      });

      sample.populateFromJson(factory2, attrs(), {});

      expect(newRecordSpy).not.toHaveBeenCalled();
    });
  });
});
