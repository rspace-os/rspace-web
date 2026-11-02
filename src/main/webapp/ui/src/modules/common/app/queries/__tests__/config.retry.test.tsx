import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import { DEFAULT_APP_CONFIG, useAppConfigQuery } from "@/modules/common/app/queries/config";

it("retries a failed configuration load and replaces the placeholder", async () => {
  let requests = 0;
  server.use(
    http.get("/api/v2/config", () => {
      requests += 1;
      return requests === 1
        ? new HttpResponse(null, { status: 500 })
        : HttpResponse.json({ ...DEFAULT_APP_CONFIG, version: "2.99.1" });
    }),
  );
  const restoreConsole = silenceConsole(["warn"], ["Could not read app configuration"]);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, retryDelay: 0 } } });
  function Consumer() {
    const { data } = useAppConfigQuery();
    return <output>{data?.version}</output>;
  }
  try {
    render(
      <QueryClientProvider client={queryClient}>
        <Consumer />
      </QueryClientProvider>,
    );
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
    expect(await screen.findByText("2.99.1")).toBeVisible();
    expect(requests).toBe(2);
  } finally {
    restoreConsole();
  }
});
