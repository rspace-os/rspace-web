import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getListBookingConfigurationsQueryKey } from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { getGetBookingSettingsQueryKey } from "@/modules/common/api/v2/generated/booking-settings/booking-settings";
import {
  getCountBookingsQueryKey,
  getGetBookingQueryKey,
  getListBookingsQueryKey,
  useUpdateBooking,
} from "@/modules/common/api/v2/generated/bookings/bookings";

it("invalidates booking lists, counts and the updated record without invalidating settings or configurations", async () => {
  server.use(http.patch("/api/v2/bookings/17", () => HttpResponse.json({ id: 17 })));
  const client = new QueryClient();
  const affected = [getListBookingsQueryKey({ page: 2 }), getCountBookingsQueryKey(), getGetBookingQueryKey(17)];
  const retained = [getGetBookingSettingsQueryKey(), getListBookingConfigurationsQueryKey(), getGetBookingQueryKey(18)];
  for (const key of [...affected, ...retained]) client.setQueryData(key, {});
  const { result } = renderHook(() => useUpdateBooking(), {
    wrapper: ({ children }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  });
  await act(async () => {
    await result.current.mutateAsync({ id: 17, data: { purpose: "Updated" }, headers: { "If-Match": '"1"' } });
  });
  for (const key of affected) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
  for (const key of retained) expect(client.getQueryState(key)?.isInvalidated).toBe(false);
});
