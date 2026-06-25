import Dialog from "@mui/material/Dialog";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "../../../../theme";
import TransferAction from "../TransferAction";

vi.mock("@mui/material/Dialog", () => ({
  default: vi.fn(({ children }: { children: React.ReactNode }) => <>{children}</>),
}));
vi.mock("../../../../common/InvApiService", () => ({ default: {} }));
describe("TransferAction", () => {
  test("Dialog should close when cancel is tapped.", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider theme={materialTheme}>
        <TransferAction as="button" disabled="" closeMenu={() => {}} selectedResults={[makeMockContainer()]} />
      </ThemeProvider>,
    );
    await waitFor(() => {
      expect(Dialog).toHaveBeenCalledWith(expect.objectContaining({ open: false }), undefined);
    });

    await user.click(screen.getAllByText("contextMenu.transfer.action")[0]);
    await waitFor(() => {
      expect(Dialog).toHaveBeenCalledWith(expect.objectContaining({ open: true }), undefined);
    });

    await user.click(screen.getByText("actions.cancel"));
    await waitFor(() => {
      expect(Dialog).toHaveBeenLastCalledWith(expect.objectContaining({ open: false }), undefined);
    });
  });
});
