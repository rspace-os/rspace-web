/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import Search from "../../Search";
import RsSet from "../../../../util/set";
import { makeMockContainer } from "../ContainerModel/mocking";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("batchEditableInstance", () => {
  describe("Submittable", () => {
    test("CurrentlyEditableFields is checked.", async () => {
      const search = new Search({
        factory: mockFactory(),
      });
      const containers = [
        makeMockContainer(),
        makeMockContainer({
          id: 2,
          globalId: "IC2",
        }),
      ];
      await search.enableBatchEditing(new RsSet(containers));

      containers.forEach((container) => {
        container.setFieldEditable("name", true);
      });
      expect(search.batchEditableInstance.submittable.isOk).toBe(true);

      containers.forEach((container) => {
        container.setFieldEditable("name", false);
      });
      expect(search.batchEditableInstance.submittable.isOk).toBe(false);
    });
  });
});
