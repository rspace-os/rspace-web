import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { useCanOpenBookableItemsAdministration } from "../bookableItemsAdministrationAccess";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "token" }),
}));
vi.mock("@/modules/common/queries/currentUser", () => ({
  useCurrentUserQuery: () => ({ data: { id: 1 } }),
}));

it("retains administration for configured owners when lists omit totals and no setup targets remain", async () => {
  server.use(
    http.get("/api/v2/booking-configurations", () => HttpResponse.json({ docs: [] })),
    http.get("/api/v2/booking-configurations/count", ({ request }) => {
      expect(new URL(request.url).searchParams.get("max")).toBe("1");
      return HttpResponse.json({ totalDocs: 1, totalIsLowerBound: true });
    }),
    http.get("/api/v2/booking-configuration-targets", () => HttpResponse.json({ docs: [] })),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const { result } = renderHook(useCanOpenBookableItemsAdministration, {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
  await waitFor(() => expect(result.current).toBe(true));
});
