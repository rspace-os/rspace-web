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
  // No QueryClientProvider mounts this client, so it never hears online events
  // and would never resume a request paused while the browser reports offline.
  // Send every request, as the plain axios call did, and let it fail instead.
  defaultOptions: { queries: { networkMode: "always" } },
});

export const deploymentPropertyQueryKeys = {
  all: ["rspace.deploymentProperty"] as const,
  property: (name: string) => [...deploymentPropertyQueryKeys.all, name] as const,
};
