/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import Result from "../../InventoryBaseRecord";
import fc, { type Command } from "fast-check";
import { mkAlert } from "../../../contexts/Alert";
import { AddScopedToastCommand } from "./addScopedToast";
import { ClearAllScopedToastsCommand } from "./clearAllScopedToasts";
import { mockFactory } from "../../../definitions/__tests__/Factory/mocking";
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
        .map<Command<{| count: number |}, Result>>(
          (message: string) => new AddScopedToastCommand(mkAlert({ message }))
        ),
      fc.constant<Command<{| count: number |}, Result>>(
        new ClearAllScopedToastsCommand()
      ),
    ];
    await fc.assert(
      fc.asyncProperty(
        fc.commands<{| count: number |}, Result>(allCommands),
        async (cmds) => {
          const s = () => ({
            model: { count: 0 },
            real: new Result(mockFactory(), {}),
          });
          await fc.asyncModelRun(s, cmds);
        }
      )
    );
  });
});
