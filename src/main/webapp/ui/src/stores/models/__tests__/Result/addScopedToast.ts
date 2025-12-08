/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import type { Command } from "fast-check";
import type { Alert } from "../../../contexts/Alert";
import type InventoryBaseRecord from "../../InventoryBaseRecord";
import type { Model } from "./common";

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
