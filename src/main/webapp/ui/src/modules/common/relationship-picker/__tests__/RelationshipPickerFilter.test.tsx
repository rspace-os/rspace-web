import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, renderHook, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveCollectionConfig";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import { useSelectedRelationshipOptions } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import {
  databaseIdFromGlobalId,
  type RelationshipSource,
  relationshipSources,
} from "@/modules/common/relationship-picker/relationshipSources";
import { FilterValueInput } from "@/modules/common/table-list/components/filters/FilterValueInput";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: () => ({ data: "test-token" }),
}));

function listInstruments(onRequest?: (url: URL) => void) {
  server.use(
    http.get("/api/v2/instruments", ({ request }) => {
      onRequest?.(new URL(request.url));
      return HttpResponse.json({
        docs: [{ id: 123, name: "Confocal microscope", globalId: "IN123" }],
        totalDocs: 1,
        limit: 20,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      });
    }),
  );
}

function renderPicker(onChange: (value: string) => void) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RelationshipPicker
        source={relationshipSources.instruments}
        value=""
        onChange={onChange}
        ariaLabel="Relationship"
      />
    </QueryClientProvider>,
  );
}

function MultiSourcePicker({ sources }: { sources: readonly RelationshipSource[] }) {
  const [value, setValue] = useState("");
  return <RelationshipPicker sources={sources} value={value} onChange={setValue} ariaLabel="Relationships" multiple />;
}

function fixtureSource(): RelationshipSource {
  return {
    id: "fixture-targets",
    globalIdPrefix: "FX",
    batchSize: 100,
    normalizeValue: (value) => {
      const match = /^FX(\d+)$/i.exec(value.trim());
      return match ? `FX${Number(match[1])}` : null;
    },
    search: async () => [],
    resolveMany: async (values, token, signal) => {
      const params = new URLSearchParams({ ids: values.join(",") });
      const response = await fetch(`/api/v2/relationship-fixtures?${params}`, {
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        signal,
      });
      if (!response.ok) throw new Error(`Fixture restore failed (${response.status})`);
      return (await response.json()) as Readonly<Record<string, unknown | null>>;
    },
    ownsValue: (value) => /^FX\d+$/i.test(value.trim()),
    toOption: (document) => {
      const target = document as { globalId: string; name: string };
      return { value: target.globalId, label: target.name };
    },
  };
}

function renderSelected(values: string, authScope = "caller-one", source = fixtureSource()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const view = render(
    <QueryClientProvider client={queryClient}>
      <RelationshipPicker
        sources={[source]}
        authScope={authScope}
        value={values}
        onChange={vi.fn()}
        ariaLabel="Selected relationships"
        multiple
      />
    </QueryClientProvider>,
  );
  return { ...view, queryClient };
}

