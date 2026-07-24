import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import DocumentationStep from "../DocumentationStep";

// Stub the ELN picker (the real one mounts a Search/fetcher); when open, clicking it plays back a
// document through onPick.
vi.mock("@/Inventory/components/Fields/Link/ElnRecordPicker", () => ({
  default: ({ open, onPick }: { open: boolean; onPick: (t: { globalId: string; name: string }) => void }) =>
    open ? (
      <button type="button" data-testid="eln-pick" onClick={() => onPick({ globalId: "SD2", name: "Other SOP" })} />
    ) : null,
}));

const doc = { globalId: "SD1", name: "My SOP" };

describe("DocumentationStep", () => {
  it("shows the selected-document state with a Clear button", () => {
    render(<DocumentationStep value={doc} onChange={vi.fn()} />);
    expect(screen.getByText(/documentation\.selected/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /clear|actions\.clear/i })).toBeInTheDocument();
  });

  it("reports a chosen document through onChange", async () => {
    const onChange = vi.fn();
    render(<DocumentationStep value={null} onChange={onChange} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /choose|documentation\.choose/i }));
    await user.click(screen.getByTestId("eln-pick"));
    expect(onChange).toHaveBeenCalledWith({ globalId: "SD2", name: "Other SOP" });
  });

  it("clears the selection through onChange(null)", async () => {
    const onChange = vi.fn();
    render(<DocumentationStep value={doc} onChange={onChange} />);
    await userEvent.setup().click(screen.getByRole("button", { name: /clear|actions\.clear/i }));
    expect(onChange).toHaveBeenCalledWith(null);
  });
});
