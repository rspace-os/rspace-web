import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { userCalendarSubscriptionQueryKey } from "../../bookable-items/bookableItemCalendarSubscription";
import { UserCalendarSubscription } from "../UserCalendarSubscription";

const path = "/api/v2/users/me/booking-calendar-subscription";
const updatedAt = "2026-08-27T12:00:00.000Z";

function urlFor(character: string): string {
  return `https://rspace.example/public/booking/calendars/feed.ics?token=${character.repeat(43)}`;
}

function renderSubscription(includeOtherField = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <UserCalendarSubscription token="oauth" />
        {includeOtherField ? <input aria-label="Another preference" /> : null}
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("UserCalendarSubscription", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("keeps focus on another preference when creation finishes after the user moves away", async () => {
    const user = userEvent.setup();
    const response = Promise.withResolvers<Response>();
    server.use(
      http.get(path, () =>
        HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: null }, { headers: { ETag: '"empty"' } }),
      ),
      http.post(path, () => response.promise),
    );
    renderSubscription(true);
    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" }));
    const status = screen.getByRole("status");
    const otherField = screen.getByRole("textbox", { name: "Another preference" });
    await user.click(otherField);
    response.resolve(
      HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("n") }, { headers: { ETag: '"new"' } }),
    );
    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.ready"));
    expect(otherField).toHaveFocus();
  });

  it("announces a replace conflict as disconnected when its refresh confirms the link is gone", async () => {
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
        active = false;
        return HttpResponse.json(
          { status: 409, code: "errors.api.v2.bookingCalendar.subscriptionConflict" },
          { status: 409 },
        );
      }),
    );
    renderSubscription();

    await user.click(await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );

    const status = screen.getByRole("status");
    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.disconnected"));
    expect(screen.getByRole("status")).toBe(status);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.create" })).toHaveFocus(),
    );
    expect(
      screen
        .getAllByRole("alert")
        .filter((alert) => alert.textContent === "booking:preferences.calendarSubscription.replaceConflict"),
    ).toHaveLength(0);
  });

  it("keeps a rotated link when an older status request returns afterward", async () => {
    const user = userEvent.setup();
    const staleResponse = Promise.withResolvers<Response>();
    const staleReadStarted = Promise.withResolvers<void>();
    const staleReadReturned = Promise.withResolvers<void>();
    let gets = 0;
    server.use(
      http.get(path, async () => {
        gets += 1;
        if (gets === 2) {
          staleReadStarted.resolve();
          const response = await staleResponse.promise;
          staleReadReturned.resolve();
          return response;
        }
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("b") },
          { headers: { ETag: '"current"' } },
        );
      }),
      http.post(`${path}/rotate`, ({ request }) => {
        expect(request.headers.get("If-Match")).toBe('"current"');
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("r") },
          { headers: { ETag: '"rotated"' } },
        );
      }),
    );
    const { queryClient } = renderSubscription();
    const linkField = await screen.findByRole("textbox", {
      name: "booking:preferences.calendarSubscription.copyPrompt",
    });
    expect(linkField).toHaveValue(urlFor("b"));

    const statusRefresh = queryClient.refetchQueries({ queryKey: userCalendarSubscriptionQueryKey, exact: true });
    await staleReadStarted.promise;
    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );
    await waitFor(() => expect(linkField).toHaveValue(urlFor("r")));
    expect(screen.getByText("booking:preferences.calendarSubscription.replaced")).toBeVisible();
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "booking:preferences.calendarSubscription.google" })).toHaveFocus(),
    );

    staleResponse.resolve(
      HttpResponse.json({ active: true, updatedAt, subscriptionUrl: urlFor("b") }, { headers: { ETag: '"current"' } }),
    );
    await Promise.all([statusRefresh, staleReadReturned.promise]);
    expect(queryClient.getQueryData(userCalendarSubscriptionQueryKey)).toMatchObject({
      subscriptionUrl: urlFor("r"),
      etag: '"rotated"',
    });
    expect(screen.getByRole("textbox", { name: "booking:preferences.calendarSubscription.copyPrompt" })).toHaveValue(
      urlFor("r"),
    );
  });

  it("announces and focuses each confirmed link change", async () => {
    const user = userEvent.setup();
    let active = false;
    let version = 0;
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          active
            ? { active: true, updatedAt, subscriptionUrl: urlFor(String.fromCharCode(97 + version)) }
            : { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: `"version-${version}"` } },
        ),
      ),
      http.post(path, () => {
        active = true;
        version += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor(String.fromCharCode(97 + version)) },
          { status: 201, headers: { ETag: `"version-${version}"` } },
        );
      }),
      http.post(`${path}/rotate`, () => {
        version += 1;
        return HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor(String.fromCharCode(97 + version)) },
          { headers: { ETag: `"version-${version}"` } },
        );
      }),
      http.delete(path, () => {
        active = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderSubscription();

    const create = await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.create" });
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();
    expect(status).toHaveClass("sr-only");
    await user.click(create);

    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.ready"));
    expect(screen.getByRole("status")).toBe(status);
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "booking:preferences.calendarSubscription.google" })).toHaveFocus(),
    );

    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replace" }));
    await user.click(
      screen.getByRole("button", { name: "booking:preferences.calendarSubscription.replaceDialog.confirm" }),
    );
    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.replaced"));
    expect(screen.getByRole("status")).toBe(status);
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "booking:preferences.calendarSubscription.google" })).toHaveFocus(),
    );

    await user.click(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.revoke" }));
    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.disconnected"));
    expect(screen.getByRole("status")).toBe(status);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "booking:preferences.calendarSubscription.create" })).toHaveFocus(),
    );
  });

  it("keeps copied and clipboard error messages in mounted live regions", async () => {
    const user = userEvent.setup();
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { active: true, updatedAt, subscriptionUrl: urlFor("b") },
          { headers: { ETag: '"current"' } },
        ),
      ),
    );
    renderSubscription();

    const copy = await screen.findByRole("button", { name: "booking:preferences.calendarSubscription.copy" });
    const status = screen.getByRole("status");
    const alert = screen.getByRole("alert");
    expect(status).toHaveClass("sr-only");
    const writeText = vi.spyOn(navigator.clipboard, "writeText");
    writeText.mockRejectedValueOnce(new Error("denied"));
    await user.click(copy);

    expect(await screen.findByText("booking:preferences.calendarSubscription.copyError")).toHaveAttribute(
      "role",
      "alert",
    );
    expect(screen.getByRole("alert")).toBe(alert);
    expect(screen.getByRole("status")).toBe(status);
    expect(copy).toHaveFocus();

    writeText.mockResolvedValueOnce(undefined);
    await user.click(copy);
    await waitFor(() => expect(status).toHaveTextContent("booking:preferences.calendarSubscription.copied"));
    expect(screen.getByRole("status")).toBe(status);
    expect(copy).toHaveFocus();
  });
});
