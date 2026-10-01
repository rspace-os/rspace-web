import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { type ComponentProps, type ReactElement, Suspense } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { currentUserResponse } from "@/__tests__/helpers/currentUserResponse";
import { wrapWithRealI18n } from "@/__tests__/helpers/realI18n";
import { v2ListEnvelope } from "@/__tests__/helpers/v2ListEnvelope";
import { server } from "@/__tests__/mswServer";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import AccountMenu from "@/modules/common/app/AccountMenu";
import NewAppBar from "@/modules/common/app/AppBar";
import HelpMenu from "@/modules/common/app/HelpMenu";
import { useLighthouseSdk } from "@/modules/common/app/lighthouse";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { ID_TOKEN_KEY } from "@/modules/common/utils/auth";

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

vi.mock("@/modules/common/app/lighthouse", () => ({
  useLighthouseSdk: vi.fn(),
}));

vi.mock("@/featureFlags/queries", () => ({
  useIsFeatureFlagEnabled: vi.fn(),
}));

vi.mock("@/modules/common/ui/avatar", () => ({
  Avatar: ({ children }: ComponentProps<"span">) => <span>{children}</span>,
  AvatarFallback: ({ children }: ComponentProps<"span">) => <span>{children}</span>,
  AvatarImage: (props: ComponentProps<"img">) => <img alt="" {...props} />,
}));

type TestLinkProps = Omit<ComponentProps<"a">, "href"> & {
  to: string;
  reloadDocument?: boolean;
};

vi.mock("@tanstack/react-router", () => ({
  Link: ({ to, reloadDocument, ...props }: TestLinkProps) => {
    void reloadDocument;
    return <a href={to} {...props} />;
  },
}));

const appConfig = {
  version: "2.99.1",
  branding: { bannerImageUrl: "" },
  helpLinks: [{ label: "Local help", url: "https://help.example.com" }],
  deploymentDescription: "",
  deploymentHelpEmail: null,
};

const defaultHandlers = [
  http.get("/api/v2/users/me", () => HttpResponse.json(currentUserResponse)),
  http.get("/api/v2/config", () => HttpResponse.json(appConfig)),
  http.get("/api/v2/maintenances", () => HttpResponse.json(v2ListEnvelope([]))),
] as const;

function matchMediaWithEnabledFeature(enabledFeature: string | null) {
  return vi.fn(
    (query: string): MediaQueryList => ({
      matches: enabledFeature !== null && query.includes(enabledFeature),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(() => true),
    }),
  );
}

beforeEach(() => {
  vi.stubGlobal("matchMedia", matchMediaWithEnabledFeature(null));
  server.use(...defaultHandlers);
  vi.mocked(useOauthTokenQuery).mockReturnValue({ data: "token" } as ReturnType<typeof useOauthTokenQuery>);
  vi.mocked(useLighthouseSdk).mockReturnValue({ lighthouseReady: false, showLighthouse: vi.fn() });
  vi.mocked(useIsFeatureFlagEnabled).mockReturnValue(true);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderAppBar() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <UIStoreProvider>
        <Suspense fallback={null}>
          <NewAppBar currentPage="workspace" />
        </Suspense>
      </UIStoreProvider>
    </QueryClientProvider>,
  );
}

function renderAccountMenu(ui: ReactElement) {
  render(ui);
}

