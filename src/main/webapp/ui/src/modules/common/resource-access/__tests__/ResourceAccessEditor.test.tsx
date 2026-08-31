import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import i18n from "@/modules/common/i18n";
import { type ResourceAccessAdapter, ResourceAccessEditor } from "../ResourceAccessEditor";

const adapter: ResourceAccessAdapter = {
  ownerRole: "STEWARD",
  defaultRole: "CONTRIBUTOR",
  allUsersRole: "READER",
  allUsersLabel: "All people",
  leaveLabel: "Leave collection",
  roles: [
    {
      key: "STEWARD",
      label: "Steward",
      description: "Full control",
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "CONTRIBUTOR",
      label: "Contributor",
      description: "Can contribute",
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    {
      key: "READER",
      label: "Reader",
      description: "Can read",
      allowedGranteeKinds: ["USER", "GROUP", "AUDIENCE"],
    },
  ],
};

const owner = {
  grantee: {
    kind: "USER",
    id: 11,
    key: "user:11",
    name: "Ada Owner",
    detail: "ada",
    available: true,
    effectiveRole: "STEWARD",
    roleSources: [],
  },
  role: "STEWARD",
};

function document(version = 3, assignments: unknown[] = [owner]) {
  return {
    scheme: "test-collection",
    version,
    assignments,
    caller: {
      effectiveRole: "STEWARD",
      roleSources: [],
      capabilities: { canManageAssignments: true, canManageOwners: true, canLeave: false },
    },
  };
}

function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

describe("ResourceAccessEditor", () => {
  it("uses the adapter's safe default role and saves one versioned generic replacement", async () => {
    let putRequest: Request | undefined;
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document())),
      http.get("/api/v2/test-collections/9/access/grantees", () =>
        HttpResponse.json([{ kind: "GROUP", id: 41, key: "group:41", name: "Imaging group", detail: "imaging" }]),
      ),
      http.put("/api/v2/test-collections/9/access", async ({ request }) => {
        putRequest = request;
        return HttpResponse.json(
          document(4, [
            owner,
            {
              grantee: {
                kind: "GROUP",
                id: 41,
                key: "group:41",
                name: "Imaging group",
                detail: "imaging",
                available: true,
                effectiveRole: "CONTRIBUTOR",
                roleSources: [],
              },
              role: "CONTRIBUTOR",
            },
          ]),
        );
      }),
    );
    const user = userEvent.setup();
    const { baseElement } = render(
      <ResourceAccessEditor resource="test-collections" resourceId={9} token="token" adapter={adapter} />,
      { wrapper: wrapper() },
    );

    expect(i18n.getFixedT("en-US", "common")("resourceAccess.addUserOrGroup")).toBe("Add user or group");
    const search = await screen.findByLabelText("common:resourceAccess.addUserOrGroup");
    expect(screen.queryByRole("heading", { name: /access/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/effective access/i)).not.toBeInTheDocument();
    await user.type(search, "im");
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.search" }));
    const result = await screen.findByText("Imaging group");
    await user.click(within(result.closest("li") ?? baseElement).getByRole("button"));

    const groupRow = screen.getByText("Imaging group").closest("li");
    expect(groupRow).not.toBeNull();
    expect(within(groupRow ?? baseElement).getByRole("combobox")).toHaveValue("CONTRIBUTOR");
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

    await waitFor(() => expect(putRequest).toBeDefined());
    expect(putRequest?.headers.get("If-Match")).toBe('"3"');
    await expect(putRequest?.json()).resolves.toEqual({
      assignments: [
        { granteeKey: "user:11", role: "STEWARD" },
        { granteeKey: "group:41", role: "CONTRIBUTOR" },
      ],
    });
    await expectAccessible(baseElement);
  });

  it("keeps the draft after a stale write until the caller reviews the latest version", async () => {
    let getCount = 0;
    server.use(
      http.get("/api/v2/test-collections/9/access", () => {
        getCount += 1;
        return HttpResponse.json(document(getCount === 1 ? 3 : 4));
      }),
      http.put("/api/v2/test-collections/9/access", () => new HttpResponse(null, { status: 412 })),
    );
    const user = userEvent.setup();
    render(<ResourceAccessEditor resource="test-collections" resourceId={9} token="token" adapter={adapter} />, {
      wrapper: wrapper(),
    });

    await screen.findByText("Ada Owner");
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.addNamed" }));
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

    expect(await screen.findByText("common:resourceAccess.conflictTitle")).toBeInTheDocument();
    expect(screen.getByText("All people")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.reviewLatest" }));
    expect(screen.queryByText("All people")).not.toBeInTheDocument();
  });
});
