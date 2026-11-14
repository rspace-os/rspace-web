import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { BookableItemsBulkActions } from "../BookableItemsBulkActions";

const selection = { selectedRowIds: new Set(["7", "8"]), clearSelection: vi.fn() };
describe("bulk disable confirmation", () => {
  it("keeps selection on cancel and runs only after confirmation", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn().mockResolvedValue(undefined);
    render(
      <BookableItemsBulkActions
        selection={selection}
        disabled={false}
        activeAction={null}
        failedAction={null}
        onAction={onAction}
      />,
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.bulk.actions.disable" }));
    const dialog = screen.getByRole("alertdialog");
    await expectAccessible(dialog);
    expect(onAction).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "common:actions.cancel" }));
    expect(selection.selectedRowIds.size).toBe(2);
    expect(onAction).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.bulk.actions.disable" }));
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "booking:bookableItems.bulk.actions.disable",
      }),
    );
    expect(onAction).toHaveBeenCalledWith("disable", selection.selectedRowIds);
  });
  it("keeps a failed disable error inside its dialog", async () => {
    const user = userEvent.setup();
    render(
      <BookableItemsBulkActions
        selection={selection}
        disabled={false}
        activeAction={null}
        failedAction="disable"
        onAction={vi.fn().mockRejectedValue(new Error("Failed"))}
      />,
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.bulk.actions.disable" }));
    expect(within(screen.getByRole("alertdialog")).getByRole("alert")).toHaveTextContent(
      "booking:bookableItems.bulk.errors.disable",
    );
    expect(screen.getAllByRole("alert")).toHaveLength(1);
  });
  it("enables immediately", async () => {
    const user = userEvent.setup();
    const onAction = vi.fn().mockResolvedValue(undefined);
    render(
      <BookableItemsBulkActions
        selection={selection}
        disabled={false}
        activeAction={null}
        failedAction={null}
        onAction={onAction}
      />,
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItems.bulk.actions.enable" }));
    expect(onAction).toHaveBeenCalledWith("enable", selection.selectedRowIds);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
