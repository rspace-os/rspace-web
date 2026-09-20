import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ContextDialog from "../ContextDialog";

vi.mock("@/stores/use-stores", () => ({
  default: () => ({ uiStore: { isTouchDevice: false } }),
}));

/**
 * MUI's Dialog decides whether a backdrop click closes it by tracking where the mousedown landed,
 * so this needs the full pointer sequence, not a lone synthetic click. The class selector stays:
 * the backdrop is aria-hidden presentation with no role or name to query it by.
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
