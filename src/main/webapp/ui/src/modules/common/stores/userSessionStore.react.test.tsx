import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";
import { UserSessionBootstrap, UserSessionStoreProvider, useActiveSession } from "./userSessionStore";

vi.mock("@/modules/common/queries/currentUser", () => ({
  useCurrentUserQuery: vi.fn(),
}));

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

const currentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: null,
  workbenchId: null,
  hasPiRole: false,
  hasSysAdminRole: false,
  profileImageUrl: null,
  orcid: { available: true, id: null },
  capabilities: { canUseInventory: true, canPublish: false, canViewSystem: false },
  session: {
    operatedAs: false,
    lastSession: null,
  },
};

const mockedUseCurrentUserQuery = vi.mocked(useCurrentUserQuery);
const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);

function ActiveSessionStatus() {
  const session = useActiveSession();
  return <output>{session ? `${session.accountId}:${session.token}` : "none"}</output>;
}

function renderBootstrap() {
  return render(
    <UserSessionStoreProvider>
      <UserSessionBootstrap />
      <ActiveSessionStatus />
    </UserSessionStoreProvider>,
  );
}

describe("UserSessionBootstrap", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    mockedUseOauthTokenQuery.mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
    mockedUseCurrentUserQuery.mockReturnValue({ data: currentUser } as ReturnType<typeof useCurrentUserQuery>);
  });

  it("registers the logged-in account as the active session", async () => {
    renderBootstrap();

    expect(await screen.findByText("1:token")).toBeInTheDocument();
  });
});