describe("relationship picker search", () => {
  it("searches and merges multiple source collections without collapsing equal values", async () => {
    const user = userEvent.setup();
    const calls: string[] = [];
    const source = (id: string, label: string) => ({
      id,
      search: async (term: string) => {
        calls.push(`${id}:${term}`);
        return [{ id: 1, name: label }];
      },
      ownsValue: (value: string) => value === `${id}:1`,
      toOption: (document: unknown) => {
        const item = document as { name: string };
        return { value: `${id}:1`, label: item.name };
      },
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MultiSourcePicker sources={[source("users", "Alice"), source("groups", "Editors")]} />
      </QueryClientProvider>,
    );

    await user.type(screen.getByRole("combobox", { name: "Relationships" }), "al");
    expect(await screen.findByRole("option", { name: "Alice" })).toBeInTheDocument();
    expect(await screen.findByRole("option", { name: "Editors" })).toBeInTheDocument();
    expect(calls).toEqual(expect.arrayContaining(["users:al", "groups:al"]));

    await user.click(screen.getByRole("option", { name: "Alice" }));
    await user.type(screen.getByRole("combobox", { name: "Relationships" }), "al");
    await screen.findByRole("option", { name: "Editors" });
    await user.click(screen.getByRole("option", { name: "Editors" }));
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Editors")).toBeInTheDocument();
  });

  it("prompts for a search term before requesting options", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    listInstruments((url) => requests.push(url));
    renderPicker(vi.fn());

    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.openOptions" }));

    expect(await screen.findByText("common:relationshipPicker.enterSearchTerm")).toBeVisible();
    expect(requests).toHaveLength(0);
  });

  it("reports when a filtered search is still loading", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("/api/v2/instruments", async () => {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        return HttpResponse.json({ docs: [] });
      }),
    );
    renderPicker(vi.fn());

    await user.type(screen.getByRole("combobox", { name: "Relationship" }), "Conf");

    expect(await screen.findByText("common:loading")).toBeVisible();
  });

  it("searches by name and applies the selected global ID", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const requests: URL[] = [];
    listInstruments((url) => requests.push(url));
    renderPicker(onChange);

    await user.type(screen.getByRole("combobox", { name: "Relationship" }), "Conf");

    const option = await screen.findByRole("option", { name: /Confocal microscope/ });
    // The global ID is plain text: a link inside the listbox would navigate away mid-selection.
    expect(option).toHaveAccessibleName(/Confocal microscope.*IN123/);
    expect(within(option).queryByRole("link")).not.toBeInTheDocument();
    await user.click(option);
    expect(onChange).toHaveBeenCalledWith("IN123");
    expect(requests.map((url) => url.searchParams.get("where"))).toContain("name=contains=Conf");
    expect(requests[0]?.searchParams.get("fields[instruments]")).toBe("id,name,globalId");
    expect(requests[0]?.searchParams.get("limit")).toBe("20");
  });

  it("searches by global ID through the row ID", async () => {
    const user = userEvent.setup();
    const requests: URL[] = [];
    listInstruments((url) => requests.push(url));
    renderPicker(vi.fn());

    await user.type(screen.getByRole("combobox", { name: "Relationship" }), "IN123");

    expect(await screen.findByRole("option", { name: /Confocal microscope/ })).toBeInTheDocument();
    expect(requests.at(-1)?.searchParams.get("where")).toBe("id==123");
    // Seeded and baseline rows have negative IDs, such as US-3; zero and malformed IDs are rejected.
    expect(databaseIdFromGlobalId("us-3", "US")).toBe(-3);
    expect(["US0", "US--3", "IN3"].map((value) => databaseIdFromGlobalId(value, "US"))).toEqual([null, null, null]);
  });

  it("keeps support for disabling options when an availability source is supplied", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    listInstruments();
    render(
      <QueryClientProvider client={queryClient}>
        <RelationshipPicker
          source={relationshipSources.instruments}
          availabilitySource={{
            queryKey: ["test-relationship-option-availability"],
            loadUnavailable: async (values) =>
              Object.fromEntries(values.map((item) => [item, { reason: "test-unavailable" }])),
            renderUnavailable: () => "common:relationshipPicker.availabilityFailed",
          }}
          value=""
          onChange={onChange}
          ariaLabel="Relationship"
        />
      </QueryClientProvider>,
    );

    const picker = screen.getByRole("combobox", { name: "Relationship" });
    await user.type(picker, "Conf");

    const option = await screen.findByRole("option", { name: /Confocal microscope/ });
    expect(await within(option).findByText("common:relationshipPicker.availabilityFailed")).toBeVisible();
    expect(option).toHaveAttribute("aria-disabled", "true");

    await user.keyboard("{ArrowDown}{Enter}");

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("relationship picker restore", () => {
  it("deduplicates canonical values and restores a non-booking source in one request", async () => {
    const requests: string[] = [];
    server.use(
      http.get("/api/v2/relationship-fixtures", ({ request }) => {
        const ids = new URL(request.url).searchParams.get("ids") ?? "";
        requests.push(ids);
        return HttpResponse.json(
          Object.fromEntries(ids.split(",").map((id) => [id, { globalId: id, name: `Target ${id.slice(2)}` }])),
        );
      }),
    );

    renderSelected("fx0042,FX42,fx7");

    expect(await screen.findByText("Target 42")).toBeVisible();
    expect(screen.getByText("Target 7")).toBeVisible();
    expect(requests).toEqual(["FX42,FX7"]);
  });

  it("keeps restored values subscribed to cache updates and invalidation refreshes", async () => {
    let requestCount = 0;
    server.use(
      http.get("/api/v2/relationship-fixtures", () => {
        requestCount += 1;
        const name = requestCount === 1 ? "Target 42" : "Refreshed target 42";
        return HttpResponse.json({ FX42: { globalId: "FX42", name } });
      }),
    );
    const { queryClient } = renderSelected("FX42");

    expect(await screen.findByText("Target 42")).toBeVisible();
    act(() => {
      queryClient.setQueryData(["relationship-option", "fixture-targets", "caller-one", "test-token", "FX42"], {
        globalId: "FX42",
        name: "Updated target 42",
      });
    });
    expect(await screen.findByText("Updated target 42")).toBeVisible();

    await act(async () => {
      await queryClient.invalidateQueries({
        queryKey: ["relationship-option-batch", "fixture-targets", "caller-one", "test-token", "FX42"],
      });
    });
    expect(await screen.findByText("Refreshed target 42")).toBeVisible();
    expect(requestCount).toBe(2);
  });

  it("uses a fresh selected cache entry without issuing a restore request", async () => {
    const request = vi.fn();
    const source = fixtureSource();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    queryClient.setQueryData(["relationship-option", "fixture-targets", "caller-one", "test-token", "FX42"], {
      globalId: "FX42",
      name: "Cached target 42",
    });
    server.use(
      http.get("/api/v2/relationship-fixtures", () => {
        request();
        return HttpResponse.json({ FX42: { globalId: "FX42", name: "Fetched target 42" } });
      }),
    );

    render(
      <QueryClientProvider client={queryClient}>
        <RelationshipPicker
          sources={[source]}
          authScope="caller-one"
          value="FX42"
          onChange={vi.fn()}
          ariaLabel="Selected relationships"
          multiple
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Cached target 42")).toBeVisible();
    expect(request).not.toHaveBeenCalled();
  });

  it("restores instruments with a sparse id=in list query", async () => {
    let requested: URL | undefined;
    server.use(
      http.get("/api/v2/instruments", ({ request }) => {
        requested = new URL(request.url);
        return HttpResponse.json({
          docs: [
            { id: 123, name: "Confocal microscope", globalId: "IN123" },
            { id: 456, name: "Orbital shaker", globalId: "IN456" },
          ],
        });
      }),
    );
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <RelationshipPicker
          source={relationshipSources.instruments}
          value="in00123,IN456"
          onChange={vi.fn()}
          ariaLabel="Selected relationships"
          multiple
        />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("Confocal microscope")).toBeVisible();
    expect(screen.getByText("Orbital shaker")).toBeVisible();
    expect(requested?.searchParams.get("where")).toBe("id=in=(123,456)");
    expect(requested?.searchParams.get("limit")).toBe("2");
    expect(requested?.searchParams.get("fields[instruments]")).toBe("id,name,globalId");
  });

  it("rejects malformed instruments restore envelopes", async () => {
    server.use(http.get("/api/v2/instruments", () => HttpResponse.json({ docs: "invalid" })));
    await expect(
      relationshipSources.instruments.resolveMany?.(["IN123"], "test-token", new AbortController().signal),
    ).rejects.toThrow("Relationship option response has an invalid envelope");
  });

  it("rejects malformed instruments restore documents", async () => {
    server.use(
      http.get("/api/v2/instruments", () => HttpResponse.json({ docs: [{ id: 123, name: "Confocal microscope" }] })),
    );
    await expect(
      relationshipSources.instruments.resolveMany?.(["IN123"], "test-token", new AbortController().signal),
    ).rejects.toThrow();
  });

  it("ignores valid instruments outside the requested restore IDs", async () => {
    server.use(
      http.get("/api/v2/instruments", () =>
        HttpResponse.json({
          docs: [
            { id: 123, name: "Confocal microscope", globalId: "IN123" },
            { id: 456, name: "Orbital shaker", globalId: "IN456" },
          ],
        }),
      ),
    );

    await expect(
      relationshipSources.instruments.resolveMany?.(["IN123"], "test-token", new AbortController().signal),
    ).resolves.toEqual({ IN123: { id: 123, name: "Confocal microscope", globalId: "IN123" } });
  });

  it("chunks restores at 100 values even when a saved selector contains many targets", async () => {
    const batches: string[][] = [];
    const source = fixtureSource();
    server.use(
      http.get("/api/v2/relationship-fixtures", ({ request }) => {
        const ids = new URL(request.url).searchParams.get("ids")?.split(",") ?? [];
        batches.push(ids);
        return HttpResponse.json(Object.fromEntries(ids.map((id) => [id, { globalId: id, name: id }])));
      }),
    );
    const values = Array.from({ length: 101 }, (_, index) => `FX${index + 1}`);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );

    const { result } = renderHook(
      () =>
        useSelectedRelationshipOptions({
          source,
          values,
          token: "test-token",
          labels: {},
        }),
      { wrapper },
    );

    await waitFor(() => expect(batches).toHaveLength(2));
    expect(batches.map((batch) => batch.length)).toEqual([100, 1]);
    expect(result.current.every((option) => option.restoreStatus === undefined)).toBe(true);
  });

  it("keeps malformed and unknown values visible without requesting them", () => {
    const request = vi.fn();
    server.use(
      http.get("/api/v2/relationship-fixtures", () => {
        request();
        return HttpResponse.json({});
      }),
    );

    renderSelected("FXoops,OTHER42");

    expect(screen.getByText("FXoops")).toBeVisible();
    expect(screen.getByText("OTHER42")).toBeVisible();
    expect(request).not.toHaveBeenCalled();
  });

  it("marks selected chips busy while a batched restore is pending", async () => {
    let markStarted!: () => void;
    const started = new Promise<void>((resolve) => {
      markStarted = resolve;
    });
    server.use(
      http.get("/api/v2/relationship-fixtures", async () => {
        markStarted();
        await new Promise((resolve) => setTimeout(resolve, 150));
        return HttpResponse.json({ FX42: { globalId: "FX42", name: "Target 42" } });
      }),
    );
    renderSelected("FX42");

    await started;

    expect(screen.getByRole("toolbar")).toHaveAttribute("aria-busy", "true");
  });

  it("preserves missing values and reports source failures without exposing error details", async () => {
    server.use(
      http.get("/api/v2/relationship-fixtures", () =>
        HttpResponse.json({ FX42: { globalId: "FX42", name: "Target 42" } }),
      ),
    );
    const missingView = renderSelected("FX404,FX42");

    expect(await screen.findByText("Target 42")).toBeVisible();
    expect(await screen.findByText("common:relationshipPicker.unavailable")).toBeVisible();
    missingView.unmount();

    server.use(
      http.get("/api/v2/relationship-fixtures", () => HttpResponse.text("private endpoint error", { status: 500 })),
    );
    renderSelected("FX500", "caller-two");

    expect(await screen.findByText(/FX500.*common:relationshipPicker\.restoreFailed/)).toBeVisible();
    expect(screen.queryByText("private endpoint error")).not.toBeInTheDocument();
  });

  it("partitions restore cache by caller and aborts a prior caller's batch", async () => {
    const started: (() => void)[] = [];
    const aborted = vi.fn();
    server.use(
      http.get("/api/v2/relationship-fixtures", async ({ request }) => {
        started.shift()?.();
        request.signal.addEventListener("abort", aborted, { once: true });
        await new Promise((resolve) => setTimeout(resolve, 300));
        return HttpResponse.json({ FX1: { globalId: "FX1", name: "Target 1" } });
      }),
    );
    const source = fixtureSource();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    let setStarted!: () => void;
    const firstStarted = new Promise<void>((resolve) => {
      setStarted = resolve;
    });
    started.push(setStarted);
    const view = render(
      <QueryClientProvider client={queryClient}>
        <RelationshipPicker
          sources={[source]}
          authScope="caller-one"
          value="FX1"
          onChange={vi.fn()}
          ariaLabel="Selected relationships"
        />
      </QueryClientProvider>,
    );
    await firstStarted;

    let setSecondStarted!: () => void;
    const secondStarted = new Promise<void>((resolve) => {
      setSecondStarted = resolve;
    });
    started.push(setSecondStarted);
    view.rerender(
      <QueryClientProvider client={queryClient}>
        <RelationshipPicker
          sources={[source]}
          authScope="caller-two"
          value="FX1"
          onChange={vi.fn()}
          ariaLabel="Selected relationships"
        />
      </QueryClientProvider>,
    );
    await secondStarted;
    await waitFor(() => expect(aborted).toHaveBeenCalledOnce());
  });
});

