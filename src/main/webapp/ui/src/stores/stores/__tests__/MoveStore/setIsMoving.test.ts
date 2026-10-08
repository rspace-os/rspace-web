import "@/stores/stores/RootStore";
import { describe, expect, test, vi } from "vitest";
import { makeMockContainer } from "../../../models/__tests__/ContainerModel/mocking";
import MoveStore from "../../MoveStore";
import type { RootStore } from "../../RootStore";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => Promise.resolve({ data: { containers: [], totalHits: 0 } })),
  },
})); // break import cycle

describe("action: setIsMoving", () => {
  test("turns off drag-and-drop in the destination container's grid, which is used only to choose locations", async () => {
    const destination = makeMockContainer({ cType: "GRID" });
    vi.spyOn(destination, "fetchAdditionalInfo").mockResolvedValue();
    const moveStore = new MoveStore({
      peopleStore: { currentUser: { bench: destination } },
      uiStore: { setDialogVisiblePanel: () => {} },
    } as unknown as RootStore);

    await moveStore.setIsMoving(true);

    expect(destination.contentSearch.uiConfig.dragAndDropDisabled).toBe(true);
  });
});
