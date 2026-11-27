import {
  type QueryClient,
  queryOptions,
  type UseMutationResult,
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from "@tanstack/react-query";
import { mapValues } from "es-toolkit";
import { useCallback } from "react";
import { Optional } from "../../util/optional";
import type { Secret } from "../../util/secret";
import {
  fetchAllIntegrations,
  type Integration,
  type IntegrationState,
  type IntegrationStates,
  type OptionsId,
  useIntegrationsEndpoint,
} from "./useIntegrationsEndpoint";

/*
 * Server state for the Apps page. One query holds the state of every
 * integration; each card selects its own integration from it, so a change to
 * one integration re-renders only that card. The mutations below write their
 * results back into that query, taking care that a response which may be
 * older than one already written never overwrites it.
 */

export const integrationsQueryKeys = {
  all: ["rspace.apps.integrations"] as const,
};

/**
 * The query for the state of every integration. It is only refetched when a
 * mutation's result cannot be trusted to be the latest, so cards that remount
 * when they move between sections do not trigger requests.
 */
export function allIntegrationsQueryOptions() {
  return queryOptions({
    queryKey: integrationsQueryKeys.all,
    queryFn: fetchAllIntegrations,
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  });
}

export type IntegrationModes = { readonly [I in Integration]: IntegrationStates[I]["mode"] };

function selectModes(states: IntegrationStates): IntegrationModes {
  return mapValues(states, (state) => state.mode);
}

/**
 * The mode of every integration, for placing the cards into sections. It
 * only re-renders its caller when a mode changes.
 */
export function useIntegrationModesQuery() {
  return useQuery({ ...allIntegrationsQueryOptions(), select: selectModes });
}

/**
 * The current state of one integration. Suspends until the integrations have
 * loaded; the Apps page only renders cards once they have.
 */
export function useIntegrationState<I extends Integration>(integration: I): IntegrationStates[I] {
  const select = useCallback((states: IntegrationStates) => states[integration], [integration]);
  return useSuspenseQuery({ ...allIntegrationsQueryOptions(), select }).data;
}

type Apply<I extends Integration> = (current: IntegrationStates[I]) => IntegrationStates[I];

/*
 * Orders the changes to each integration. A change made on its own writes its
 * response into the cache and settles. When changes overlap, the server may
 * apply them in a different order from the one in which their responses
 * arrive, so no response can be trusted to be the latest: each response is
 * still shown as it arrives, unless a change that began later has already
 * written, and once the last overlapping change finishes the integrations are
 * refetched. Every overlapping change settles only after that refetch, so
 * callers (such as a pending-add guard) see the server's final state. A
 * response written whilst a refetch is in flight is reconciled the same way,
 * as that refetch may have been answered before the change was made.
 */
type ChangeTracker = {
  pending: number;
  started: number;
  lastWritten: number;
  // set when changes overlap, or a write raced a refetch; cleared by the reconciling refetch
  reconcile: boolean;
  // resolved once the reconciling refetch has finished
  reconciled: { promise: Promise<void>; resolve: () => void } | null;
};
const trackers = new WeakMap<QueryClient, Map<Integration, ChangeTracker>>();

function trackerFor(queryClient: QueryClient, integration: Integration): ChangeTracker {
  let byIntegration = trackers.get(queryClient);
  if (!byIntegration) {
    byIntegration = new Map();
    trackers.set(queryClient, byIntegration);
  }
  let tracker = byIntegration.get(integration);
  if (!tracker) {
    tracker = { pending: 0, started: 0, lastWritten: 0, reconcile: false, reconciled: null };
    byIntegration.set(integration, tracker);
  }
  return tracker;
}

/** Returns the change's position in the order in which changes to the integration began. */
function beginChange(queryClient: QueryClient, integration: Integration): number {
  const tracker = trackerFor(queryClient, integration);
  tracker.pending += 1;
  tracker.started += 1;
  if (tracker.pending > 1) tracker.reconcile = true;
  return tracker.started;
}

/** `apply` is null when the change failed, leaving nothing to write. */
function endChange<I extends Integration>(
  queryClient: QueryClient,
  integration: I,
  sequence: number,
  apply: Apply<I> | null,
): Promise<void> {
  const tracker = trackerFor(queryClient, integration);
  tracker.pending -= 1;
  if (apply) {
    if (sequence > tracker.lastWritten) {
      tracker.lastWritten = sequence;
      queryClient.setQueryData<IntegrationStates>(
        integrationsQueryKeys.all,
        (states) => states && { ...states, [integration]: apply(states[integration]) },
      );
      if (queryClient.isFetching({ queryKey: integrationsQueryKeys.all }) > 0) tracker.reconcile = true;
    } else {
      tracker.reconcile = true;
    }
  }
  if (!tracker.reconcile) return Promise.resolve();
  if (!tracker.reconciled) {
    let resolve!: () => void;
    const promise = new Promise<void>((r) => {
      resolve = r;
    });
    tracker.reconciled = { promise, resolve };
  }
  const { promise } = tracker.reconciled;
  if (tracker.pending === 0) {
    const { resolve } = tracker.reconciled;
    tracker.reconcile = false;
    tracker.reconciled = null;
    void queryClient.invalidateQueries({ queryKey: integrationsQueryKeys.all }).finally(resolve);
  }
  return promise;
}

/**
 * A mutation of one integration whose outcome is written into the
 * integrations query, ordered against every other change to that
 * integration as described above.
 *
 * @param mutationFn Makes the request.
 * @param apply      Derives the integration's new state from its current
 *                   state and the request's result.
 */
export function useIntegrationMutation<I extends Integration, TVariables, TResult>(
  integration: I,
  mutationFn: (variables: TVariables) => Promise<TResult>,
  apply: (current: IntegrationStates[I], result: TResult, variables: TVariables) => IntegrationStates[I],
): UseMutationResult<TResult, Error, TVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: [...integrationsQueryKeys.all, integration],
    mutationFn: async (variables: TVariables) => {
      const sequence = beginChange(queryClient, integration);
      let result: TResult;
      try {
        result = await mutationFn(variables);
      } catch (e) {
        await endChange(queryClient, integration, sequence, null);
        throw e;
      }
      await endChange(queryClient, integration, sequence, (current) => apply(current, result, variables));
      return result;
    },
  });
}

