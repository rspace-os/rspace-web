import Dialog from "@mui/material/Dialog";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import JwtService from "@/common/JwtService";
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

  test("offers the accessible opt-in only for a manageable Booking configuration", async () => {
    JwtService.saveToken("test-token");
    server.use(
      http.get("/api/v2/booking-configurations", ({ request }) => {
        expect(request.headers.get("Authorization")).toBe("Bearer test-token");
        return HttpResponse.json({
          docs: [
            {
              id: 7,
              target: {
                relationTo: "booking-instruments",
                value: { id: 1, name: "An instrument", deleted: false },
                globalId: "IN1",
              },
              enabled: true,
              timezone: "UTC",
              slotGranularityMinutes: 5,
              openingStart: "00:00",
              openingEnd: "24:00",
              bufferBeforeMinutes: 0,
              bufferAfterMinutes: 0,
              maxBookingDurationMinutes: 0,
              allowDoubleBooking: false,
              capabilities: {
                canEditConfiguration: true,
                canArchiveConfiguration: true,
                canViewAudit: true,
                canViewAccess: true,
                canManageAssignments: true,
                canManageOwners: true,
                canCreateBooking: true,
                canManageOwnBookings: true,
                canManageAllEvents: true,
                canCreateBlockout: true,
                canSubscribeCalendar: true,
                canLeaveConfiguration: false,
              },
            },
          ],
          totalDocs: 1,
          limit: 1,
          totalPages: 1,
          page: 1,
          pagingCounter: 1,
          hasPrevPage: false,
          hasNextPage: false,
          prevPage: null,
          nextPage: null,
        });
      }),
    );
    const user = userEvent.setup();
    const { baseElement } = render(
      <ThemeProvider theme={materialTheme}>
        <TransferAction as="button" disabled="" closeMenu={() => {}} selectedResults={[makeMockInstrument()]} />
      </ThemeProvider>,
    );

    await user.click(screen.getAllByText("common:actions.transfer")[0]);

    expect(
      await screen.findByRole("checkbox", {
        name: "inventory:contextMenu.transfer.dialog.transferBookingConfigurationOwnership",
      }),
    ).toBeInTheDocument();
    await expectAccessible(baseElement);
  });
});
