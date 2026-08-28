import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { CalendarSubscriptionPopover } from "../CalendarSubscriptionPopover";

const path = "/api/v2/booking-configurations/7/calendar-subscription";
const updatedAt = "2026-08-27T12:00:00.000Z";

function urlFor(character: string): string {
  return `https://rspace.example/public/booking/calendars/feed.ics?token=${character.repeat(43)}`;
}

function renderPopover() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return render(<CalendarSubscriptionPopover configurationId={7} token="oauth" />, { wrapper: Wrapper });
}

describe("CalendarSubscriptionPopover", () => {
  beforeEach(() => {
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("automatically creates a missing link and shows the simplified calendar choices", async () => {
    const user = userEvent.setup();
    let gets = 0;
    let posts = 0;
    server.use(
      http.get(path, () => {
        gets += 1;
        return HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null });
      }),
      http.post(path, () => {
        posts += 1;
        return HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("a") });
      }),
    );
    const { container } = renderPopover();
    const trigger = screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" });

    expect(gets).toBe(0);
    await user.click(trigger);

    expect(
      await screen.findByRole("link", { name: "booking:bookableItemDetails.calendarSubscription.apple" }),
    ).toHaveAttribute("href", expect.stringMatching(/^webcal:/));
    expect(screen.getByRole("link", { name: "booking:bookableItemDetails.calendarSubscription.google" })).toHaveFocus();
    expect(
      screen.getByRole("link", { name: "booking:bookableItemDetails.calendarSubscription.other" }),
    ).toHaveAttribute("href", expect.stringMatching(/^webcal:/));
    const copyGroup = screen.getByRole("group", {
      name: "booking:bookableItemDetails.calendarSubscription.copyPrompt",
    });
    expect(within(copyGroup).getByRole("textbox")).toHaveValue(urlFor("a"));
    expect(
      within(copyGroup).getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.copy" }),
    ).toBeVisible();
    expect(gets).toBe(1);
    expect(posts).toBe(1);
    await expectAccessible(container);
  });

  it("shows an existing link without replacing it", async () => {
    const user = userEvent.setup();
    let posts = 0;
    server.use(
      http.get(path, () => HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("b") })),
      http.post(path, () => {
        posts += 1;
        return HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("c") });
      }),
    );
    renderPopover();

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));

    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("b"));
    expect(posts).toBe(0);
  });

  it("retries status and generation failures", async () => {
    const user = userEvent.setup();
    let gets = 0;
    let posts = 0;
    server.use(
      http.get(path, () => {
        gets += 1;
        return gets === 1
          ? HttpResponse.json({ status: 503 }, { status: 503 })
          : HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null });
      }),
      http.post(path, () => {
        posts += 1;
        return posts === 1
          ? HttpResponse.json({ status: 503 }, { status: 503 })
          : HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("d") });
      }),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "booking:bookableItemDetails.calendarSubscription.statusError",
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.retry" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "booking:bookableItemDetails.calendarSubscription.generateError",
    );
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.retry" }));
    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("d"));
  });

  it("copies the link and reports a clipboard failure without moving focus", async () => {
    const user = userEvent.setup();
    server.use(http.get(path, () => HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("e") })));
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    const copy = await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.copy" });

    await user.click(copy);
    expect(await screen.findByRole("status")).toHaveTextContent(
      "booking:bookableItemDetails.calendarSubscription.copied",
    );
    expect(copy).toHaveFocus();

    vi.spyOn(navigator.clipboard, "writeText").mockRejectedValueOnce(new Error("denied"));
    await user.click(copy);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "booking:bookableItemDetails.calendarSubscription.copyError",
    );
    expect(copy).toHaveFocus();
  });

  it("closes with Escape and restores focus to the trigger", async () => {
    const user = userEvent.setup();
    server.use(http.get(path, () => HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("f") })));
    renderPopover();
    const trigger = screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" });
    await user.click(trigger);
    const dialog = await screen.findByRole("dialog");
    await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" });

    await user.keyboard("{Escape}");

    expect(dialog).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
});
