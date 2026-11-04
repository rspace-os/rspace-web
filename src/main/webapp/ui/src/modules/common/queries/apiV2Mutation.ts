import {
  type DefaultError,
  MutationCache,
  type QueryClient,
  type QueryKey,
  type UseMutationOptions,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";

declare module "@tanstack/react-query" {
  interface Register {
    mutationMeta: { invalidates?: readonly QueryKey[] };
    queryMeta: { contributesToNavigationProgress?: boolean; dataScope?: string | number };
  }
}

const automaticInvalidationCaches = new WeakSet<MutationCache>();

function invalidateQueries(client: QueryClient, keys: readonly QueryKey[] = []) {
  return Promise.all(keys.map((queryKey) => client.invalidateQueries({ queryKey })));
}

export function createApiV2MutationCache(getClient: () => QueryClient) {
  const cache = new MutationCache({
    onSuccess: (_data, _variables, _context, mutation) => invalidateQueries(getClient(), mutation.meta?.invalidates),
  });
  automaticInvalidationCaches.add(cache);
  return cache;
}

/** Shared components also run under standalone clients in stories and tests. */
export function useApiV2Mutation<TData, TError = DefaultError, TVariables = void, TOnMutateResult = unknown>(
  options: UseMutationOptions<TData, TError, TVariables, TOnMutateResult>,
) {
  const client = useQueryClient();
  return useMutation({
    ...options,
    onSuccess: async (...args) => {
      if (!automaticInvalidationCaches.has(client.getMutationCache())) {
        await invalidateQueries(client, options.meta?.invalidates);
      }
      return options.onSuccess?.(...args);
    },
  });
}
