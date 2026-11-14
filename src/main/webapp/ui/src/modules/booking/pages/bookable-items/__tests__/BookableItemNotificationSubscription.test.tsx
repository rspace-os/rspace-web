import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { Suspense } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { currentUser } from "@/__tests__/helpers/currentUserFixture";
import { server } from "@/__tests__/mswServer";
import { getGetMyBookingNotificationSubscriptionQueryKey } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { ApiV2Problem } from "@/modules/common/api/v2/generated/models/apiV2Problem";
import { BookableItemNotificationSubscription } from "../BookableItemNotificationSubscription";

function response<T extends { version: number }>(subscription: T) {
  const { version, ...body } = subscription;
  return HttpResponse.json(body, { headers: { ETag: `"v${version}-110"` } });
}
function cached<T extends { version: number }>(subscription: T) {
  const { version, ...body } = subscription;
  return { body, headers: new Headers({ ETag: `"v${version}-110"` }) };
}

function renderSubscription() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    ...render(
      <QueryClientProvider client={queryClient}>
        <Suspense fallback={null}>
          <BookableItemNotificationSubscription configurationId={7} globalId="IN7" subjectId={currentUser.id} />
        </Suspense>
      </QueryClientProvider>,
    ),
    queryClient,
  };
}

describe("BookableItemNotificationSubscription", () => {
  it("announces a saved setting through the live region mounted before the mutation", async () => {
    const user = userEvent.setup();
    let subscription = {
      configurationId: 7,
      enabled: false,
      version: 1,
      createdEnabled: true,
      cancelledEnabled: true,
      emailEnabled: false,
    };
    server.use(
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => response(subscription)),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        const body = (await request.json()) as { enabled: boolean };
        subscription = { ...subscription, enabled: body.enabled, version: subscription.version + 1 };
        return response(subscription);
      }),
    );
    const { container } = renderSubscription();

    const on = await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" });
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();
    expect(status).toHaveClass("sr-only");
    await user.click(on);
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveAttribute("aria-disabled", "true");
    expect(savedButton).toHaveFocus();
    expect(status).toHaveTextContent("booking:preferences.actions.saved");
    expect(screen.getByRole("status")).toBe(status);
    await expectAccessible(container);
  });

  it("syncs clean refreshes while preserving an edited choice and focus through save", async () => {
    const user = userEvent.setup();
    let subscription = {
      configurationId: 7,
      enabled: false,
      version: 1,
      createdEnabled: true,
      cancelledEnabled: true,
      emailEnabled: false,
    };
    let saved: unknown;
    server.use(
      http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
      http.get("/api/v2/booking-configurations/7/notification-subscription", () => response(subscription)),
      http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
        saved = await request.json();
        const body = saved as { enabled: boolean };
        subscription = { ...subscription, enabled: body.enabled, version: subscription.version + 1 };
        return response(subscription);
      }),
    );
    const { queryClient } = renderSubscription();
    const queryKey = getGetMyBookingNotificationSubscriptionQueryKey(7);
    const on = await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" });
    const off = screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.off" });

    act(() => {
      queryClient.setQueryData(queryKey, cached({ ...subscription, enabled: true, version: 2 }));
    });
    await waitFor(() => expect(on).toBeChecked());

    await user.click(off);
    act(() => {
      queryClient.setQueryData(queryKey, cached({ ...subscription, enabled: true, version: 3 }));
    });
    await waitFor(() => expect(off).toBeChecked());
    await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));

    const savedButton = await screen.findByRole("button", { name: "booking:preferences.actions.saved" });
    expect(savedButton).toHaveFocus();
    expect(saved).toEqual({ enabled: false });
    expect(on).not.toBeChecked();
  });
});

it("resets a conflicted switch to the server value and version", async () => {
  const user = userEvent.setup();
  let subscription = {
    configurationId: 7,
    enabled: false,
    version: 1,
    createdEnabled: true,
    cancelledEnabled: true,
    emailEnabled: false,
  };
  const versions: (string | null)[] = [];
  server.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    http.get("/api/v2/booking-configurations/7/notification-subscription", () => response(subscription)),
    http.put("/api/v2/booking-configurations/7/notification-subscription", async ({ request }) => {
      versions.push(request.headers.get("If-Match"));
      subscription = { ...subscription, version: 3 };
      return HttpResponse.json<ApiV2Problem>(
        { title: "Request failed", code: "errors.api.v2.unexpected", status: 412 },
        { status: 412 },
      );
    }),
  );
  const { queryClient } = renderSubscription();
  await user.click(await screen.findByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
  act(() =>
    queryClient.setQueryData(
      getGetMyBookingNotificationSubscriptionQueryKey(7),
      cached({
        ...subscription,
        version: 2,
      }),
    ),
  );
  await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));
  await screen.findByText("booking:notificationSubscriptions.item.conflict");
  await waitFor(() =>
    expect(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.off" })).toBeChecked(),
  );
  expect(versions).toEqual(['"v1-110"']);
  await user.click(screen.getByRole("radio", { name: "booking:notificationSubscriptions.options.on" }));
  await user.click(screen.getByRole("button", { name: "booking:preferences.actions.save" }));
  await waitFor(() => expect(versions).toEqual(['"v1-110"', '"v3-110"']));
});
