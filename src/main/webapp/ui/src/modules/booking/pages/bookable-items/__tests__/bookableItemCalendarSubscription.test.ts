import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ApiV2ProblemError } from "@/modules/booking/domain/booking";
import {
  calendarApplicationUrls,
  createOrReplaceCalendarSubscription,
  createOrReplaceUserCalendarSubscription,
  fetchCalendarSubscriptionStatus,
  fetchUserCalendarSubscriptionStatus,
  revokeCalendarSubscription,
  revokeUserCalendarSubscription,
  toWebcalUrl,
} from "../bookableItemCalendarSubscription";

const path = "/api/v2/booking-configurations/7/calendar-subscription";
const timestamp = "2026-08-27T12:00:00.000Z";

describe("bookable item calendar subscription client", () => {
  it("gets and validates inactive and active status with the current URL", async () => {
    let request: Request | undefined;
    server.use(
      http.get(path, ({ request: received }) => {
        request = received;
        return HttpResponse.json({ active: false, updatedAt: null });
      }),
    );

    await expect(fetchCalendarSubscriptionStatus(7, "secret", new AbortController().signal)).resolves.toEqual({
      active: false,
      updatedAt: null,
      subscriptionUrl: null,
    });
    expect(request?.method).toBe("GET");
    expect(request?.headers.get("Authorization")).toBe("Bearer secret");

    const subscriptionUrl = "https://example.test/feed.ics?token=current";
    server.use(http.get(path, () => HttpResponse.json({ active: true, updatedAt: timestamp, subscriptionUrl })));
    const active = await fetchCalendarSubscriptionStatus(7, "secret");
    expect(active).toEqual({ active: true, updatedAt: timestamp, subscriptionUrl });
  });

  it("rejects status shapes that violate the active timestamp contract", async () => {
    server.use(http.get(path, () => HttpResponse.json({ active: false, updatedAt: timestamp, subscriptionUrl: null })));
    await expect(fetchCalendarSubscriptionStatus(7, "token")).rejects.toThrow();

    server.use(http.get(path, () => HttpResponse.json({ active: true, updatedAt: null, subscriptionUrl: null })));
    await expect(fetchCalendarSubscriptionStatus(7, "token")).rejects.toThrow();

    server.use(
      http.get(path, () =>
        HttpResponse.json({ active: true, updatedAt: timestamp, subscriptionUrl: "javascript:alert(1)" }),
      ),
    );
    await expect(fetchCalendarSubscriptionStatus(7, "token")).rejects.toThrow();
  });

  it("creates or replaces and accepts HTTP or HTTPS context-path URLs", async () => {
    const bodies: unknown[] = [];
    server.use(
      http.post(path, async ({ request }) => {
        bodies.push(await request.text());
        return HttpResponse.json({
          active: true,
          updatedAt: timestamp,
          subscriptionUrl: "http://localhost:8097/rspace/public/booking/calendars/feed.ics?token=value",
        });
      }),
    );

    await expect(createOrReplaceCalendarSubscription(7, "secret")).resolves.toMatchObject({ active: true });
    expect(bodies).toEqual([""]);
  });

  it("rejects malformed creation documents", async () => {
    server.use(
      http.post(path, () =>
        HttpResponse.json({ active: false, updatedAt: null, subscriptionUrl: "javascript:alert(1)" }),
      ),
    );
    await expect(createOrReplaceCalendarSubscription(7, "token")).rejects.toThrow();
  });

  it.each([401, 403, 404])("parses a %s API problem", async (status) => {
    server.use(
      http.get(path, () => HttpResponse.json({ status, code: `problem.${status}`, detail: "Safe detail" }, { status })),
    );
    const request = fetchCalendarSubscriptionStatus(7, "token");
    await expect(request).rejects.toEqual(expect.any(ApiV2ProblemError));
    await expect(request).rejects.toMatchObject({ status, code: `problem.${status}` });
  });

  it("revokes only on a 204 response and does not parse a body", async () => {
    let method = "";
    server.use(
      http.delete(path, ({ request }) => {
        method = request.method;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    await expect(revokeCalendarSubscription(7, "token")).resolves.toBeUndefined();
    expect(method).toBe("DELETE");

    server.use(http.delete(path, () => HttpResponse.json({ active: false }, { status: 200 })));
    await expect(revokeCalendarSubscription(7, "token")).rejects.toThrow();
  });
});

describe("calendar application URLs", () => {
  it("converts only the HTTP scheme and encodes complete application URLs", () => {
    const feed = "https://rspace.example/context/public/booking/calendars/feed.ics?token=a_b-c";
    const webcal = "webcal://rspace.example/context/public/booking/calendars/feed.ics?token=a_b-c";
    expect(toWebcalUrl(feed)).toBe(webcal);
    expect(toWebcalUrl(feed.replace("https://", "http://"))).toBe(webcal);
    expect(() => toWebcalUrl("ftp://rspace.example/feed.ics")).toThrow();
    expect(calendarApplicationUrls(feed)).toEqual({
      apple: webcal,
      google: `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(webcal)}`,
      other: webcal,
    });
  });
});

describe("user booking calendar subscription client", () => {
  const userPath = "/api/v2/users/me/booking-calendar-subscription";

  it("uses the user-scoped endpoint for status, creation, and revocation", async () => {
    const methods: string[] = [];
    server.use(
      http.get(userPath, ({ request }) => {
        methods.push(request.method);
        return HttpResponse.json(
          { active: false, updatedAt: null, subscriptionUrl: null },
          { headers: { ETag: '"inactive"' } },
        );
      }),
      http.post(userPath, ({ request }) => {
        methods.push(request.method);
        expect(request.headers.get("Authorization")).toBe("Bearer secret");
        expect(request.headers.get("If-Match")).toBe('"inactive"');
        return HttpResponse.json(
          {
            active: true,
            updatedAt: timestamp,
            subscriptionUrl: "https://example.test/feed.ics?token=user",
          },
          { headers: { ETag: '"subscription-0"' } },
        );
      }),
      http.delete(userPath, ({ request }) => {
        methods.push(request.method);
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await expect(fetchUserCalendarSubscriptionStatus("secret")).resolves.toMatchObject({
      active: false,
      etag: '"inactive"',
    });
    await expect(createOrReplaceUserCalendarSubscription("secret", '"inactive"')).resolves.toMatchObject({
      active: true,
      etag: '"subscription-0"',
    });
    await expect(revokeUserCalendarSubscription("secret")).resolves.toBeUndefined();
    expect(methods).toEqual(["GET", "POST", "DELETE"]);
  });
});
