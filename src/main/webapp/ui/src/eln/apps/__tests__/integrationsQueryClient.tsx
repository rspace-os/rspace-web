import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type RenderOptions, render } from "@testing-library/react";
import type React from "react";
import { integrationsQueryKeys } from "../queries";
import type { Integration, IntegrationStates } from "../useIntegrationsEndpoint";

/**
 * A fresh query client holding `states` as if the Apps page had loaded them.
 * A card only reads its own integration, so tests need only provide that one.
 */
export function createIntegrationsClient(states: Partial<IntegrationStates>): QueryClient {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData(integrationsQueryKeys.all, states as IntegrationStates);
  return queryClient;
}

/** The state of `integration` in the cache. */
export function cachedState<I extends Integration>(queryClient: QueryClient, integration: I): IntegrationStates[I] {
  const states = queryClient.getQueryData<IntegrationStates>(integrationsQueryKeys.all);
  if (!states) throw new Error("The integrations are not in the cache");
  return states[integration];
}

export function WithIntegrations({
  client,
  children,
}: {
  client: QueryClient;
  children: React.ReactNode;
}): React.ReactNode {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

/**
 * Renders a card with `states` in the integrations query, returning the
 * query client so that tests can inspect the cache.
 */
export function renderWithIntegrations(
  ui: React.ReactElement,
  states: Partial<IntegrationStates>,
  options?: Omit<RenderOptions, "wrapper">,
) {
  const queryClient = createIntegrationsClient(states);
  const view = render(ui, {
    ...options,
    wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations>,
  });
  return { ...view, queryClient };
}
