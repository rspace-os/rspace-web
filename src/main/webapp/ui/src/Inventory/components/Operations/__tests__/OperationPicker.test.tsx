import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import OperationPicker from "../OperationPicker";

// The picker shows every operation; each is enabled or aria-disabled for the current selection.
describe("OperationPicker", () => {
  it("shows every operation, enabling single-origin ones and disabling Pool for one subsample", () => {
    render(<OperationPicker onSelect={() => undefined} selectionCount={1} allSameCategory />);
    // Seven operations ship: derive, cryopreserve, aliquot, revive, passage, pool, destroy.
    expect(screen.getAllByRole("button")).toHaveLength(7);
    expect(screen.getByRole("button", { name: /operations\.derive\.label/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    // Destroy is single-origin, so it is enabled for one subsample.
    expect(screen.getByRole("button", { name: /operations\.destroy\.label/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: /operations\.pool\.label/i })).toHaveAttribute("aria-disabled", "true");
  });

  it("enables only Pool for a multi-subsample selection of one measurement category", () => {
    render(<OperationPicker onSelect={() => undefined} selectionCount={2} allSameCategory />);
    expect(screen.getByRole("button", { name: /operations\.pool\.label/i })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("button", { name: /operations\.derive\.label/i })).toHaveAttribute("aria-disabled", "true");
  });

  it("disables Pool when the selected subsamples span measurement categories", () => {
    render(<OperationPicker onSelect={() => undefined} selectionCount={2} allSameCategory={false} />);
    expect(screen.getByRole("button", { name: /operations\.pool\.label/i })).toHaveAttribute("aria-disabled", "true");
  });

  it("reports the chosen operation's key when an enabled operation is picked", async () => {
    const chosen: Array<string> = [];
    render(<OperationPicker onSelect={(o) => chosen.push(o.key)} selectionCount={1} allSameCategory />);
    await userEvent.setup().click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(chosen).toContain("derive");
  });

  it("renders the operations in the configured order", () => {
    render(<OperationPicker onSelect={() => undefined} selectionCount={1} allSameCategory />);
    const names = screen.getAllByRole("button").map((b) => b.textContent ?? "");
    const order = ["aliquot", "passage", "pool", "derive", "cryopreserve", "revive", "destroy"];
    order.forEach((key, i) => {
      expect(names[i]).toContain(`operations.${key}.label`);
    });
  });

  it("renders each operation's configured icon", () => {
    const { container } = render(<OperationPicker onSelect={() => undefined} selectionCount={1} allSameCategory />);
    // FontAwesomeIcon renders an <svg data-icon="..."> per operation; every operation carries an icon.
    const icons = container.querySelectorAll("svg[data-icon]");
    expect(icons).toHaveLength(7);
    expect(container.querySelector('svg[data-icon="trash"]')).toBeInTheDocument();
    expect(container.querySelector('svg[data-icon="flask"]')).toBeInTheDocument();
  });
});
