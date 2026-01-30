/*
 */
import { describe, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import InventoryBaseRecord from "../../InventoryBaseRecord";
import fc, { type Command } from "fast-check";
import { mkAlert } from "../../../contexts/Alert";
import { AddScopedToastCommand } from "./addScopedToast";
import { ClearAllScopedToastsCommand } from "./clearAllScopedToasts";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../definitions/Factory";
import { type Model } from "./common";

vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../stores/RootStore", () => ({
  default: () => ({
  searchStore: {},
  uiStore: {
    removeAlert: vi.fn(() => {}),
  },
})
}));

describe("Scoped Toasts Model Tests", () => {
  it("add and clear", async () => {
    const allCommands = [
      fc
        .string()
        .map<Command<Model, InventoryBaseRecord>>(
          (message: string) => new AddScopedToastCommand(mkAlert({ message }))
        ),
      fc.constant<Command<Model, InventoryBaseRecord>>(
        new ClearAllScopedToastsCommand()
      ),
    ];
    await fc.assert(
      fc.asyncProperty(
        fc.commands<Model, InventoryBaseRecord>(allCommands),
        async (cmds) => {
          const s = () => ({
            model: { count: 0 },
            real: new InventoryBaseRecord(mockFactory(), {}),
          });
          fc.modelRun(s, cmds);
        }
      )
    );
  });
});


