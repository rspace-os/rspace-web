/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import InventoryBaseRecord from "../../InventoryBaseRecord";
import { type Command } from "fast-check";
import { type Model } from "./common";

export class ClearAllScopedToastsCommand
  implements Command<Model, InventoryBaseRecord>
{
  constructor() {}

  check(): boolean {
    return true;
  }

  run(model: Model, result: InventoryBaseRecord): void {
    result.clearAllScopedToasts();
    model.count = 0;
    expect(model.count).toEqual(result.scopedToasts.length);
  }

  toString(): string {
    return "clearAllScopedToasts";
  }
}
