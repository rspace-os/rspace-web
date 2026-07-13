import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import { type ComponentProps, type ReactElement, Suspense } from "react";
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import AccountMenu, { formatFullName, logoutHrefForSession } from "@/modules/common/app/AccountMenu";
import NewAppBar from "@/modules/common/app/AppBar";
import HelpMenu from "@/modules/common/app/HelpMenu";
import { useLighthouseSdk } from "@/modules/common/app/lighthouse";
import { useLivechatPropertiesQuery } from "@/modules/common/app/queries/livechatProperties";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import type { CurrentUser } from "@/modules/common/queries/currentUser";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { createUserSessionStore, UserSessionStoreProvider } from "@/modules/common/stores/userSessionStore";
import { ID_TOKEN_KEY } from "@/modules/common/utils/auth";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

vi.mock("@/modules/common/app/queries/livechatProperties", () => ({
  useLivechatPropertiesQuery: vi.fn(),
}));

vi.mock("@/modules/common/app/lighthouse", () => ({
  useLighthouseSdk: vi.fn(),
}));

vi.mock("@/modules/common/ui/avatar", () => ({
  Avatar: ({ children }: ComponentProps<"span">) => <span>{children}</span>,
  AvatarFallback: ({ children }: ComponentProps<"span">) => <span>{children}</span>,
  AvatarImage: (props: ComponentProps<"img">) => <img alt="" {...props} />,
}));

type TestLinkProps = Omit<ComponentProps<"a">, "href"> & {
  to: string;
  viewTransition?: boolean;
};

vi.mock("@tanstack/react-router", () => ({
  Link: ({ to, viewTransition, ...props }: TestLinkProps) => {
    void viewTransition;
    return <a href={to} {...props} />;
  },
}));

const currentUser: CurrentUser = {
  id: 1,
  username: "ada",
  email: "ada@example.com",
  firstName: "Ada",
  lastName: "Lovelace",
  homeFolderId: 2,
  workbenchId: 3,
  hasPiRole: true,
  hasSysAdminRole: false,
  profileImageUrl: null,
  orcid: { available: true, id: null },
  capabilities: { canUseInventory: true, canPublish: true, canViewSystem: false },
  session: { operatedAs: false, lastSession: null },
};

const appConfig = {
  branding: { bannerImageUrl: "" },
  helpLinks: [{ label: "Local help", url: "https://help.example.com" }],
  nextMaintenance: null,
  deploymentDescription: "",
  deploymentHelpEmail: "",
};

const server = setupServer(
  http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
  http.get("/api/v2/config", () => HttpResponse.json(appConfig)),
);

beforeAll(() => {
  fetchMock.disableMocks();
  server.listen({ onUnhandledRequest: "error" });
});

beforeEach(() => {
  vi.mocked(useOauthTokenQuery).mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  vi.mocked(useLivechatPropertiesQuery).mockReturnValue({
    data: { livechatEnabled: false, currentUser: "ada" },
  } as ReturnType<typeof useLivechatPropertiesQuery>);
  vi.mocked(useLighthouseSdk).mockReturnValue({ lighthouseReady: false, showLighthouse: vi.fn() });
});

afterEach(() => server.resetHandlers());

afterAll(() => {
  server.close();
  fetchMock.enableMocks();
});

function renderAppBar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <UIStoreProvider>
        <UserSessionStoreProvider>
          <Suspense fallback={null}>
            <NewAppBar currentPage="workspace" />
          </Suspense>
        </UserSessionStoreProvider>
      </UIStoreProvider>
    </QueryClientProvider>,
  );
}

function renderAccountMenu(ui: ReactElement, store = createUserSessionStore()) {
  render(<UserSessionStoreProvider store={store}>{ui}</UserSessionStoreProvider>);
  return store;
}

describe("NewAppBar (MSW-driven)", () => {
  it("derives navigation visibility from the current-user capabilities served by /api/v2/users/me", async () => {
    server.use(
      http.get("/api/v2/users/me", () =>
        HttpResponse.json({
          ...currentUser,
          capabilities: { ...currentUser.capabilities, canUseInventory: false, canViewSystem: true },
        }),
      ),
    );

    renderAppBar();

    const navigation = await screen.findByRole("navigation", { name: "common:appBar.mainLinks" });
    expect(
      within(navigation).queryByRole("link", { name: "common:appBar.sections.inventory.title" }),
    ).not.toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "common:appBar.sections.system.title" })).toBeInTheDocument();
  });

  it("shows the maintenance notice when /api/v2/config carries a scheduled window", async () => {
    server.use(
      http.get("/api/v2/config", () =>
        HttpResponse.json({
          ...appConfig,
          nextMaintenance: {
            id: 1,
            startDate: "2999-01-01T00:00:00.000Z",
            endDate: "2999-01-01T01:00:00.000Z",
            stopUserLoginDate: null,
            message: "Planned upgrade",
          },
        }),
      ),
    );

    renderAppBar();

    expect(await screen.findByRole("button", { name: /appBar.maintenancePopup/ })).toBeInTheDocument();
  });

  it("hides the maintenance notice when /api/v2/config reports none", async () => {
    renderAppBar();

    await screen.findByRole("navigation", { name: "common:appBar.mainLinks" });
    expect(screen.queryByRole("button", { name: /appBar.maintenancePopup/ })).not.toBeInTheDocument();
  });

  it("renders the deployment banner served by /api/v2/config", async () => {
    server.use(
      http.get("/api/v2/config", () =>
        HttpResponse.json({ ...appConfig, branding: { bannerImageUrl: "/public/banner" } }),
      ),
    );

    renderAppBar();

    const banners = await screen.findAllByRole("img", { name: "common:appBar.brandingAlt" });
    expect(banners[0]).toHaveAttribute("src", "/public/banner");
  });
});

