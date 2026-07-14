import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import DocumentationStep from "../DocumentationStep";

// Stub the ELN picker (the real one mounts a Search/fetcher); when open, clicking it plays back a
// document through onPick.
vi.mock("@/Inventory/components/Fields/Link/ElnRecordPicker", () => ({
  default: ({ open, onPick }: { open: boolean; onPick: (t: { globalId: string; name: string }) => void }) =>
    open ? (
      <button type="button" data-testid="eln-pick" onClick={() => onPick({ globalId: "SD2", name: "Other SOP" })} />
    ) : null,
}));

let mockDefaults: Record<string, { globalId: string; name: string } | null>;
const mockSetDefaults = vi.fn();
vi.mock("@/hooks/api/useUiPreference", () => ({
  PREFERENCES: { INVENTORY_OPERATION_DOC_DEFAULTS: Symbol.for("INVENTORY_OPERATION_DOC_DEFAULTS") },
  default: () => [mockDefaults, mockSetDefaults],
}));

const remembered = { globalId: "SD1", name: "My SOP" };

describe("DocumentationStep", () => {
  beforeEach(() => {
    mockDefaults = {};
    mockSetDefaults.mockClear();
  });

  it("pre-checks remember and auto-applies the remembered document, with no 'use remembered' button", () => {
    mockDefaults = { derive: remembered };
    const onChange = vi.fn();
    render(<DocumentationStep operationKey="derive" value={null} onChange={onChange} />);
    expect(screen.getByRole("checkbox")).toBeChecked();
    expect(onChange).toHaveBeenCalledWith(remembered);
    expect(screen.queryByRole("button", { name: /use remembered|useRemembered/i })).not.toBeInTheDocument();
  });

  it("persists a newly chosen document while remember is on", async () => {
    mockDefaults = { derive: remembered };
    render(<DocumentationStep operationKey="derive" value={remembered} onChange={vi.fn()} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: /choose|documentation\.choose/i }));
    await user.click(screen.getByTestId("eln-pick"));
    expect(mockSetDefaults).toHaveBeenCalledWith(
      expect.objectContaining({ derive: { globalId: "SD2", name: "Other SOP" } }),
    );
  });

  it("ignores a stored default that is not a well-formed document", () => {
    mockDefaults = { derive: "SD1" as unknown as { globalId: string; name: string } };
    const onChange = vi.fn();
    render(<DocumentationStep operationKey="derive" value={null} onChange={onChange} />);
    expect(screen.getByRole("checkbox")).not.toBeChecked();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("forgets the remembered default when remember is unchecked", async () => {
    mockDefaults = { derive: remembered };
    render(<DocumentationStep operationKey="derive" value={remembered} onChange={vi.fn()} />);
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(mockSetDefaults).toHaveBeenCalledWith(expect.objectContaining({ derive: null }));
  });
});
