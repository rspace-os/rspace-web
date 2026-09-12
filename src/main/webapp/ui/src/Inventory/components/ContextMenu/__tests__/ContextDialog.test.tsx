import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ContextDialog from "../ContextDialog";

// ContextDialog only reads uiStore.isTouchDevice for its paper positioning.
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ uiStore: { isTouchDevice: false } }),
}));

/**
 * Clicks the backdrop through the full pointer sequence.
 *
 * `fireEvent.click` dispatches one synthetic click and nothing else, but MUI's Dialog decides
 * whether a backdrop click closes it by tracking where the MOUSEDOWN landed, so that a drag ending
 * on the backdrop does not close the dialog. Driving it with userEvent exercises the sequence the
 * component was actually built around, instead of a partial one that could break on an MUI upgrade
 * with no product bug, or stay green while real clicks stopped working (PR #963 review).
 *
 * The class selector stays: the backdrop is aria-hidden presentation with no role or name, so there
 * is nothing semantic to query it by.
 */
async function clickBackdrop() {
  const backdrop = document.querySelector(".MuiBackdrop-root");
  expect(backdrop).not.toBeNull();
  if (backdrop) await userEvent.setup().click(backdrop);
}

describe("ContextDialog", () => {
  it("closes on a backdrop click by default", async () => {
    const onClose = vi.fn();
    render(
      <ContextDialog open onClose={onClose}>
        <div data-testid="body" />
      </ContextDialog>,
    );
    await clickBackdrop();
    expect(onClose).toHaveBeenCalled();
  });

  it("does not close on a backdrop click when disableBackdropClick is set (modal)", async () => {
    const onClose = vi.fn();
    render(
      <ContextDialog open onClose={onClose} disableBackdropClick>
        <div data-testid="body" />
      </ContextDialog>,
    );
    await clickBackdrop();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("still closes on Escape when disableBackdropClick is set", async () => {
    // The guard narrows on reason === "backdropClick" precisely so Escape keeps working. Written
    // as `if (disableBackdropClick) return;` it would trap a keyboard user in the operation wizard,
    // the only component that sets the prop, and both backdrop tests above would still pass
    // (parallel review).
    const onClose = vi.fn();
    render(
      <ContextDialog open onClose={onClose} disableBackdropClick>
        <div data-testid="body" />
      </ContextDialog>,
    );
    await userEvent.setup().keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
