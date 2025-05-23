/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import Result from "../../InventoryBaseRecord";
import { type Command } from "fast-check";
import { type Model } from "./common";

export class ClearAllScopedToastsCommand implements Command<Model, Result> {
  constructor() {}

  check(): boolean {
    return true;
  }

  run(model: Model, result: Result): void {
    result.clearAllScopedToasts();
    model.count = 0;
    expect(model.count).toEqual(result.scopedToasts.length);
  }

  toString(): string {
    return "clearAllScopedToasts";
  }
}
