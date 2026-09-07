import "@/__tests__/__mocks__/matchMedia";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { RelationshipPicker } from "@/modules/common/relationship-picker/RelationshipPicker";
import {
  granteeRelationshipSource,
  type RelationshipSource,
  relationshipSources,
} from "@/modules/common/relationship-picker/relationshipSources";

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

describe("relationship picker search", () => {
  it("keeps grantee search scoped to the resource endpoint", async () => {
    let requested: URL | undefined;
    server.use(
      http.get("/api/v2/bookings/42/access/grantees", ({ request }) => {
        requested = new URL(request.url);
        return HttpResponse.json([{ kind: "USER", id: 7, key: "user:7", name: "Ada", detail: null }]);
      }),
    );

    const source = granteeRelationshipSource("bookings", 42);
    await expect(source.search("Ada", "test-token", new AbortController().signal)).resolves.toHaveLength(1);
    expect(requested?.pathname).toBe("/api/v2/bookings/42/access/grantees");
    expect(requested?.searchParams.get("query")).toBe("Ada");
    expect(requested?.searchParams.get("limit")).toBe("20");
    expect(source.ownsValue("user:7")).toBe(true);
    expect(source.ownsValue("audience:all-users")).toBe(false);
  });

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

  it("searches by name and applies the selected global ID", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const requests: URL[] = [];
    listInstruments((url) => requests.push(url));
    renderPicker(onChange);

    await user.type(screen.getByRole("combobox", { name: "Relationship" }), "Conf");

    const option = await screen.findByRole("option", { name: /Confocal microscope/ });
    expect(within(option).getByRole("link", { name: "common:relationshipPicker.openRecord" })).toHaveAttribute(
      "href",
      "/globalId/IN123",
    );
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