describe("relationship filter value picker", () => {
  function browsingSource(browsable: boolean, searches: string[]): RelationshipSource {
    return {
      id: "fixture-locations",
      globalIdPrefix: "FX",
      browsable,
      search: async (term) => {
        searches.push(term);
        return [{ id: 12, name: "Cold room" }];
      },
      ownsValue: (value) => /^FX\d+$/.test(value),
      toOption: (document) => {
        const location = document as { id: number; name: string };
        return { value: `FX${location.id}`, label: location.name };
      },
    };
  }

  function renderFilterValue(source: RelationshipSource) {
    const field = resolveCollectionConfig<{ id: string; location: string }>({
      slug: "fixtures",
      idField: "id",
      useAsTitle: "id",
      labels: { singularKey: "a", pluralKey: "b" },
      defaultColumns: ["id"],
      fields: [
        { name: "id", type: "text", labelKey: "id" },
        {
          name: "location",
          type: "relationship",
          relationTo: "fixture-locations",
          hasMany: false,
          labelKey: "location",
          filterPicker: { resource: "fixture-locations", identity: "globalId", globalIdPrefix: "FX" },
          capabilities: { filterOperators: ["equals"] },
        },
      ],
    }).fields[1];
    const onChange = vi.fn();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <FilterValueInput
          field={field}
          sources={{ "fixture-locations": source }}
          operator="equals"
          value=""
          number={1}
          onChange={onChange}
        />
      </QueryClientProvider>,
    );
    return onChange;
  }

  it("lists a browsable source's choices before the user types", async () => {
    const user = userEvent.setup();
    const searches: string[] = [];
    const onChange = renderFilterValue(browsingSource(true, searches));

    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.openOptions" }));
    await user.click(await screen.findByRole("option", { name: "Cold room" }));

    expect(searches).toContain("");
    expect(onChange).toHaveBeenCalledWith("FX12");
  });

  it("asks for a term when the source does not browse", async () => {
    const user = userEvent.setup();
    const searches: string[] = [];
    renderFilterValue(browsingSource(false, searches));

    await user.click(screen.getByRole("button", { name: "common:relationshipPicker.openOptions" }));

    expect(await screen.findByText("common:relationshipPicker.enterSearchTerm")).toBeVisible();
    expect(searches).toHaveLength(0);
  });
});