describe("NewAppBar (MSW-driven)", () => {
  it("derives navigation visibility from the current-user capabilities served by /api/v2/users/me", async () => {
    server.use(
      http.get("/api/v2/users/me", () =>
        HttpResponse.json({
          ...currentUserResponse,
          capabilities: { ...currentUserResponse.capabilities, canUseInventory: false, canViewSystem: true },
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

  it("hides the booking nav link when the booking feature flag is disabled", async () => {
    vi.mocked(useIsFeatureFlagEnabled).mockReturnValue(false);

    renderAppBar();

    const navigation = await screen.findByRole("navigation", { name: "common:appBar.mainLinks" });
    expect(
      within(navigation).queryByRole("link", { name: "common:appBar.sections.booking.title" }),
    ).not.toBeInTheDocument();
  });

  it("shows the maintenance notice when /api/v2/maintenances carries a scheduled window", async () => {
    server.use(
      http.get("/api/v2/maintenances", () =>
        HttpResponse.json(
          v2ListEnvelope([
            {
              id: 1,
              startDate: "2999-01-01T00:00:00.000Z",
            },
          ]),
        ),
      ),
    );

    renderAppBar();

    expect(await screen.findByRole("button", { name: "common:appBar.maintenancePopup" })).toBeInTheDocument();
  });

  it("hides the maintenance notice when /api/v2/maintenances reports none", async () => {
    renderAppBar();

    await screen.findByRole("navigation", { name: "common:appBar.mainLinks" });
    expect(screen.queryByRole("button", { name: "common:appBar.maintenancePopup" })).not.toBeInTheDocument();
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

  it("supplies live-chat data from /api/v2/users/me to the help menu", async () => {
    server.use(
      http.get("/api/v2/users/me", () =>
        HttpResponse.json({ ...currentUserResponse, livechat: { enabled: true, serverKey: "server-key" } }),
      ),
    );

    renderAppBar();

    await screen.findByRole("navigation", { name: "common:appBar.mainLinks" });
    expect(useLighthouseSdk).toHaveBeenCalledWith({
      livechatEnabled: true,
      livechatServerKey: "server-key",
    });
  });

  it.each(["(prefers-contrast: more)", "(forced-colors: active)"])(
    "reports high contrast as enabled for %s",
    async (enabledFeature) => {
      vi.stubGlobal("matchMedia", matchMediaWithEnabledFeature(enabledFeature));
      const user = userEvent.setup();
      renderAppBar();

      await user.click(await screen.findByRole("button", { name: "common:accessibilityTips.buttonLabel" }));

      const dialog = await screen.findByRole("dialog");
      expect(within(dialog).getByText("common:accessibilityTips.highContrast.enabled")).toBeVisible();
    },
  );
});

describe("HelpMenu", () => {
  it("opens custom help links even when Lighthouse is not ready and closes on Escape", async () => {
    const user = userEvent.setup();
    render(
      <HelpMenu
        helpLinks={[{ label: "Local help", url: "https://help.example.com" }]}
        livechatProperties={{ livechatEnabled: false }}
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:helpDocs.openHelp" }));
    expect(screen.getByRole("menuitem", { name: "Local help" })).toHaveAttribute("href", "https://help.example.com");

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("menuitem", { name: "Local help" })).not.toBeInTheDocument();
  });
});

describe("AccountMenu", () => {
  it.each([
    [" Ada ", " Lovelace ", "Ada Lovelace (ada)", "AL"],
    ["Maria", "van den Heuvel", "Maria van den Heuvel (ada)", "MH"],
    [" ", "", "ada (ada)", "A"],
  ])("renders identity from first name '%s' and last name '%s'", async (firstName, lastName, identity, initials) => {
    const user = userEvent.setup();
    renderAccountMenu(
      await wrapWithRealI18n(
        <AccountMenu currentUser={{ ...currentUserResponse, firstName, lastName }} bannerImageUrl="" />,
        { resources: { common: commonEn }, defaultNS: "common" },
      ),
    );

    const accountMenu = screen.getByRole("button", { name: "Account Menu" });
    expect(within(accountMenu).getByText(initials)).toBeVisible();
    await user.click(accountMenu);

    expect(await screen.findByText(identity)).toBeVisible();
  });

  it.each([
    [true, "/groups/viewPIGroup"],
    [false, "/userform"],
  ])("links hasPiRole=%s to the expected profile destination", async (hasPiRole, expectedHref) => {
    const user = userEvent.setup();
    renderAccountMenu(<AccountMenu currentUser={{ ...currentUserResponse, hasPiRole }} bannerImageUrl="" />);

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
          ...currentUserResponse,
          profileImageUrl: "/profile.png",
          orcid: { available: true, id: "0000-0001-2345-6789" },
          session: { ...currentUserResponse.session, operatedAs: true },
        }}
        bannerImageUrl="/public/banner"
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));

    expect(await screen.findByText("common:appBar.operatingAs")).toBeInTheDocument();
    expect(screen.getByText("0000-0001-2345-6789")).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "common:appBar.published" })).toHaveAttribute(
      "href",
      "/public/publishedView/publishedDocuments",
    );
    expect(screen.getByRole("menuitem", { name: "common:appBar.release" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "common:appBar.brandingAlt" })).toHaveAttribute("src", "/public/banner");
  });

  it("clears canonical authentication state when releasing an operated-as session", async () => {
    const user = userEvent.setup();
    const signOut = vi.fn();
    vi.stubGlobal("gapi", { auth2: { getAuthInstance: () => ({ signOut }) } });
    sessionStorage.setItem(ID_TOKEN_KEY, "token");
    renderAccountMenu(
      <AccountMenu
        currentUser={{ ...currentUserResponse, session: { ...currentUserResponse.session, operatedAs: true } }}
        bannerImageUrl=""
      />,
    );

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
    await user.click(await screen.findByRole("menuitem", { name: "common:appBar.release" }));

    expect(sessionStorage.getItem(ID_TOKEN_KEY)).toBeNull();
    expect(signOut).not.toHaveBeenCalled();
  });

  it.each([
    ["gapi", undefined],
    ["auth2", {}],
    ["the auth instance", { auth2: { getAuthInstance: () => null } }],
  ])("logs out an ordinary session without %s", async (_missing, gapi) => {
    const user = userEvent.setup();
    vi.stubGlobal("gapi", gapi);
    sessionStorage.setItem(ID_TOKEN_KEY, "token");
    renderAccountMenu(<AccountMenu currentUser={currentUserResponse} bannerImageUrl="" />);

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
    await user.click(await screen.findByRole("menuitem", { name: "common:appBar.logOut" }));

    expect(sessionStorage.getItem(ID_TOKEN_KEY)).toBeNull();
  });

  it("starts Google sign-out when logging out an ordinary session", async () => {
    const user = userEvent.setup();
    const signOut = vi.fn(() => new Promise<void>(() => {}));
    vi.stubGlobal("gapi", { auth2: { getAuthInstance: () => ({ signOut }) } });
    sessionStorage.setItem(ID_TOKEN_KEY, "token");
    renderAccountMenu(<AccountMenu currentUser={currentUserResponse} bannerImageUrl="" />);

    await user.click(screen.getByRole("button", { name: "common:appBar.accountMenu" }));
    await user.click(await screen.findByRole("menuitem", { name: "common:appBar.logOut" }));

    expect(signOut).toHaveBeenCalledOnce();
    expect(sessionStorage.getItem(ID_TOKEN_KEY)).toBeNull();
  });

  it("uses the profile image for an ordinary session", () => {
    renderAccountMenu(
      <AccountMenu currentUser={{ ...currentUserResponse, profileImageUrl: "/profile.png" }} bannerImageUrl="" />,
    );

    expect(screen.getByAltText("")).toHaveAttribute("src", "/profile.png");
  });
});
