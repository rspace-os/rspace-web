/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import InventoryBaseRecord from "../../InventoryBaseRecord";
import fc, { type Command } from "fast-check";
import { mkAlert } from "../../../contexts/Alert";
import { AddScopedToastCommand } from "./addScopedToast";
import { ClearAllScopedToastsCommand } from "./clearAllScopedToasts";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
import { type Factory } from "../../../definitions/Factory";
import { type Model } from "./common";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../stores/RootStore", () => () => ({
  searchStore: {},
  uiStore: {
    removeAlert: jest.fn(() => {}),
  },
}));

describe("Scoped Toasts Model Tests", () => {
  test("add and clear", async () => {
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
            real: new InventoryBaseRecord(mockFactory() as Factory, {}),
          });
          fc.modelRun(s, cmds);
        }
      )
    );
  });
});
