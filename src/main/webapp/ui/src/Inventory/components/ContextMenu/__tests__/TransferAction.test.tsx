import Dialog from "@mui/material/Dialog";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { makeMockContainer } from "../../../../stores/models/__tests__/ContainerModel/mocking";
import { makeMockInstrument } from "../../../../stores/models/__tests__/InstrumentModel/mocking";
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

    await user.click(screen.getAllByText("common:actions.transfer")[0]);
    await waitFor(() => {
      expect(Dialog).toHaveBeenCalledWith(expect.objectContaining({ open: true }), undefined);
    });

    await user.click(screen.getByText("common:actions.cancel"));
    await waitFor(() => {
      expect(Dialog).toHaveBeenLastCalledWith(expect.objectContaining({ open: false }), undefined);
    });
  });

  test("does not expose Booking-specific ownership controls", async () => {
    const user = userEvent.setup();
    const { baseElement } = render(
      <ThemeProvider theme={materialTheme}>
        <TransferAction as="button" disabled="" closeMenu={() => {}} selectedResults={[makeMockInstrument()]} />
      </ThemeProvider>,
    );

    await user.click(screen.getAllByText("common:actions.transfer")[0]);

    expect(
      screen.queryByRole("checkbox", {
        name: "inventory:contextMenu.transfer.dialog.transferBookingConfigurationOwnership",
      }),
    ).not.toBeInTheDocument();
    await expectAccessible(baseElement);
  });
});
