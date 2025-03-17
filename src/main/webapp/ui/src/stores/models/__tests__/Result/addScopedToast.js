/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import Result from "../../Result";
import { type Alert } from "../../../contexts/Alert";
import { type Model } from "./common";
import { type Command } from "fast-check";

export class AddScopedToastCommand implements Command<Model, Result> {
  toast: Alert;

  constructor(toast: Alert) {
    this.toast = toast;
  }

  check(): boolean {
    return true;
  }

  run(model: Model, result: Result): void {
    result.addScopedToast(this.toast);
    model.count++;
    expect(model.count).toEqual(result.scopedToasts.length);
  }

  toString(): string {
    return "addScopedToast";
  }
}
