/*
 */

// biome-ignore lint/style/useImportType: initial biome migration
import { type Command } from "fast-check";
import { expect } from "vitest";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Alert } from "../../../contexts/Alert";
// biome-ignore lint/style/useImportType: initial biome migration
import InventoryBaseRecord from "../../InventoryBaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Model } from "./common";

export class AddScopedToastCommand implements Command<Model, InventoryBaseRecord> {
  toast: Alert;

  constructor(toast: Alert) {
    this.toast = toast;
  }

  check(): boolean {
    return true;
  }

  run(model: Model, result: InventoryBaseRecord): void {
    result.addScopedToast(this.toast);
    model.count++;
    expect(model.count).toEqual(result.scopedToasts.length);
  }

  toString(): string {
    return "addScopedToast";
  }
}
