/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import ContainerModel, { type ContainerAttrs } from "../../ContainerModel";
import { containerAttrs } from "./mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("action: populateFromJson", () => {
  describe("When called, it should", () => {
    test("not use the factory with which it was instantiated.", () => {
      const factory = mockFactory();
      const newRecordSpy = jest
        .spyOn(factory, "newRecord")
        .mockImplementation(
          (attrs) => new ContainerModel(factory, attrs as ContainerAttrs)
        );

      const attrs = (): ContainerAttrs => ({
        ...containerAttrs(),
        locations: [
          {
            id: 1,
            coordX: 1,
            coordY: 1,
            content: containerAttrs({ id: 1, globalId: "IC1" }),
          },
        ],
      });

      const container = factory.newRecord(attrs());
      expect(newRecordSpy).toHaveBeenCalled();

      newRecordSpy.mockClear();

      container.populateFromJson(mockFactory(), attrs(), undefined);

      expect(newRecordSpy).not.toHaveBeenCalled();
    });
  });
});
