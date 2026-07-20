import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import OperationPicker from "../OperationPicker";

describe("OperationPicker", () => {
  it("lists the operations from operations_config.json as selectable buttons", () => {
    render(<OperationPicker onSelect={() => undefined} />);
    // One button per shipped operation: Derive, Cryopreserve, Aliquot, Revive, Passage.
    expect(screen.getAllByRole("button")).toHaveLength(5);
  });

  it("reports the chosen operation's key", async () => {
    const chosen: Array<string> = [];
    render(<OperationPicker onSelect={(o) => chosen.push(o.key)} />);
    await userEvent.setup().click(screen.getAllByRole("button")[0]);
    expect(chosen).toContain("derive");
  });
});
