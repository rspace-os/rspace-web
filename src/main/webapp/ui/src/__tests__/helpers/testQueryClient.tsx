import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";

/** A query client for one test: requests are not retried, so a failure shows straight away. */
export function createTestQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

/**
 * A `wrapper` for `render` or `renderHook` that provides `client`. Every rerender keeps the same client, so cached
 * queries survive it.
 */
export function queryClientWrapper(client: QueryClient = createTestQueryClient()) {
  return function QueryClientWrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}
