// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import fc, { type Command } from "fast-check";
import { describe, test, vi } from "vitest";
import { mkAlert } from "../../../contexts/Alert";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import InventoryBaseRecord from "../../InventoryBaseRecord";
import { AddScopedToastCommand } from "./addScopedToast";
import { ClearAllScopedToastsCommand } from "./clearAllScopedToasts";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
// biome-ignore lint/style/useImportType: initial biome migration
import { type Model } from "./common";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/RootStore", () => ({
  default: () => ({
    searchStore: {},
    uiStore: {
      removeAlert: vi.fn(() => {}),
    },
  }),
}));
describe("Scoped Toasts Model Tests", () => {
  test("add and clear", async () => {
    const allCommands = [
      fc.string().map((message: string) => new AddScopedToastCommand(mkAlert({ message }))),
      fc.constant(new ClearAllScopedToastsCommand()),
    ];
    await fc.assert(
      fc.asyncProperty(fc.commands(allCommands), async (cmds) => {
        const s = () => ({
          model: { count: 0 },
          real: new InventoryBaseRecord(mockFactory(), {}),
        });
        await fc.modelRun(s, cmds);
      }),
    );
  });
});
