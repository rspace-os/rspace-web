import React from "react";
import { describe, it, expect, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
import LinkTargetBrowser from "../LinkTargetBrowser";

// Search pulls in the store/fetcher chain at construction; stub it out (this is a thin wrapper).
vi.mock("@/stores/models/Search", () => ({
  default: class {
    fetcher = { performInitialSearch: vi.fn(() => Promise.resolve()) };
    uiConfig = { selectionMode: "SINGLE" };
  },
}));
vi.mock("@/stores/models/Factory/AlwaysNewFactory", () => ({ default: class {} }));
vi.mock("@/components/AlwaysNewWindowNavigationContext", () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
// Stub the picker so we can assert the props LinkTargetBrowser passes and drive its onAddition.
vi.mock("@/Inventory/components/Picker/Picker", () => ({
  default: (props: {
    resetActiveResultOnClose?: boolean;
    onAddition: (records: Array<{ globalId: string; name: string }>) => void;
  }) => (
    <div
      data-testid="inventory-picker"
      data-reset={String(props.resetActiveResultOnClose ?? false)}
    >
      <button
        type="button"
        onClick={() =>
          props.onAddition([{ globalId: "SA42", name: "Sample 42" }])
        }
      >
        pick SA42
      </button>
    </div>
  ),
}));

describe("LinkTargetBrowser", () => {
  it("resets the picker's active result on close so a reopened dialog starts fresh", () => {
    render(<LinkTargetBrowser open onPick={() => {}} onCancel={() => {}} />);
    // guards the resetActiveResultOnClose fix: without it, a reused Search model re-emits the
    // previously-picked item when the dialog is reopened
    expect(screen.getByTestId("inventory-picker")).toHaveAttribute(
      "data-reset",
      "true",
    );
  });

  it("calls onPick with the chosen target's global id and name", async () => {
    const onPick = vi.fn();
    const user = userEvent.setup();
    render(<LinkTargetBrowser open onPick={onPick} onCancel={() => {}} />);

    await user.click(screen.getByRole("button", { name: /pick sa42/i }));

    expect(onPick).toHaveBeenCalledWith({
      globalId: "SA42",
      name: "Sample 42",
    });
  });
});
