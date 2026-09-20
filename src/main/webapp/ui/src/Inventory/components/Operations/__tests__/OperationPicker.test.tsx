import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import OperationPicker from "../OperationPicker";
import { MAX_ORIGINS } from "../operationsConfig";
import { operations } from "./testOperations";

const renderPicker = (props: Partial<React.ComponentProps<typeof OperationPicker>> = {}) =>
  render(
    <OperationPicker
      operations={operations}
      onSelect={() => undefined}
      selectionCount={1}
      allSameCategory
      {...props}
    />,
  );

const poolButton = () => screen.getByRole("button", { name: /operations\.pool\.label/i });

describe("OperationPicker", () => {
  it("is accessible", async () => {
    const { container } = renderPicker();
    await expectAccessible(container);
  });

  it("shows every operation, enabling single-origin ones and disabling Pool for one subsample", () => {
    renderPicker();
    expect(screen.getAllByRole("button")).toHaveLength(7);
    expect(screen.getByRole("button", { name: /operations\.derive\.label/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: /operations\.destroy\.label/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(poolButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("enables only Pool for a multi-subsample selection of one measurement category", () => {
    renderPicker({ selectionCount: 2 });
    expect(poolButton()).not.toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("button", { name: /operations\.derive\.label/i })).toHaveAttribute("aria-disabled", "true");
  });

  it("disables Pool above the backend's origin cap, so the wizard never opens a flow Perform rejects", () => {
    renderPicker({ selectionCount: MAX_ORIGINS + 1 });
    expect(poolButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("enables Pool at exactly the cap", () => {
    renderPicker({ selectionCount: MAX_ORIGINS });
    expect(poolButton()).not.toHaveAttribute("aria-disabled", "true");
  });

  it("disables Pool when the selected subsamples span measurement categories", () => {
    renderPicker({ selectionCount: 2, allSameCategory: false });
    expect(poolButton()).toHaveAttribute("aria-disabled", "true");
  });

  it("reports the chosen operation's key when an enabled operation is picked", async () => {
    const chosen: Array<string> = [];
    renderPicker({ onSelect: (o) => chosen.push(o.key) });
    await userEvent.setup().click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(chosen).toContain("derive");
  });

  it("renders the operations in the configured order", () => {
    renderPicker();
    const names = screen.getAllByRole("button").map((b) => b.textContent ?? "");
    const order = ["aliquot", "passage", "pool", "derive", "cryopreserve", "revive", "destroy"];
    order.forEach((key, i) => {
      expect(names[i]).toContain(`operations.${key}.label`);
    });
  });

  it("renders each operation's configured icon", () => {
    const { container } = renderPicker();
    const icons = container.querySelectorAll("svg[data-icon]");
    expect(icons).toHaveLength(7);
    expect(container.querySelector('svg[data-icon="trash"]')).toBeInTheDocument();
    expect(container.querySelector('svg[data-icon="flask"]')).toBeInTheDocument();
  });
});