describe("HelpMenu", () => {
  it("opens custom help links even when Lighthouse is not ready and closes on Escape", async () => {
    const user = userEvent.setup();
    render(<HelpMenu helpLinks={[{ label: "Local help", url: "https://help.example.com" }]} />);

    await user.click(screen.getByRole("button", { name: "common:helpDocs.openHelp" }));
    expect(screen.getByRole("menuitem", { name: "Local help" })).toHaveAttribute("href", "https://help.example.com");

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menuitem", { name: "Local help" })).not.toBeInTheDocument();
  });

  it("renders the disabled help fallback when there are no configured links", () => {
    render(<HelpMenu helpLinks={[]} />);

    expect(screen.getByRole("button", { name: "common:helpDocs.openHelp" })).toBeDisabled();
    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
  });
});

describe("AccountMenu", () => {
  it("closes when clicking outside", async () => {
    const user = userEvent.setup();
    renderAccountMenu(<AccountMenu currentUser={currentUser} bannerImageUrl="" />);

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
    expect(await screen.findByRole("menuitem", { name: "common:appBar.messaging" })).toBeVisible();

    await user.click(document.body);
    expect(screen.queryByRole("menuitem", { name: "common:appBar.messaging" })).not.toBeInTheDocument();
  });

  it("derives a full name and falls back to the username", () => {
    expect(formatFullName({ firstName: " Ada ", lastName: " Lovelace ", username: "ada" })).toBe("Ada Lovelace");
    expect(formatFullName({ firstName: " ", lastName: "", username: "ada" })).toBe("ada");
  });

  it("selects the ordinary and operated-as logout destinations", () => {
    expect(logoutHrefForSession(false)).toBe("/logout");
    expect(logoutHrefForSession(true)).toBe("/logout/runAsRelease");
  });

  it.each([
    [true, "/groups/viewPIGroup"],
    [false, "/userform"],
  ])("links hasPiRole=%s to the expected profile destination", async (hasPiRole, expectedHref) => {
    const user = userEvent.setup();
    renderAccountMenu(<AccountMenu currentUser={{ ...currentUser, hasPiRole }} bannerImageUrl="" />);

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));

    expect(await screen.findByRole("menuitem", { name: "common:appBar.sections.myRSpace.title" })).toHaveAttribute(
      "href",
      expectedHref,
    );
  });

  it("renders profile, ORCID, publishing, and operated-as session state", async () => {
    const user = userEvent.setup();
    renderAccountMenu(
      <AccountMenu
        currentUser={{
          ...currentUser,
          profileImageUrl: "/profile.png",
          orcid: { available: true, id: "0000-0001-2345-6789" },
          session: { ...currentUser.session, operatedAs: true },
        }}
        bannerImageUrl="/public/banner"
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));

    expect(await screen.findByText("common:appBar.operatingAs")).toBeInTheDocument();
    expect(screen.getByText("0000-0001-2345-6789")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /common:appBar.published/ })).toHaveAttribute(
      "href",
      "/public/publishedView/publishedDocuments",
    );
    expect(screen.getByRole("menuitem", { name: "common:appBar.release" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "common:appBar.brandingAlt" })).toHaveAttribute("src", "/public/banner");
  });

  it("clears canonical authentication state when releasing an operated-as session", async () => {
    const user = userEvent.setup();
    const sessionStore = createUserSessionStore();
    sessionStore.getState().upsertSession({ accountId: String(currentUser.id), token: "token" });
    sessionStorage.setItem(ID_TOKEN_KEY, "token");
    renderAccountMenu(
      <AccountMenu
        currentUser={{ ...currentUser, session: { ...currentUser.session, operatedAs: true } }}
        bannerImageUrl=""
      />,
      sessionStore,
    );

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
    await user.click(await screen.findByRole("menuitem", { name: "common:appBar.release" }));

    expect(sessionStore.getState().sessions).toEqual([]);
    expect(sessionStore.getState().activeAccountId).toBeNull();
    expect(sessionStorage.getItem(ID_TOKEN_KEY)).toBeNull();
  });

  it("uses the profile image for an ordinary session", () => {
    renderAccountMenu(
      <AccountMenu currentUser={{ ...currentUser, profileImageUrl: "/profile.png" }} bannerImageUrl="" />,
    );

    expect(screen.getByAltText("")).toHaveAttribute("src", "/profile.png");
  });
});