/**
 * Calls `/update` with a new state for the integration: its mode, and any
 * simple configuration such as an API key. `useIntegrationsEndpoint` shows the
 * success or error alert.
 */
export function useUpdateIntegrationMutation<I extends Integration>(integration: I) {
  const { update } = useIntegrationsEndpoint();
  return useIntegrationMutation(
    integration,
    (newState: IntegrationStates[I]) => update(integration, newState),
    (_current, saved) => saved,
  );
}

/**
 * Enables or disables the integration, keeping its credentials as they are
 * in the cache when called. Failures are shown as an alert by
 * `useIntegrationsEndpoint`.
 */
export function useIntegrationModeUpdate<I extends Integration>(
  integration: I,
): (mode: IntegrationStates[I]["mode"]) => void {
  const queryClient = useQueryClient();
  const { mutate } = useUpdateIntegrationMutation(integration);
  return (mode) => {
    const current = queryClient.getQueryData<IntegrationStates>(integrationsQueryKeys.all)?.[integration];
    if (current) mutate({ ...current, mode });
  };
}

/**
 * Calls `/saveAppOptions`, creating a configuration when `optionsId` is empty
 * or overwriting the one it names. The caller shows any alerts.
 */
export function useSaveAppOptionsMutation<I extends Integration>(integration: I) {
  const { saveAppOptions } = useIntegrationsEndpoint();
  return useIntegrationMutation(
    integration,
    ({ optionsId, option }: { optionsId: Optional<OptionsId>; option: object }) =>
      saveAppOptions(integration, optionsId, option),
    (_current, saved) => saved,
  );
}

/**
 * The integration's state without the configuration `optionsId`, for both
 * shapes of multi-configuration credentials: a list of configurations, or a
 * list of authenticated servers.
 */
function withoutConfiguration<S extends { credentials: unknown }>(state: S, optionsId: OptionsId): S {
  const { credentials } = state;
  if (Array.isArray(credentials)) {
    return {
      ...state,
      credentials: (credentials as Array<Optional<{ optionsId: OptionsId }>>).filter((config) =>
        config.map((c) => c.optionsId !== optionsId).orElse(true),
      ),
    };
  }
  if (typeof credentials === "object" && credentials !== null && "authenticatedServers" in credentials) {
    const servers = credentials.authenticatedServers as ReadonlyArray<{ optionsId: OptionsId }>;
    return {
      ...state,
      credentials: { ...credentials, authenticatedServers: servers.filter((s) => s.optionsId !== optionsId) },
    };
  }
  return state;
}

/**
 * Calls `/deleteAppOptions` and removes the configuration from the cache.
 * The caller shows any alerts.
 */
export function useDeleteAppOptionsMutation<I extends Integration>(integration: I) {
  const { deleteAppOptions } = useIntegrationsEndpoint();
  return useIntegrationMutation(
    integration,
    (optionsId: OptionsId) => deleteAppOptions(integration, optionsId),
    (current, _result, optionsId) => withoutConfiguration(current, optionsId),
  );
}

/**
 * Records a change that the server has already made, such as an OAuth
 * connection that another window has reported, ordered against the
 * mutations above.
 */
export function useRecordIntegrationChange<I extends Integration>(integration: I): (apply: Apply<I>) => void {
  const queryClient = useQueryClient();
  return (apply) => {
    void endChange(queryClient, integration, beginChange(queryClient, integration), apply);
  };
}

type TokenState = IntegrationState<{ ACCESS_TOKEN: Optional<Secret> }>;

type TokenIntegration = {
  [I in Integration]: IntegrationStates[I] extends TokenState ? I : never;
}[Integration];

function withToken<S extends TokenState>(state: S, connected: boolean): S {
  return {
    ...state,
    credentials: {
      ...state.credentials,
      // a stored token is never sent to the browser, so it is modelled as a stored secret
      ACCESS_TOKEN: connected ? Optional.present(null) : Optional.empty(),
    },
  };
}

/**
 * For integrations that the server connects to with a stored token: whether
 * the user is connected, and a way of recording that they have connected or
 * disconnected.
 */
export function useTokenConnection<I extends TokenIntegration>(
  integration: I,
): { connected: boolean; setConnected: (connected: boolean) => void } {
  const state: TokenState = useIntegrationState(integration);
  const record = useRecordIntegrationChange(integration);
  return {
    connected: state.credentials.ACCESS_TOKEN.isPresent(),
    setConnected: (connected) => record((current) => withToken(current, connected)),
  };
}
