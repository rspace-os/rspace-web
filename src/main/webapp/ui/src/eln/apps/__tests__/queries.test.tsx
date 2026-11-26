import { act, render, renderHook, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import type React from "react";
import { beforeEach, describe, expect, test } from "vitest";
import axios from "@/common/axios";
import { Optional } from "../../../util/optional";
import { integrationsQueryKeys, useIntegrationState, useUpdateIntegrationMutation } from "../queries";
import type { FetchedState, IntegrationStates } from "../useIntegrationsEndpoint";
import allIntegrationsAreDisabled from "./allIntegrationsAreDisabled.json";
import { cachedState, createIntegrationsClient, WithIntegrations } from "./integrationsQueryClient";

const mockAxios = new MockAdapter(axios);

const zenodo = (token: string): IntegrationStates["ZENODO"] => ({
  mode: "DISABLED",
  credentials: { ZENODO_USER_TOKEN: Optional.present(token) },
});

const zenodoResponse = (token: string): FetchedState => ({
  name: "ZENODO",
  available: true,
  enabled: false,
  options: { ZENODO_USER_TOKEN: token },
});

/** Replies to each POST to /update only when the test calls the returned function for it. */
function deferUpdates(): Array<() => void> {
  const replies: Array<() => void> = [];
  mockAxios.onPost("integration/update").reply(
    (config) =>
      new Promise((resolve) => {
        const posted = JSON.parse(config.data as string) as FetchedState;
        replies.push(() => resolve([200, { success: true, data: posted }]));
      }),
  );
  return replies;
}

function allIntegrationsGets(): number {
  return mockAxios.history.get.filter(({ url }) => url === "allIntegrations").length;
}

/** Replies to GET /allIntegrations with this Zenodo token, only when the test calls the returned function. */
function deferRefetch(token: string): () => void {
  let finish: () => void = () => {
    throw new Error("No refetch has been requested");
  };
  mockAxios.onGet("integration/allIntegrations").reply(
    () =>
      new Promise((resolve) => {
        finish = () =>
          resolve([
            200,
            { success: true, data: { ...allIntegrationsAreDisabled.data, ZENODO: zenodoResponse(token) }, error: null },
          ]);
      }),
  );
  return () => finish();
}

describe("Apps page queries", () => {
  beforeEach(() => {
    mockAxios.reset();
  });

  test("A change made alone writes its response into the cache without refetching.", async () => {
    const replies = deferUpdates();
    const queryClient = createIntegrationsClient({ ZENODO: zenodo("") });
    const { result } = renderHook(() => useUpdateIntegrationMutation("ZENODO"), {
      wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations>,
    });

    const saving = result.current.mutateAsync(zenodo("only"));
    await waitFor(() => expect(replies).toHaveLength(1));
    replies[0]();
    await saving;

    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("only"));
    expect(allIntegrationsGets()).toBe(0);
  });

  test("A response that arrives after a newer one does not overwrite it; the integrations are refetched instead.", async () => {
    const replies = deferUpdates();
    mockAxios.onGet("integration/allIntegrations").reply(200, {
      success: true,
      data: { ...allIntegrationsAreDisabled.data, ZENODO: zenodoResponse("newer") },
      error: null,
    });
    const queryClient = createIntegrationsClient({ ZENODO: zenodo("") });
    const tokensWritten: Array<unknown> = [];
    queryClient.getQueryCache().subscribe(() => {
      const states = queryClient.getQueryData<IntegrationStates>(integrationsQueryKeys.all);
      tokensWritten.push(states?.ZENODO.credentials.ZENODO_USER_TOKEN.orElse(null));
    });
    const { result } = renderHook(
      () => {
        // the Apps page always observes the integrations, so that a refetch has somewhere to go
        useIntegrationState("ZENODO");
        return useUpdateIntegrationMutation("ZENODO");
      },
      { wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations> },
    );

    const older = result.current.mutateAsync(zenodo("older"));
    const newer = result.current.mutateAsync(zenodo("newer"));
    await waitFor(() => expect(replies).toHaveLength(2));
    replies[1]();
    // overlapping changes settle only once they are reconciled, so wait for the cache rather than the change
    await waitFor(() =>
      expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("newer")),
    );

    replies[0]();
    await Promise.all([older, newer]);
    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("newer"));
    await waitFor(() => expect(allIntegrationsGets()).toBe(1));
    await waitFor(() => expect(queryClient.isFetching()).toBe(0));

    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("newer"));
    expect(tokensWritten).not.toContain("older");
  });

  test("Overlapping changes are shown as they arrive, then reconciled with the server, which may have applied them in another order.", async () => {
    const replies = deferUpdates();
    // the server applied the second change before the first, so the first is its latest state
    const finishRefetch = deferRefetch("first");
    const queryClient = createIntegrationsClient({ ZENODO: zenodo("") });
    const { result } = renderHook(
      () => {
        useIntegrationState("ZENODO");
        return useUpdateIntegrationMutation("ZENODO");
      },
      { wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations> },
    );

    const first = result.current.mutateAsync(zenodo("first"));
    const second = result.current.mutateAsync(zenodo("second"));
    await waitFor(() => expect(replies).toHaveLength(2));
    replies[0]();
    await waitFor(() =>
      expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("first")),
    );
    replies[1]();
    await waitFor(() =>
      expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("second")),
    );
    await waitFor(() => expect(allIntegrationsGets()).toBe(1));

    act(() => finishRefetch());
    await Promise.all([first, second]);
    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("first"));
  });

  test("A change discarded while another is still in flight settles only after the reconciling refetch.", async () => {
    const replies = deferUpdates();
    const finishRefetch = deferRefetch("server");
    const queryClient = createIntegrationsClient({ ZENODO: zenodo("") });
    const { result } = renderHook(
      () => {
        useIntegrationState("ZENODO");
        return useUpdateIntegrationMutation("ZENODO");
      },
      { wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations> },
    );

    let aSettled = false;
    const a = result.current.mutateAsync(zenodo("a")).then(() => {
      aSettled = true;
    });
    const b = result.current.mutateAsync(zenodo("b"));
    const c = result.current.mutateAsync(zenodo("c"));
    await waitFor(() => expect(replies).toHaveLength(3));
    replies[1]();
    replies[0]();
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(aSettled).toBe(false);

    replies[2]();
    await waitFor(() => expect(allIntegrationsGets()).toBe(1));
    expect(aSettled).toBe(false);
    act(() => finishRefetch());
    await Promise.all([a, b, c]);
    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("server"));
  });

  test("A discarded change settles only once the refetch it triggers has finished.", async () => {
    const replies = deferUpdates();
    let finishRefetch!: () => void;
    mockAxios.onGet("integration/allIntegrations").reply(
      () =>
        new Promise((resolve) => {
          finishRefetch = () =>
            resolve([
              200,
              {
                success: true,
                data: { ...allIntegrationsAreDisabled.data, ZENODO: zenodoResponse("server") },
                error: null,
              },
            ]);
        }),
    );
    const queryClient = createIntegrationsClient({ ZENODO: zenodo("") });
    const { result } = renderHook(
      () => {
        useIntegrationState("ZENODO");
        return useUpdateIntegrationMutation("ZENODO");
      },
      { wrapper: ({ children }) => <WithIntegrations client={queryClient}>{children}</WithIntegrations> },
    );

    let olderSettled = false;
    const older = result.current.mutateAsync(zenodo("older")).then(() => {
      olderSettled = true;
    });
    const newer = result.current.mutateAsync(zenodo("newer"));
    await waitFor(() => expect(replies).toHaveLength(2));
    replies[1]();
    await waitFor(() =>
      expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("newer")),
    );
    replies[0]();
    await waitFor(() => expect(allIntegrationsGets()).toBe(1));
    expect(olderSettled).toBe(false);

    act(() => finishRefetch());
    await Promise.all([older, newer]);
    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("server"));
  });

  test("A change to one integration does not re-render a card that shows another.", async () => {
    const replies = deferUpdates();
    const queryClient = createIntegrationsClient({
      ZENODO: zenodo(""),
      FIELDMARK: { mode: "DISABLED", credentials: { FIELDMARK_USER_TOKEN: Optional.empty() } },
    });
    let fieldmarkRenders = 0;
    function FieldmarkProbe(): React.ReactNode {
      useIntegrationState("FIELDMARK");
      fieldmarkRenders++;
      return null;
    }
    let update!: ReturnType<typeof useUpdateIntegrationMutation<"ZENODO">>["mutateAsync"];
    function ZenodoProbe(): React.ReactNode {
      useIntegrationState("ZENODO");
      update = useUpdateIntegrationMutation("ZENODO").mutateAsync;
      return null;
    }
    render(
      <WithIntegrations client={queryClient}>
        <FieldmarkProbe />
        <ZenodoProbe />
      </WithIntegrations>,
    );
    const rendersBefore = fieldmarkRenders;

    let saving!: Promise<unknown>;
    act(() => {
      saving = update(zenodo("key"));
    });
    await waitFor(() => expect(replies).toHaveLength(1));
    replies[0]();
    await act(() => saving);

    expect(cachedState(queryClient, "ZENODO").credentials.ZENODO_USER_TOKEN).toEqual(Optional.present("key"));
    expect(fieldmarkRenders).toBe(rendersBefore);
  });
});
