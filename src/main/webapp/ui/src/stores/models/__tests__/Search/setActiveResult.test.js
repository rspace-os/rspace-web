/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import Search from "../../Search";
import { makeMockContainer } from "../ContainerModel/mocking";
import LocationModel from "../../LocationModel";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type InventoryRecord } from "../../../definitions/InventoryRecord";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle

describe("setActiveResult", () => {
  describe("callback", () => {
    test("should only be called after fetchAdditionalInfo has completed.", async () => {
      const callback = jest.fn<[?InventoryRecord], void>((c) => {
        // $FlowExpectedError[prop-missing] setActiveResult is passed a container
        // $FlowExpectedError[incompatible-use]
        expect(c.locations.length).toBe(1);
      });
      const search = new Search({
        callbacks: {
          setActiveResult: callback,
        },
        factory: mockFactory(),
      });
      const container = makeMockContainer();
      jest.spyOn(container, "fetchAdditionalInfo").mockImplementation(() => {
        container.locations = [
          new LocationModel({
            id: null,
            coordX: 1,
            coordY: 1,
            content: makeMockContainer({
              id: 2,
              globalId: "IC2",
            }),
            parentContainer: container,
          }),
        ];
        return Promise.resolve();
      });

      await search.setActiveResult(container);
    });
  });
});
