import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { SimplePageWithAppBar } from "../index.story";

vi.mock("@/hooks/api/useWhoAmI", () => ({
  __esModule: true,
  default: () => ({
    tag: "success",
    value: {
      id: 1,
      username: "test",
      firstName: "Test",
      lastName: "User",
      hasPiRole: false,
      hasSysAdminRole: false,
      email: "test@example.com",
      bench: null,
      workbenchId: null,
      getBench: () => Promise.reject(new Error("Not implemented")),
      isCurrentUser: true,
      fullName: "Test User",
      label: "Test User (test)",
    },
  }),
}));

vi.mock("@/hooks/websockets/useWebSocketNotifications", () => ({
  __esModule: true,
  default: () => ({
    notificationCount: 0,
    messageCount: 0,
    specialMessageCount: 0,
  }),
}));

const mockAxios = new MockAdapter(axios);

type UiNavigationData = {
  bannerImgSrc: string;
  visibleTabs: {
    inventory: boolean;
    myLabGroups: boolean;
    published: boolean;
    system: boolean;
  };
  userDetails: {
    username: string;
    fullName: string;
    email: string;
    orcidId: null;
    orcidAvailable: boolean;
    profileImgSrc: null;
    lastSession: string;
  };
  operatedAs: boolean;
  nextMaintenance: null;
};

const baseUiNavigationData: UiNavigationData = {
  bannerImgSrc: "",
  visibleTabs: {
    inventory: true,
    myLabGroups: true,
    published: true,
    system: true,
  },
  userDetails: {
    username: "user1a",
    fullName: "user user",
    email: "user@user.com",
    orcidId: null,
    orcidAvailable: false,
    profileImgSrc: null,
    lastSession: "2025-03-25T15:45:57.000Z",
  },
  operatedAs: false,
  nextMaintenance: null,
};

function setupUiNavigationData(
  overrides: Partial<Omit<UiNavigationData, "visibleTabs" | "userDetails">> & {
    visibleTabs?: Partial<UiNavigationData["visibleTabs"]>;
    userDetails?: Partial<UiNavigationData["userDetails"]>;
  } = {},
) {
  const value = {
    ...baseUiNavigationData,
    ...overrides,
    visibleTabs: {
      ...baseUiNavigationData.visibleTabs,
      ...overrides.visibleTabs,
    },
    userDetails: {
      ...baseUiNavigationData.userDetails,
      ...overrides.userDetails,
    },
  };
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, value);
}

function renderAppBar(
  props: Partial<React.ComponentProps<typeof SimplePageWithAppBar>> = {},
) {
  return render(<SimplePageWithAppBar {...props} />);
}

beforeEach(() => {
  mockAxios.reset();
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {
        return false;
      },
    })),
  );
  mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, { data: "token" });
  mockAxios.onGet("/api/v1/userDetails/whoami").reply(200, {
    id: 1,
    username: "user1a",
    email: "user@user.com",
    firstName: "User",
    lastName: "User",
    hasPiRole: false,
    hasSysAdminRole: false,
    workbenchId: 1,
  });
  mockAxios.onGet("livechatProperties").reply(200, { livechatEnabled: false });
  setupUiNavigationData();
});

describe("AppBar", () => {
  test("shows a visually hidden h1 on tabbed pages", async () => {
    renderAppBar({ variant: "page", currentPage: "Workspace" });

    expect(await screen.findByRole("heading", { level: 1 })).toHaveTextContent(
      "Workspace",
    );
  });

  test("does not show a hidden h1 on non-tabbed pages", async () => {
    renderAppBar({ variant: "page", currentPage: "Test Page" });

    await screen.findByRole("button", { name: "Account Menu" });
    expect(screen.queryByRole("heading", { level: 1 })).not.toBeInTheDocument();
  });

  test("shows a visible dialog heading on dialog variant", async () => {
    renderAppBar({ variant: "dialog", currentPage: "Test Page" });

    expect(await screen.findByRole("heading", { level: 2 })).toHaveTextContent(
      "Test Page",
    );
  });

  test("opens the account menu with profile and logout options", async () => {
    const user = userEvent.setup();
    renderAppBar({ variant: "page", currentPage: "Test Page" });

    await user.click(await screen.findByRole("button", { name: "Account Menu" }));

    const accountMenu = await screen.findByRole("menu", { name: /account menu/i });
    expect(accountMenu).toBeVisible();
    expect(within(accountMenu).getByRole("menuitem", { name: /log out/i })).toBeVisible();
  });

  test("honours visibleTabs configuration", async () => {
    setupUiNavigationData({
      visibleTabs: {
        inventory: false,
        myLabGroups: false,
        published: false,
        system: false,
      },
    });
    const user = userEvent.setup();
    renderAppBar({ variant: "page", currentPage: "Test Page" });

    await screen.findByRole("button", { name: "Account Menu" });
    expect(screen.queryByRole("link", { name: /inventory/i })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /my rspace/i })).toHaveAttribute(
      "href",
      "/userform",
    );
    expect(screen.queryByRole("link", { name: /system/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Account Menu" }));
    expect(screen.queryByRole("menuitem", { name: /published/i })).not.toBeInTheDocument();
  });

  test("points My RSpace to the PI group when myLabGroups is enabled", async () => {
    setupUiNavigationData({ visibleTabs: { myLabGroups: true } });
    renderAppBar({ variant: "page", currentPage: "Test Page" });

    const myRSpaceLink = await screen.findByRole("link", { name: /my rspace/i });
    await vi.waitFor(() => {
      expect(myRSpaceLink).toHaveAttribute("href", "/groups/viewPIGroup");
    });
  });
});
