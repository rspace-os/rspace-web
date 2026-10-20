import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Link,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { ResourceAccessEditor } from "../ResourceAccessEditor";
import type { GranteeKind, ResourceAccessAssignment } from "../schemas";

const adapter = {
  ownerRole: "STEWARD",
  defaultRole: "CONTRIBUTOR",
  allUsersRole: "READER",
  allUsersLabel: "All people",
  leaveLabel: "Leave collection",
  roles: [
    { key: "STEWARD", label: "Steward", description: "Full control", allowedGranteeKinds: ["USER", "GROUP"] },
    {
      key: "CONTRIBUTOR",
      label: "Contributor",
      description: "Can contribute",
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    { key: "READER", label: "Reader", description: "Can read", allowedGranteeKinds: ["USER", "GROUP", "AUDIENCE"] },
  ],
} as const;

function assignment(id: number, name: string, role: string, kind: GranteeKind = "USER"): ResourceAccessAssignment {
  return {
    grantee: {
      kind,
      id,
      key: `${kind.toLowerCase()}:${id}`,
      name,
      available: true,
      effectiveRole: role,
      roleSources: [],
    },
    role,
  };
}

const owner = assignment(11, "Ada Owner", "STEWARD");
const oldMember = assignment(12, "Old member", "CONTRIBUTOR");
const newMember = assignment(13, "New member", "CONTRIBUTOR");
const secondOwner = assignment(14, "Bob Steward", "STEWARD");
const remoteReader = assignment(15, "Remote reader", "READER");
const inheritedGroup = assignment(41, "Imaging group", "READER", "GROUP");
const directMember = assignment(21, "Ada Member", "CONTRIBUTOR");
const otherOwner = assignment(99, "Cleo Owner", "STEWARD");

type CallerOptions = {
  callerKey?: string | null;
  effectiveRole?: string | null;
  roleSources?: unknown[];
  canManageAssignments?: boolean;
  canManageOwners?: boolean;
  canLeave?: boolean;
};

function accessDocument(version = 3, assignments: unknown[] = [owner], caller: CallerOptions = {}) {
  return {
    scheme: "test-collection",
    version,
    assignments,
    caller: {
      effectiveRole: caller.effectiveRole ?? "STEWARD",
      roleSources: caller.roleSources ?? [],
      granteeKey: caller.callerKey === undefined ? "user:11" : caller.callerKey,
      capabilities: {
        canManageAssignments: caller.canManageAssignments ?? true,
        canManageOwners: caller.canManageOwners ?? true,
        canLeave: caller.canLeave ?? false,
      },
    },
  };
}

function table() {
  return within(screen.getByRole("table"));
}

describe("ResourceAccessEditor regressions", () => {
  it("starts a clean draft when the same mounted editor changes resource IDs", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const putRequests: Array<{ id: string; request: Request }> = [];
    server.use(
      http.get("/api/v2/items/9/access", () => HttpResponse.json(accessDocument(3, [owner, oldMember]))),
      http.get("/api/v2/items/10/access", () => HttpResponse.json(accessDocument(3, [owner, newMember]))),
      http.put("/api/v2/items/:id/access", async ({ request, params }) => {
        putRequests.push({ id: String(params.id), request: request.clone() });
        return HttpResponse.json(accessDocument(4, [owner]));
      }),
    );

    const { rerender } = render(
      <QueryClientProvider client={client}>
        <ResourceAccessEditor resource="items" resourceId={9} token="token" adapter={adapter} />
      </QueryClientProvider>,
    );
    await screen.findByRole("table");
    await user.click(table().getByRole("button", { name: "common:resourceAccess.removeNamed" }));
    expect(table().getByText("common:resourceAccess.staged.removed")).toBeVisible();

    rerender(
      <QueryClientProvider client={client}>
        <ResourceAccessEditor resource="items" resourceId={10} token="token" adapter={adapter} />
      </QueryClientProvider>,
    );

    expect(await within(await screen.findByRole("table")).findByText("New member")).toBeVisible();
    expect(table().queryByText("Old member")).not.toBeInTheDocument();
    await user.click(table().getByRole("button", { name: "common:resourceAccess.removeNamed" }));
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

    expect(await screen.findByText("common:resourceAccess.saved")).toBeInTheDocument();
    expect(putRequests.map(({ id }) => id)).toEqual(["10"]);
    expect(putRequests[0]?.request.headers.get("If-Match")).toBe('"3"');
    await expect(putRequests[0]?.request.json()).resolves.toEqual({
      assignments: [{ granteeKey: "user:11", role: "STEWARD" }],
    });
  });

  it("keeps a failed conflict refresh recoverable and blocks navigation until the merged draft is saved", async () => {
    const user = userEvent.setup();
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => {});
    try {
      const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
      let accessRequests = 0;
      const putRequests: Request[] = [];
      server.use(
        http.get("/api/v2/test-collections/9/access", () => {
          accessRequests += 1;
          if (accessRequests === 2) return new HttpResponse(null, { status: 503 });
          return HttpResponse.json(
            accessRequests === 1
              ? accessDocument(3, [owner, secondOwner])
              : accessDocument(4, [owner, secondOwner, remoteReader]),
          );
        }),
        http.put("/api/v2/test-collections/9/access", async ({ request }) => {
          putRequests.push(request.clone());
          if (putRequests.length === 1) return new HttpResponse(null, { status: 412 });
          return HttpResponse.json(accessDocument(5, [owner, remoteReader]));
        }),
      );

      const rootRoute = createRootRoute({
        component: () => (
          <QueryClientProvider client={client}>
            <Outlet />
          </QueryClientProvider>
        ),
      });
      const editorRoute = createRoute({
        getParentRoute: () => rootRoute,
        path: "/editor",
        component: () => (
          <>
            <Link to="/public/apiDocs">{"Elsewhere"}</Link>
            <ResourceAccessEditor resource="test-collections" resourceId={9} token="token" adapter={adapter} />
          </>
        ),
      });
      const elsewhereRoute = createRoute({
        getParentRoute: () => rootRoute,
        path: "/public/apiDocs",
        component: () => <p>{"Elsewhere page"}</p>,
      });
      const router = createRouter({
        routeTree: rootRoute.addChildren([editorRoute, elsewhereRoute]),
        history: createMemoryHistory({ initialEntries: ["/editor"] }),
      });
      render(<RouterProvider router={router} />);

      await screen.findByRole("table");
      await user.click(table().getByRole("button", { name: "common:resourceAccess.removeNamed" }));
      await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

      const retry = await screen.findByRole("button", { name: "common:resourceAccess.retryLatest" });
      expect(table().getByText("Bob Steward")).toBeVisible();
      expect(table().getByText("common:resourceAccess.staged.removed")).toBeVisible();
      await user.click(screen.getByRole("link", { name: "Elsewhere" }));
      const navigationDialog = await screen.findByRole("alertdialog");
      expect(within(navigationDialog).getByText("common:dirtyNavigation.title")).toBeVisible();
      await user.click(within(navigationDialog).getByRole("button", { name: "common:actions.cancel" }));

      await user.click(retry);
      expect(await table().findByText("Remote reader")).toBeVisible();
      expect(await screen.findByText("common:resourceAccess.conflictMerged")).toBeInTheDocument();
      expect(table().getByText("common:resourceAccess.staged.removed")).toBeVisible();
      await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

      expect(await screen.findByText("common:resourceAccess.saved")).toBeInTheDocument();
      expect(putRequests).toHaveLength(2);
      expect(putRequests[0]?.headers.get("If-Match")).toBe('"3"');
      expect(putRequests[1]?.headers.get("If-Match")).toBe('"4"');
      await expect(putRequests[1]?.json()).resolves.toEqual({
        assignments: [
          { granteeKey: "user:11", role: "STEWARD" },
          { granteeKey: "user:15", role: "READER" },
        ],
      });
      await user.click(screen.getByRole("link", { name: "Elsewhere" }));
      expect(await screen.findByText("Elsewhere page")).toBeVisible();
    } finally {
      scrollTo.mockRestore();
    }
  });

  it("closes Leave and shows the remaining group access after a successful response", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const callerWithDirectAndGroupAccess = {
      callerKey: "user:21",
      effectiveRole: "CONTRIBUTOR",
      roleSources: [
        { kind: "DIRECT", role: "CONTRIBUTOR" },
        { kind: "GROUP", role: "READER", grantee: inheritedGroup.grantee },
      ],
      canManageAssignments: false,
      canManageOwners: false,
      canLeave: true,
    };
    const callerWithGroupAccess = {
      callerKey: "user:21",
      effectiveRole: "READER",
      roleSources: [{ kind: "GROUP", role: "READER", grantee: inheritedGroup.grantee }],
      canManageAssignments: false,
      canManageOwners: false,
      canLeave: false,
    };
    let accessRequests = 0;
    server.use(
      http.get("/api/v2/test-collections/9/access", () => {
        accessRequests += 1;
        return HttpResponse.json(
          accessRequests === 1
            ? accessDocument(3, [otherOwner, directMember, inheritedGroup], callerWithDirectAndGroupAccess)
            : accessDocument(4, [otherOwner, inheritedGroup], callerWithGroupAccess),
        );
      }),
      http.delete("/api/v2/test-collections/9/access/me", () => new HttpResponse(null, { status: 204 })),
    );

    render(
      <QueryClientProvider client={client}>
        <ResourceAccessEditor resource="test-collections" resourceId={9} token="token" adapter={adapter} />
      </QueryClientProvider>,
    );
    await screen.findByRole("table");
    await user.click(table().getByRole("button", { name: "common:resourceAccess.leaveSelf" }));
    const leaveDialog = await screen.findByRole("alertdialog");
    await user.click(within(leaveDialog).getByRole("button", { name: "Leave collection" }));

    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    await waitFor(() => expect(table().queryByText("Ada Member")).not.toBeInTheDocument());
    expect(table().getByText("Imaging group")).toBeVisible();
  });

  it("does not keep the previous caller's access controls after a token change is denied", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const authorizations: Array<string | null> = [];
    server.use(
      http.get("/api/v2/test-collections/9/access", ({ request }) => {
        const authorization = request.headers.get("authorization");
        authorizations.push(authorization);
        return authorization === "Bearer old-token"
          ? HttpResponse.json(accessDocument(3, [owner, oldMember]))
          : new HttpResponse(null, { status: 403 });
      }),
    );

    const { rerender } = render(
      <QueryClientProvider client={client}>
        <ResourceAccessEditor resource="test-collections" resourceId={9} token="old-token" adapter={adapter} />
      </QueryClientProvider>,
    );
    await screen.findByRole("table");
    await user.click(table().getByRole("button", { name: "common:resourceAccess.removeNamed" }));
    expect(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" })).toBeEnabled();

    rerender(
      <QueryClientProvider client={client}>
        <ResourceAccessEditor resource="test-collections" resourceId={9} token="new-token" adapter={adapter} />
      </QueryClientProvider>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent("common:resourceAccess.loadError");
    expect(screen.queryByLabelText("common:resourceAccess.addUserOrGroup")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "common:resourceAccess.saveChanges" })).not.toBeInTheDocument();
    expect(authorizations).toEqual(["Bearer old-token", "Bearer new-token"]);
  });

  it("does not run a completed Leave callback after the editor changes resources", async () => {
    const user = userEvent.setup();
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
    const deleteResponse = Promise.withResolvers<Response>();
    let deleteStarted = false;
    let left = false;
    server.use(
      http.get("/api/v2/test-collections/9/access", () =>
        HttpResponse.json(
          accessDocument(3, [otherOwner, directMember], {
            callerKey: "user:21",
            effectiveRole: "CONTRIBUTOR",
            canManageAssignments: false,
            canManageOwners: false,
            canLeave: true,
          }),
        ),
      ),
      http.get("/api/v2/test-collections/10/access", () =>
        HttpResponse.json(accessDocument(3, [otherOwner, newMember])),
      ),
      http.delete("/api/v2/test-collections/9/access/me", () => {
        deleteStarted = true;
        return deleteResponse.promise;
      }),
    );

    try {
      const onLeave = () => {
        left = true;
      };
      const { rerender } = render(
        <QueryClientProvider client={client}>
          <ResourceAccessEditor
            resource="test-collections"
            resourceId={9}
            token="token"
            adapter={adapter}
            onLeave={onLeave}
          />
        </QueryClientProvider>,
      );
      await screen.findByRole("table");
      await user.click(table().getByRole("button", { name: "common:resourceAccess.leaveSelf" }));
      const leaveDialog = await screen.findByRole("alertdialog");
      await user.click(within(leaveDialog).getByRole("button", { name: "Leave collection" }));
      await waitFor(() => expect(deleteStarted).toBe(true));

      rerender(
        <QueryClientProvider client={client}>
          <ResourceAccessEditor
            resource="test-collections"
            resourceId={10}
            token="token"
            adapter={adapter}
            onLeave={onLeave}
          />
        </QueryClientProvider>,
      );
      expect(await within(await screen.findByRole("table")).findByText("New member")).toBeVisible();
      deleteResponse.resolve(new HttpResponse(null, { status: 204 }));
      await waitFor(() => expect(client.isMutating()).toBe(0));

      expect(left).toBe(false);
      expect(table().getByText("New member")).toBeVisible();
    } finally {
      deleteResponse.resolve(new HttpResponse(null, { status: 204 }));
      await waitFor(() => expect(client.isMutating()).toBe(0));
    }
  });
});
