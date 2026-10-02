import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ComponentProps, ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { DeleteBookingDialog } from "../DeleteBookingDialog";

const cancelledBooking = {
  id: 41,
  version: 1,
  target: {
    relationTo: "booking-instruments",
    value: { id: 12, name: "Scope", deleted: false },
    globalId: "IN12",
  },
  timezone: "Europe/Berlin",
  start: "2026-08-17T06:00:00Z",
  end: "2026-08-17T07:00:00Z",
  state: "CANCELLED",
  privacy: "full",
  purpose: null,
  cancellationReason: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  canCancel: false,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
} as const;

function renderDialog(
  onDeleted = vi.fn(),
  iconOnly = false,
  eventKind: "BOOKING" | "MAINTENANCE" = "BOOKING",
  extraProps: Partial<ComponentProps<typeof DeleteBookingDialog>> = {},
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  const result = render(
    <DeleteBookingDialog
      bookingId={41}
      bookingVersion={0}
      itemName="Confocal microscope"
      period="08:00–09:00"
      token="token"
      eventKind={eventKind}
      iconOnly={iconOnly}
      onDeleted={onDeleted}
      {...extraProps}
    />,
    {
      wrapper: ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      ),
    },
  );
  return { ...result, invalidate, onDeleted };
}

describe("DeleteBookingDialog", () => {
  it("opens from its icon-only trigger", async () => {
    const user = userEvent.setup();
    renderDialog(vi.fn(), true);

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(screen.getByRole("alertdialog")).toBeVisible();
  });

  it("cancels without a request and returns focus to the trigger", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.patch("/api/v2/bookings/41", () => {
        requests += 1;
        return HttpResponse.json(cancelledBooking);
      }),
    );
    renderDialog();

    const trigger = screen.getByRole("button", { name: "booking:bookings.actions.cancel" });
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "booking:bookings.cancelDialog.keep" }));

    expect(requests).toBe(0);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("sends one exact PATCH, blocks duplicate actions, invalidates, and calls onDeleted", async () => {
    const user = userEvent.setup();
    let resolveRequest: (() => void) | undefined;
    const waiting = new Promise<void>((resolve) => {
      resolveRequest = resolve;
    });
    const requests: Request[] = [];
    server.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        requests.push(request.clone());
        await waiting;
        return HttpResponse.json(cancelledBooking);
      }),
    );
    const { invalidate, onDeleted } = renderDialog();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    const confirm = screen.getByRole("button", { name: "booking:bookings.actions.cancel" });
    const cancel = screen.getByRole("button", { name: "booking:bookings.cancelDialog.keep" });
    await user.click(confirm);

    expect(confirm).toBeDisabled();
    expect(confirm).toHaveAttribute("aria-busy", "true");
    expect(cancel).toBeDisabled();
    await user.click(confirm);
    expect(requests).toHaveLength(1);

    resolveRequest?.();
    await screen.findByRole("button", { name: "booking:bookings.actions.cancel" });
    expect(await requests[0].json()).toEqual({ state: "CANCELLED" });
    expect(requests[0].method).toBe("PATCH");
    expect(requests[0].headers.get("Authorization")).toBe("Bearer token");
    expect(requests[0].headers.get("If-Match")).toBe('"0"');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidate).toHaveBeenCalledOnce();
    expect(onDeleted).toHaveBeenCalledOnce();
  });

  it("trims a reason into the cancellation PATCH and omits whitespace-only reasons", async () => {
    const user = userEvent.setup();
    const requests: Request[] = [];
    server.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        requests.push(request.clone());
        return HttpResponse.json({ ...cancelledBooking, cancellationReason: "Needs recalibration" });
      }),
    );
    renderDialog();

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    await user.type(
      screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" }),
      "  Needs recalibration  ",
    );
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await screen.findByRole("button", { name: "booking:bookings.actions.cancel" })).toBeVisible();
    expect(await requests[0].json()).toEqual({ state: "CANCELLED", cancellationReason: "Needs recalibration" });

    server.use(
      http.patch("/api/v2/bookings/41", async ({ request }) => {
        requests.push(request.clone());
        return HttpResponse.json(cancelledBooking);
      }),
    );
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    await user.type(screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" }), "   ");
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await requests[1].json()).toEqual({ state: "CANCELLED" });
  });

  it("keeps the typed reason after a failed request and shows reason validation errors", async () => {
    const user = userEvent.setup();
    server.use(
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json(
          { status: 400, code: "errors.api.v2.booking.cancellationReason.length", detail: "Too long" },
          { status: 400 },
        ),
      ),
    );
    renderDialog();

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    const reason = screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" });
    await user.type(reason, "A reason");
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.cancellationReasonLength");
    expect(reason).toHaveValue("A reason");
  });

  it("does not retry after a stale response and preserves the reason", async () => {
    const user = userEvent.setup();
    let requests = 0;
    server.use(
      http.patch("/api/v2/bookings/41", () => {
        requests += 1;
        return HttpResponse.json(
          { status: 412, code: "errors.api.v2.booking.concurrentModification", detail: "stale" },
          { status: 412 },
        );
      }),
    );
    renderDialog();

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    const reason = screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" });
    await user.type(reason, "Changed equipment");
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.deleteStale");
    expect(reason).toHaveValue("Changed equipment");
    expect(requests).toBe(1);
  });

  it.each([
    [403, "errors.api.v2.forbidden", "booking:bookings.errors.deleteForbidden"],
    [409, "errors.api.v2.booking.state.transition", "booking:bookings.errors.deleteStale"],
    [412, "errors.api.v2.booking.concurrentModification", "booking:bookings.errors.deleteStale"],
  ])("shows local text for a %s cancellation failure", async (status, code, message) => {
    const user = userEvent.setup();
    server.use(
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json({ status, code, detail: "Do not display this" }, { status }),
      ),
    );
    const { invalidate } = renderDialog();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("alert")).not.toHaveTextContent("Do not display this");
    expect(screen.getByRole("alertdialog")).toBeVisible();
    if (status !== 403) {
      expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
      expect(invalidate).toHaveBeenCalledOnce();
    }
  });

  it("keeps an accessible dialog open after an unknown failure", async () => {
    const user = userEvent.setup();
    server.use(http.patch("/api/v2/bookings/41", () => HttpResponse.error()));
    renderDialog();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    await expectAccessible(document.body);
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.deleteGeneric");
    expect(screen.getByRole("alertdialog")).toBeVisible();
  });

  it("uses maintenance-specific cancellation wording", async () => {
    const user = userEvent.setup();
    renderDialog(vi.fn(), false, "MAINTENANCE");

    await user.click(screen.getByRole("button", { name: "booking:bookings.details.cancelMaintenance" }));

    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent("booking:bookings.details.cancelMaintenanceDescription");
    expect(screen.getByRole("heading")).toHaveTextContent("booking:bookings.details.cancelMaintenanceTitle");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.cancelDialog.keepMaintenance" })).toBeVisible();
    expect(within(dialog).getByRole("button", { name: "booking:bookings.details.cancelMaintenance" })).toBeVisible();
  });

  it("names the booking and period, and offers to keep the booking rather than a second Cancel", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    const dialog = screen.getByRole("alertdialog");
    expect(dialog).toHaveTextContent("booking:bookings.cancelDialog.description");
    expect(dialog).not.toHaveTextContent("booking:bookings.details.cancelDescription");
    expect(within(dialog).getByRole("button", { name: "booking:bookings.cancelDialog.keep" })).toBeVisible();
    expect(within(dialog).queryByRole("button", { name: "common:actions.cancel" })).not.toBeInTheDocument();
  });

  it("falls back to the generic description without an item name and period", async () => {
    const user = userEvent.setup();
    renderDialog(vi.fn(), false, "BOOKING", { itemName: "", period: "" });

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(screen.getByRole("alertdialog")).toHaveTextContent("booking:bookings.details.cancelDescription");
  });

  it("can be opened and closed by its caller without rendering a trigger", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    const { rerender } = renderDialog(vi.fn(), false, "BOOKING", { open: false, onOpenChange });

    expect(screen.queryByRole("button", { name: "booking:bookings.actions.cancel" })).not.toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();

    rerender(
      <DeleteBookingDialog
        bookingId={41}
        bookingVersion={0}
        itemName="Confocal microscope"
        period="08:00–09:00"
        token="token"
        open
        onOpenChange={onOpenChange}
        onDeleted={vi.fn()}
      />,
    );
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "booking:bookings.cancelDialog.keep" }));

    expect(onOpenChange).toHaveBeenLastCalledWith(false);
  });

  it("clears the reason whenever an uncontrolled dialog is reopened", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));
    const reason = screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" });
    await user.type(reason, "Do not keep this");
    await user.click(screen.getByRole("button", { name: "booking:bookings.cancelDialog.keep" }));
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.cancel" }));

    expect(screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" })).toHaveValue("");
  });

  it("clears the reason on a controlled closed-to-open transition", async () => {
    const user = userEvent.setup();
    const { rerender } = renderDialog(vi.fn(), false, "BOOKING", { open: false });
    const props = {
      bookingId: 41,
      bookingVersion: 0,
      itemName: "Confocal microscope",
      period: "08:00–09:00",
      token: "token",
      onDeleted: vi.fn(),
      onOpenChange: vi.fn(),
    } satisfies ComponentProps<typeof DeleteBookingDialog>;

    rerender(<DeleteBookingDialog {...props} open />);
    const reason = await screen.findByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" });
    await user.type(reason, "Do not keep this");
    rerender(<DeleteBookingDialog {...props} open={false} />);
    rerender(<DeleteBookingDialog {...props} open />);

    expect(screen.getByRole("textbox", { name: "booking:bookings.cancelDialog.reasonLabel" })).toHaveValue("");
  });
});
