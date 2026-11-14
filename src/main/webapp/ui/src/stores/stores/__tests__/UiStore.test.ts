import { isObservableObject, reaction } from "mobx";
import { createElement } from "react";
import { describe, expect, it, vi } from "vitest";
import type { RootStore } from "../RootStore";
import UiStore from "../UiStore";

describe("UiStore confirmation dialog", () => {
  it("keeps async confirmation props reactive without converting React nodes", async () => {
    const store = new UiStore({} as RootStore);
    const title = createElement("span", null, "Delete");
    const message = createElement("span", null, "Delete this item?");
    let finishOperation!: () => void;
    const operation = new Promise<void>((resolve) => {
      finishOperation = resolve;
    });
    const observedProps: Array<typeof store.confirmationDialogProps> = [];
    const warn = vi.spyOn(console, "warn");
    const dispose = reaction(
      () => store.confirmationDialogProps,
      (props) => observedProps.push(props),
    );

    try {
      const confirmation = store.confirm(title, message, "Delete", "Cancel", () => operation);
      const openedProps = store.confirmationDialogProps;
      if (!openedProps) throw new Error("The confirmation dialog did not open.");

      expect(openedProps.title).toBe(title);
      expect(openedProps.message).toBe(message);
      expect(isObservableObject(openedProps)).toBe(false);

      openedProps.yes();

      const spinnerProps = store.confirmationDialogProps;
      if (!spinnerProps) throw new Error("The confirmation dialog closed before the operation finished.");

      expect(spinnerProps).not.toBe(openedProps);
      expect(spinnerProps.confirmationSpinner).toBe(true);
      expect(spinnerProps.title).toBe(title);
      expect(spinnerProps.message).toBe(message);
      expect(spinnerProps.yes).toBe(openedProps.yes);
      expect(isObservableObject(spinnerProps)).toBe(false);
      expect(observedProps).toHaveLength(2);
      expect(observedProps[0]).toBe(openedProps);
      expect(observedProps[1]).toBe(spinnerProps);

      finishOperation();
      await expect(confirmation).resolves.toBe(true);

      expect(store.confirmationDialogProps).toBeNull();
      expect(observedProps).toHaveLength(3);
      expect(observedProps[2]).toBeNull();
      expect(warn).not.toHaveBeenCalled();
    } finally {
      dispose();
      warn.mockRestore();
    }
  });

  it("closes with false when the user cancels", async () => {
    const store = new UiStore({} as RootStore);
    const confirmation = store.confirm("Delete", "Delete this item?");
    const props = store.confirmationDialogProps;
    if (!props) throw new Error("The confirmation dialog did not open.");

    props.no();

    await expect(confirmation).resolves.toBe(false);
    expect(store.confirmationDialogProps).toBeNull();
  });
});
