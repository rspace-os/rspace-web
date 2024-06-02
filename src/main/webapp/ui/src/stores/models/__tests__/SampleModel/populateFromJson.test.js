/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import SampleModel from "../../SampleModel";
import SubSampleModel from "../../SubSampleModel";
import { sampleAttrs } from "./mocking";
import { subsampleAttrs } from "../SubSampleModel/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../stores/RootStore", () => {}); // break import cycle

describe("action: populateFromJson", () => {
  describe("When called, it should", () => {
    test("not use the factory with which it was instantiated.", () => {
      const factory = mockFactory();
      const newRecordSpy = jest
        .spyOn(factory, "newRecord")
        .mockImplementation((attrs) =>
          attrs.type === "SAMPLE"
            ? new SampleModel(factory, attrs)
            : new SubSampleModel(factory, attrs)
        );

      const attrs = () => ({
        ...sampleAttrs(),
        subSamples: [subsampleAttrs()],
      });

      const sample = factory.newRecord(attrs());
      expect(newRecordSpy).toHaveBeenCalled();

      newRecordSpy.mockClear();
      const factory2 = mockFactory();
      jest.spyOn(factory2, "newRecord").mockImplementation((a) => {
        return a.type === "SAMPLE"
          ? new SampleModel(factory, a)
          : new SubSampleModel(factory, a);
      });

      sample.populateFromJson(factory2, attrs());

      expect(newRecordSpy).not.toHaveBeenCalled();
    });
  });
});
