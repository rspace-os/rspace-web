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
    { key: "STEWARD", label: "Steward", description: "Full control", allowedGranteeKinds: ["USER", "GROUP"] },
    {
      key: "CONTRIBUTOR",
      label: "Contributor",
      description: "Can contribute",
      allowedGranteeKinds: ["USER", "GROUP"],
    },
    { key: "READER", label: "Reader", description: "Can read", allowedGranteeKinds: ["USER", "GROUP", "AUDIENCE"] },
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

const second = {
  grantee: {
    kind: "USER",
    id: 12,
    key: "user:12",
    name: "Bob Steward",
    detail: "bob",
    available: true,
    effectiveRole: "STEWARD",
    roleSources: [],
  },
  role: "STEWARD",
};

const audience = {
  grantee: {
    kind: "AUDIENCE",
    id: 0,
    key: "audience:all-users",
    name: "All people",
    detail: null,
    available: true,
    effectiveRole: "READER",
    roleSources: [],
  },
  role: "READER",
};

function document(version = 3, assignments: unknown[] = [owner], callerKey: string | null = "user:11") {
  return {
    scheme: "test-collection",
    version,
    assignments,
    caller: {
      effectiveRole: "STEWARD",
      roleSources: [],
      granteeKey: callerKey,
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

function editor() {
  return render(<ResourceAccessEditor resource="test-collections" resourceId={9} token="token" adapter={adapter} />, {
    wrapper: wrapper(),
  });
}

/**
 * The table and the narrow-screen card list both mount; only CSS hides one, and jsdom does not
 * evaluate the container query. Scoping to the table keeps every query unambiguous.
 */
function table() {
  return within(screen.getByRole("table"));
}

function row(name: string) {
  return within(table().getByText(name).closest("tr") as HTMLElement);
}

describe("ResourceAccessEditor", () => {
  it("stages a picked grantee at the adapter's default role and saves one versioned replacement", async () => {
    let putRequest: Request | undefined;
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document())),
      http.get("/api/v2/test-collections/9/access/grantees", () =>
        HttpResponse.json([{ kind: "GROUP", id: 41, key: "group:41", name: "Imaging group", detail: "imaging" }]),
      ),
      http.put("/api/v2/test-collections/9/access", async ({ request }) => {
        putRequest = request;
        return HttpResponse.json(document(4, [owner]));
      }),
    );
    const user = userEvent.setup();
    const { baseElement } = editor();

    expect(i18n.getFixedT("en-US", "common")("resourceAccess.addUserOrGroup")).toBe("Add user or group");
    const search = await screen.findByLabelText("common:resourceAccess.addUserOrGroup");
    expect(screen.queryByText(/effective access/i)).not.toBeInTheDocument();

    await user.type(search, "im");
    await user.click(await screen.findByRole("option", { name: /Imaging group/ }));

    // Picking stages immediately: there is no separate "Add" step any more.
    expect(row("Imaging group").getByRole("button", { name: "common:resourceAccess.roleFor" })).toHaveTextContent(
      "Contributor",
    );
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

  it("keeps a row staged for removal visible until it is saved, and can restore it", async () => {
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document(3, [owner, second]))),
      http.get("/api/v2/test-collections/9/access/grantees", () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    editor();
    await screen.findByRole("table");

    await user.click(row("Bob Steward").getByRole("button", { name: "common:resourceAccess.removeNamed" }));

    // Still listed, marked as staged, and restorable rather than silently gone.
    expect(table().getByText("Bob Steward")).toBeInTheDocument();
    expect(row("Bob Steward").getByText("common:resourceAccess.staged.removed")).toBeInTheDocument();
    const restore = row("Bob Steward").getByRole("button", { name: "common:resourceAccess.restoreNamed" });

    await user.click(restore);
    expect(row("Bob Steward").queryByText("common:resourceAccess.staged.removed")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" })).toBeDisabled();
  });

  it("marks a later edit dirty again after a successful save", async () => {
    const contributor = { ...second, role: "CONTRIBUTOR" };
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document(3, [owner, second]))),
      http.get("/api/v2/test-collections/9/access/grantees", () => HttpResponse.json([])),
      http.put("/api/v2/test-collections/9/access", () => HttpResponse.json(document(4, [owner, contributor]))),
    );
    const user = userEvent.setup();
    editor();
    await screen.findByRole("table");

    await user.click(row("Bob Steward").getByRole("button", { name: "common:resourceAccess.roleFor" }));
    await user.click(await screen.findByRole("menuitem", { name: "Contributor" }));
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));
    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("common:resourceAccess.saved"));

    await user.click(row("Bob Steward").getByRole("button", { name: "common:resourceAccess.roleFor" }));
    await user.click(await screen.findByRole("menuitem", { name: "Steward" }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("common:resourceAccess.unsavedChanges"));
    expect(screen.getByRole("status")).not.toHaveTextContent("common:resourceAccess.saved");
    expect(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" })).toBeEnabled();
  });

  it("blocks removing the last owner but keeps the reason reachable", async () => {
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document())),
      http.get("/api/v2/test-collections/9/access/grantees", () => HttpResponse.json([])),
    );
    editor();
    await screen.findByRole("table");

    // Leave, not Remove: this is the caller's own row.
    const action = row("Ada Owner").getByRole("button", { name: "common:resourceAccess.leaveSelf" });
    expect(action).toHaveAttribute("aria-disabled", "true");
    // aria-disabled, not disabled, so it stays focusable and its reason is announced.
    expect(action).not.toBeDisabled();
    const describedBy = action.getAttribute("aria-describedby");
    expect(describedBy).not.toBeNull();
    expect(action.ownerDocument.getElementById(describedBy ?? "")).toHaveTextContent("common:resourceAccess.lastOwner");
  });

  it("shows the audience without a kind badge and never offers to remove it", async () => {
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document(3, [owner, audience]))),
      http.get("/api/v2/test-collections/9/access/grantees", () => HttpResponse.json([])),
    );
    editor();
    await screen.findByRole("table");

    expect(table().getByText("All people")).toBeInTheDocument();
    // No User/Group badge, and no remove action: the audience is not revoked from this editor.
    expect(row("All people").queryByText("common:resourceAccess.kind.user")).toBeNull();
    expect(row("All people").queryByText("common:resourceAccess.kind.group")).toBeNull();
    expect(row("All people").queryByRole("button", { name: "common:resourceAccess.removeNamed" })).toBeNull();
  });

  it("omits already-assigned grantees from the picker", async () => {
    server.use(
      http.get("/api/v2/test-collections/9/access", () => HttpResponse.json(document(3, [owner, second]))),
      http.get("/api/v2/test-collections/9/access/grantees", () =>
        HttpResponse.json([
          { kind: "USER", id: 12, key: "user:12", name: "Bob Steward", detail: "bob" },
          { kind: "GROUP", id: 41, key: "group:41", name: "Imaging group", detail: "imaging" },
        ]),
      ),
    );
    const user = userEvent.setup();
    editor();

    const search = await screen.findByLabelText("common:resourceAccess.addUserOrGroup");
    await user.type(search, "b");
    await user.type(search, "o");

    expect(await screen.findByRole("option", { name: /Imaging group/ })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Bob Steward/ })).not.toBeInTheDocument();
  });

  it("lets a caller who may only leave do so, without needing manage rights", async () => {
    let leaveRequest: Request | undefined;
    let left = false;
    // A different person owns it; the caller only holds a contributor row of their own.
    const otherOwner = {
      grantee: {
        kind: "USER",
        id: 99,
        key: "user:99",
        name: "Cleo Owner",
        detail: "cleo",
        available: true,
        effectiveRole: "STEWARD",
        roleSources: [],
      },
      role: "STEWARD",
    };
    const contributorSelf = {
      grantee: {
        kind: "USER",
        id: 11,
        key: "user:11",
        name: "Ada Contributor",
        detail: "ada",
        available: true,
        effectiveRole: "CONTRIBUTOR",
        roleSources: [],
      },
      role: "CONTRIBUTOR",
    };
    server.use(
      http.get("/api/v2/test-collections/9/access", () =>
        HttpResponse.json({
          scheme: "test-collection",
          version: 3,
          assignments: [otherOwner, contributorSelf],
          caller: {
            effectiveRole: "CONTRIBUTOR",
            roleSources: [],
            granteeKey: "user:11",
            // May leave, may not administer: the two capabilities are independent.
            capabilities: { canManageAssignments: false, canManageOwners: false, canLeave: true },
          },
        }),
      ),
      http.delete("/api/v2/test-collections/9/access/me", ({ request }) => {
        leaveRequest = request;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    render(
      <ResourceAccessEditor
        resource="test-collections"
        resourceId={9}
        token="token"
        adapter={adapter}
        onLeave={() => {
          left = true;
        }}
      />,
      { wrapper: wrapper() },
    );
    await screen.findByRole("table");

    const leaveAction = row("Ada Contributor").getByRole("button", { name: "common:resourceAccess.leaveSelf" });
    expect(leaveAction).not.toHaveAttribute("aria-disabled");
    await user.click(leaveAction);

    // Leaving is immediate rather than staged, so it is confirmed first.
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Leave collection" }));

    await waitFor(() => expect(leaveRequest).toBeDefined());
    expect(leaveRequest?.method).toBe("DELETE");
    await waitFor(() => expect(left).toBe(true));
  });

  it("three-way merges a stale draft and requires resolution for a divergent assignment", async () => {
    let getCount = 0;
    let putCount = 0;
    let replacement: Request | undefined;
    let finishRefresh: (() => void) | undefined;
    const refreshGate = new Promise<void>((resolve) => {
      finishRefresh = resolve;
    });
    server.use(
      http.get("/api/v2/test-collections/9/access", async () => {
        getCount += 1;
        if (getCount > 1) await refreshGate;
        return HttpResponse.json(
          getCount === 1
            ? document(3)
            : document(4, [
                owner,
                {
                  grantee: {
                    kind: "GROUP",
                    id: 41,
                    key: "group:41",
                    name: "Imaging group",
                    detail: "imaging",
                    available: true,
                    effectiveRole: "READER",
                    roleSources: [],
                  },
                  role: "READER",
                },
              ]),
        );
      }),
      http.get("/api/v2/test-collections/9/access/grantees", () =>
        HttpResponse.json([{ kind: "GROUP", id: 41, key: "group:41", name: "Imaging group", detail: "imaging" }]),
      ),
      http.put("/api/v2/test-collections/9/access", ({ request }) => {
        putCount += 1;
        if (putCount === 1) return new HttpResponse(null, { status: 412 });
        replacement = request;
        return HttpResponse.json(document(5));
      }),
    );
    const user = userEvent.setup();
    editor();

    await screen.findByRole("table");
    await user.type(screen.getByLabelText("common:resourceAccess.addUserOrGroup"), "im");
    await user.click(await screen.findByRole("option", { name: /Imaging group/ }));
    await user.click(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" }));

    expect(await screen.findByText("common:resourceAccess.conflictTitle")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "common:resourceAccess.saveChanges" })).toBeDisabled();

    finishRefresh?.();
    await user.click(await screen.findByRole("button", { name: "common:resourceAccess.keepMine" }));
    const saveButton = screen.getByRole("button", { name: "common:resourceAccess.saveChanges" });
    expect(saveButton).toBeEnabled();
    await user.click(saveButton);

    await waitFor(() => expect(replacement).toBeDefined());
    expect(replacement?.headers.get("If-Match")).toBe('"4"');
    await expect(replacement?.json()).resolves.toEqual({
      assignments: [
        { granteeKey: "user:11", role: "STEWARD" },
        { granteeKey: "group:41", role: "CONTRIBUTOR" },
      ],
    });
  });
});
