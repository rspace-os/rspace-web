import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { render } from "@/__tests__/customQueries";
import OperationPicker from "../OperationPicker";

describe("OperationPicker", () => {
  it("lists the operations from operations_config.json as selectable buttons", () => {
    render(<OperationPicker onSelect={() => undefined} />);
    // Derive + Cryopreserve are the two shipped operations.
    expect(screen.getAllByRole("button")).toHaveLength(2);
  });

  it("reports the chosen operation's key", async () => {
    const chosen: Array<string> = [];
    render(<OperationPicker onSelect={(o) => chosen.push(o.key)} />);
    await userEvent.setup().click(screen.getAllByRole("button")[0]);
    expect(chosen).toContain("derive");
  });
});
