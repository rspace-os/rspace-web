import { describe, expect, test, vi } from 'vitest';
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import Search from "../../Search";
import RsSet from "../../../../util/set";
import { makeMockContainer } from "../ContainerModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
  }})); // break import cycle
vi.mock("../../../../stores/stores/RootStore", () => ({
  default: () => ({})
}));
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

