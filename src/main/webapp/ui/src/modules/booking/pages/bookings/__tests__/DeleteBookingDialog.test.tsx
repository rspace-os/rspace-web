import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { DeleteBookingDialog } from "../DeleteBookingDialog";

const cancelledBooking = {
  id: 41,
  target: {
    relationTo: "instruments",
    value: { id: 12, name: "Scope", deleted: false },
    globalId: "IN12",
  },
  timezone: "Europe/Berlin",
  start: "2026-08-17T06:00:00Z",
  end: "2026-08-17T07:00:00Z",
  state: "CANCELLED",
  privacy: "full",
  purpose: null,
  bookedBy: "Ada Lovelace (ada)",
  canEdit: true,
  createdAt: "2026-08-17T00:00:00Z",
  updatedAt: "2026-08-17T00:00:00Z",
} as const;

function renderDialog(onDeleted = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  const result = render(
    <DeleteBookingDialog
      bookingId={41}
      itemName="Confocal microscope"
      period="08:00–09:00"
      token="token"
      onDeleted={onDeleted}
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

    const trigger = screen.getByRole("button", { name: "booking:bookings.actions.delete" });
    await user.click(trigger);
    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));

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
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.delete" }));
    const confirm = screen.getByRole("button", { name: "common:actions.delete" });
    const cancel = screen.getByRole("button", { name: "common:actions.cancel" });
    await user.click(confirm);

    expect(confirm).toBeDisabled();
    expect(confirm).toHaveAttribute("aria-busy", "true");
    expect(cancel).toBeDisabled();
    await user.click(confirm);
    expect(requests).toHaveLength(1);

    resolveRequest?.();
    await screen.findByRole("button", { name: "booking:bookings.actions.delete" });
    expect(await requests[0].json()).toEqual({ state: "CANCELLED" });
    expect(requests[0].method).toBe("PATCH");
    expect(requests[0].headers.get("Authorization")).toBe("Bearer token");
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings"] });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ["api-v2", "bookings", 41] });
    expect(onDeleted).toHaveBeenCalledOnce();
  });

  it.each([
    [403, "errors.api.v2.forbidden", "booking:bookings.errors.deleteForbidden"],
    [409, "errors.api.v2.booking.state.transition", "booking:bookings.errors.deleteStale"],
  ])("shows local text for a %s cancellation failure", async (status, code, message) => {
    const user = userEvent.setup();
    server.use(
      http.patch("/api/v2/bookings/41", () =>
        HttpResponse.json({ status, code, detail: "Do not display this" }, { status }),
      ),
    );
    renderDialog();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.delete" }));
    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("alert")).not.toHaveTextContent("Do not display this");
    expect(screen.getByRole("alertdialog")).toBeVisible();
  });

  it("keeps an accessible dialog open after an unknown failure", async () => {
    const user = userEvent.setup();
    server.use(http.patch("/api/v2/bookings/41", () => HttpResponse.error()));
    renderDialog();
    await user.click(screen.getByRole("button", { name: "booking:bookings.actions.delete" }));
    await expectAccessible(document.body);
    await user.click(screen.getByRole("button", { name: "common:actions.delete" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("booking:bookings.errors.deleteGeneric");
    expect(screen.getByRole("alertdialog")).toBeVisible();
  });
});
