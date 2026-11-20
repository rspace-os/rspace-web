import { QueryClient } from "@tanstack/react-query";

/*
 * Each React island on a page creates its own QueryClient, so deployment
 * properties use this module-level client instead. Every island on the page
 * imports the same module chunk, so each property is fetched once per page
 * load and no caller needs a QueryClientProvider.
 *
 * This module imports nothing else so that the test setup files can reset the
 * client without loading the hook (and with it the real axios module) before
 * a test file's `vi.mock` calls take effect.
 */
export const deploymentPropertyQueryClient = new QueryClient({
  // a failed property has no data and so counts as stale; do not refetch it on every focus or reconnect
  defaultOptions: { queries: { refetchOnWindowFocus: false, refetchOnReconnect: false } },
});

export const deploymentPropertyQueryKeys = {
  all: ["rspace.deploymentProperty"] as const,
  property: (name: string) => [...deploymentPropertyQueryKeys.all, name] as const,
};
