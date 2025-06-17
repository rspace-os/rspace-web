/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import Search from "../../Search";
import { makeMockContainer } from "../ContainerModel/mocking";
import LocationModel from "../../LocationModel";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle

describe("setActiveResult", () => {
  describe("callback", () => {
    test("should only be called after fetchAdditionalInfo has completed.", async () => {
      const callback = jest.fn().mockImplementation((c) => {
        expect(c.locations.length).toBe(1);
      });

      const search = new Search({
        callbacks: {
          setActiveResult: callback,
        },
        factory: mockFactory(),
      });

      const container = makeMockContainer();
      // Mock the fetchAdditionalInfo method
      const mockFetch = jest.spyOn(container, "fetchAdditionalInfo");
      mockFetch.mockImplementation(() => {
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

      // Verify the callback was called
      expect(callback).toHaveBeenCalled();

      // Verify the mock implementation was called
      expect(mockFetch).toHaveBeenCalled();

      // Use non-null assertion since we know we set the locations in the mock
      expect(container.locations!.length).toBe(1);
    });
  });
});
