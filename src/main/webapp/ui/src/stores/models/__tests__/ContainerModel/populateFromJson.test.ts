import { test, describe, expect, vi } from 'vitest';
import { mockFactory } from "../../../../stores/definitions/__tests__/Factory/mocking";
import ContainerModel, { type ContainerAttrs } from "../../ContainerModel";
import { containerAttrs } from "./mocking";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({})
}));

describe("action: populateFromJson", () => {
  describe("When called, it should", () => {
    test("not use the factory with which it was instantiated.", () => {
      const factory = mockFactory();
      const newRecordSpy = vi
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


