/*
 */

// biome-ignore lint/style/useImportType: initial biome migration
import { type Command } from "fast-check";
import { expect } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import InventoryBaseRecord from "../../InventoryBaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Model } from "./common";

export class ClearAllScopedToastsCommand implements Command<Model, InventoryBaseRecord> {
  // biome-ignore lint/complexity/noUselessConstructor: initial biome migration
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
