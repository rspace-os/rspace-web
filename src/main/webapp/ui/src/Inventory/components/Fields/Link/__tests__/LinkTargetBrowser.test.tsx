import React from "react";
import { describe, it, expect, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
import LinkTargetBrowser from "../LinkTargetBrowser";

// Search pulls in the store/fetcher chain at construction; stub it out (this is a thin wrapper).
const { searchConstructorArgs } = vi.hoisted(() => ({
  searchConstructorArgs: [] as Array<unknown>,
}));
vi.mock("@/stores/models/Search", () => ({
  default: class {
    fetcher = { performInitialSearch: vi.fn(() => Promise.resolve()) };
    uiConfig = { selectionMode: "SINGLE" };

    constructor(args: unknown) {
      searchConstructorArgs.push(args);
    }
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
      <button type="button" onClick={() => props.onAddition([])}>
        cancel picker
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

  it("lets sample templates be browsed as link targets", () => {
    render(<LinkTargetBrowser open onPick={() => {}} onCancel={() => {}} />);

    const args = searchConstructorArgs.at(-1) as {
      uiConfig: { allowedTypeFilters: Set<string> };
    };
    expect([...args.uiConfig.allowedTypeFilters]).toContain("TEMPLATE");
  });

  it("requires an explicit Choose click instead of confirming on row click", () => {
    // mirrors Browse ELN: clicking a result only selects it; the picker's
    // Choose button (enabled once something is selected) confirms the pick.
    // instantConfirm would instead fire onPick the moment a row is clicked.
    render(<LinkTargetBrowser open onPick={() => {}} onCancel={() => {}} />);

    const args = searchConstructorArgs.at(-1) as {
      uiConfig: { instantConfirm?: boolean };
    };
    expect(args.uiConfig.instantConfirm).toBe(false);
  });

  it("constructs the search model once, not on every render", () => {
    // useState's initializer must be lazy: a Search constructed per render is
    // discarded by React immediately but still pays the store/fetcher setup
    // cost (and any MobX side effects) on each render
    const before = searchConstructorArgs.length;
    const { rerender } = render(
      <LinkTargetBrowser open onPick={() => {}} onCancel={() => {}} />,
    );
    rerender(
      <LinkTargetBrowser open={false} onPick={() => {}} onCancel={() => {}} />,
    );
    rerender(<LinkTargetBrowser open onPick={() => {}} onCancel={() => {}} />);

    expect(searchConstructorArgs.length - before).toBe(1);
  });

  it("treats an empty addition as cancel so the Cancel button closes the dialog", async () => {
    // defensive: the Choose button is disabled while nothing is selected, but
    // an empty confirmation must still close rather than commit an empty pick
    const onCancel = vi.fn();
    const user = userEvent.setup();
    render(<LinkTargetBrowser open onPick={() => {}} onCancel={onCancel} />);

    await user.click(screen.getByRole("button", { name: /cancel picker/i }));

    expect(onCancel).toHaveBeenCalled();
  });
});
