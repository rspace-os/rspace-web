import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { CalendarSubscriptionPopover } from "../CalendarSubscriptionPopover";

const path = "/api/v2/booking-configurations/7/calendar-subscription";
const updatedAt = "2026-08-27T12:00:00.000Z";

function urlFor(character: string): string {
  return `https://rspace.example/public/booking/calendars/feed.ics?token=${character.repeat(43)}`;
}

function renderPopover(archived = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return render(<CalendarSubscriptionPopover configurationId={7} token="oauth" archived={archived} />, {
    wrapper: Wrapper,
  });
}

describe("CalendarSubscriptionPopover", () => {
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
        return HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        );
      }),
      http.post(path, () => {
        posts += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("a") },
          { headers: { ETag: '"current"' } },
        );
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
      http.get(path, () =>
        HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("b") },
          { headers: { ETag: '"current"' } },
        ),
      ),
      http.post(path, () => {
        posts += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("c") },
          { headers: { ETag: '"current"' } },
        );
      }),
    );
    renderPopover();

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));

    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("b"));
    expect(posts).toBe(0);
  });

  it("does not generate a missing link while archived", async () => {
    const user = userEvent.setup();
    let posts = 0;
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
      ),
      http.post(path, () => {
        posts += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("z") },
          { headers: { ETag: '"current"' } },
        );
      }),
    );
    renderPopover(true);

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));

    expect(
      await screen.findByText("booking:bookableItemDetails.calendarSubscription.archivedUnavailable"),
    ).toBeVisible();
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
          : HttpResponse.json(
              { active: false, updatedAt: null, subscriptionUrl: null },
              { headers: { ETag: '"inactive"' } },
            );
      }),
      http.post(path, () => {
        posts += 1;
        return posts === 1
          ? HttpResponse.json({ status: 503 }, { status: 503 })
          : HttpResponse.json(
              { active: true, updatedAt, subscriptionUrl: urlFor("d") },
              { headers: { ETag: '"current"' } },
            );
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

  it("creates without a precondition, so a repeated create shows the same link", async () => {
    const user = userEvent.setup();
    let posts = 0;
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        ),
      ),
      http.post(path, ({ request }) => {
        expect(request.headers.get("If-Match")).toBeNull();
        posts += 1;
        // Another tab created the link first, so the server returns it unchanged.
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("w") },
          { status: 200, headers: { ETag: '"winner"' } },
        );
      }),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("w"));
    expect(posts).toBe(1);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("replaces the link only after confirmation", async () => {
    const user = userEvent.setup();
    const rotations: (string | null)[] = [];
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("b") },
          { headers: { ETag: '"current"' } },
        ),
      ),
      http.post(`${path}/rotate`, ({ request }) => {
        rotations.push(request.headers.get("If-Match"));
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("r") },
          { headers: { ETag: '"rotated"' } },
        );
      }),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replace" }),
    );

    expect(screen.getByText("booking:bookableItemDetails.calendarSubscription.replaceWarning")).toBeVisible();
    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));
    expect(rotations).toEqual([]);

    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replace" }));
    await user.click(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replaceConfirm" }),
    );

    await waitFor(() =>
      expect(
        screen.getByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
      ).toHaveValue(urlFor("r")),
    );
    expect(rotations).toEqual(['"current"']);
  });

  it("shows the current link when a replace conflicts", async () => {
    const user = userEvent.setup();
    let gets = 0;
    server.use(
      http.get(path, () => {
        gets += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor(gets === 1 ? "b" : "x") },
          { headers: { ETag: gets === 1 ? '"stale"' : '"elsewhere"' } },
        );
      }),
      http.post(`${path}/rotate`, () =>
        HttpResponse.json({ status: 409, code: "errors.api.v2.bookingCalendar.subscriptionConflict" }, { status: 409 }),
      ),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replace" }),
    );
    await user.click(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replaceConfirm" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "booking:bookableItemDetails.calendarSubscription.replaceConflict",
    );
    await waitFor(() =>
      expect(
        screen.getByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
      ).toHaveValue(urlFor("x")),
    );
  });

  it("offers to add the item again after a replace finds the link removed elsewhere", async () => {
    const user = userEvent.setup();
    let active = true;
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          active
            ? { active: true, updatedAt, subscriptionUrl: urlFor("b") }
            : { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: active ? '"current"' : '"inactive"' } },
        ),
      ),
      http.post(`${path}/rotate`, () => {
        // Another tab disconnected the link before this replace arrived.
        active = false;
        return HttpResponse.json(
          { status: 409, code: "errors.api.v2.bookingCalendar.subscriptionConflict" },
          { status: 409 },
        );
      }),
      http.post(path, () => {
        active = true;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("n") },
          { status: 201, headers: { ETag: '"new"' } },
        );
      }),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replace" }),
    );
    await user.click(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.replaceConfirm" }),
    );

    expect(await screen.findByText("booking:bookableItemDetails.calendarSubscription.disconnected")).toBeVisible();
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "booking:bookableItemDetails.calendarSubscription.trigger",
      }),
    );

    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("n"));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("disconnects the link and can add the item again", async () => {
    const user = userEvent.setup();
    let active = true;
    let posts = 0;
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          active
            ? { active: true, updatedAt, subscriptionUrl: urlFor("b") }
            : { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: active ? '"current"' : '"inactive"' } },
        ),
      ),
      http.delete(path, () => {
        active = false;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post(path, () => {
        posts += 1;
        active = true;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("n") },
          { status: 201, headers: { ETag: '"new"' } },
        );
      }),
    );
    renderPopover();
    await user.click(screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }));
    await user.click(
      await screen.findByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.disconnect" }),
    );
    // Disconnecting breaks subscribed calendars, so it waits for confirmation.
    expect(screen.getByText("booking:bookableItemDetails.calendarSubscription.disconnectWarning")).toBeVisible();
    expect(screen.queryByText("booking:bookableItemDetails.calendarSubscription.disconnected")).not.toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.disconnectConfirm" }),
    );

    expect(await screen.findByText("booking:bookableItemDetails.calendarSubscription.disconnected")).toBeVisible();
    expect(posts).toBe(0);
    const dialog = screen.getByRole("dialog");
    await user.click(
      within(dialog).getByRole("button", { name: "booking:bookableItemDetails.calendarSubscription.trigger" }),
    );
    expect(
      await screen.findByRole("textbox", { name: "booking:bookableItemDetails.calendarSubscription.copyPrompt" }),
    ).toHaveValue(urlFor("n"));
    expect(posts).toBe(1);
  });

  it("copies the link and reports a clipboard failure without moving focus", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("e") },
          { headers: { ETag: '"current"' } },
        ),
      ),
    );
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
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("f") },
          { headers: { ETag: '"current"' } },
        ),
      ),
    );
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
