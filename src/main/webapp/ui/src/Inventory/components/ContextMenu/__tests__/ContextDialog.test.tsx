import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ContextDialog from "../ContextDialog";

// ContextDialog only reads uiStore.isTouchDevice for its paper positioning.
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ uiStore: { isTouchDevice: false } }),
}));

function clickBackdrop() {
  const backdrop = document.querySelector(".MuiBackdrop-root");
  expect(backdrop).not.toBeNull();
  if (backdrop) fireEvent.click(backdrop);
}

describe("ContextDialog", () => {
  it("closes on a backdrop click by default", () => {
    const onClose = vi.fn();
    render(
      <ContextDialog open onClose={onClose}>
        <div data-testid="body" />
      </ContextDialog>,
    );
    clickBackdrop();
    expect(onClose).toHaveBeenCalled();
  });

  it("does not close on a backdrop click when disableBackdropClick is set (modal)", () => {
    const onClose = vi.fn();
    render(
      <ContextDialog open onClose={onClose} disableBackdropClick>
        <div data-testid="body" />
      </ContextDialog>,
    );
    clickBackdrop();
    expect(onClose).not.toHaveBeenCalled();
  });
});
