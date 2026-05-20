import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen, within } from "@/__tests__/customQueries";
import { SimplePageWithAppBar } from "../index.story";
import { type UiNavigationData } from "../useUiNavigationData";

const mockUseUiNavigationData = vi.fn<
  () => { tag: "success"; value: UiNavigationData }
>();
const mockUseWhoAmI = vi.fn<
  () => {
    tag: "success";
    value: {
      id: number;
      username: string;
      firstName: string;
      lastName: string;
      hasPiRole: boolean;
      hasSysAdminRole: boolean;
      email: string;
      bench: null;
      workbenchId: number;
      getBench: () => Promise<never>;
      isCurrentUser: boolean;
      fullName: string;
      label: string;
    };
  }
>();

vi.mock("../../../hooks/browser/useViewportDimensions", () => ({
  default: () => ({ isViewportSmall: false }),
}));

vi.mock("../../Help/HelpDocs", () => ({
  __esModule: true,
  default: () => <button aria-label="Open Help">Help</button>,
}));

vi.mock("../useUiNavigationData", () => ({
  __esModule: true,
  default: () => mockUseUiNavigationData(),
}));

vi.mock("@/hooks/api/useWhoAmI", () => ({
  __esModule: true,
  default: () => mockUseWhoAmI(),
}));

vi.mock("../../../hooks/websockets/useWebSocketNotifications", () => ({
  __esModule: true,
  default: () => ({
    notificationCount: 0,
    messageCount: 0,
    specialMessageCount: 0,
  }),
}));

function successUiNavigationData(
  overrides: Partial<UiNavigationData> = {},
): { tag: "success"; value: UiNavigationData } {
  return {
    tag: "success",
    value: {
      bannerImgSrc: "/public/banner",
      visibleTabs: {
        inventory: true,
        myLabGroups: true,
        published: false,
        system: false,
      },
      userDetails: {
        username: "user1a",
        fullName: "user user",
        email: "user@user.com",
        orcidId: null,
        orcidAvailable: false,
        profileImgSrc: null,
      },
      extraHelpLinks: [],
      operatedAs: false,
      nextMaintenance: null,
      ...overrides,
    },
  };
}

beforeEach(() => {
  mockUseUiNavigationData.mockReturnValue(successUiNavigationData());
  mockUseWhoAmI.mockReturnValue({
    tag: "success",
    value: {
      id: 1,
      username: "user1a",
      firstName: "user",
      lastName: "user",
      hasPiRole: true,
      hasSysAdminRole: false,
      email: "user@user.com",
      bench: null,
      workbenchId: 1,
      getBench: () => Promise.reject(new Error("Not implemented")),
      isCurrentUser: true,
      fullName: "user user",
      label: "user user (user1a)",
    },
  });
});

describe("AppBar", () => {
  test("shows a hidden h1 for tabbed page variants", () => {
    render(<SimplePageWithAppBar variant="page" currentPage="Inventory" />);

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Inventory",
    );
  });

  test("shows a visible dialog heading for dialog variants", () => {
    render(<SimplePageWithAppBar variant="dialog" currentPage="Test Page" />);

    expect(screen.getByRole("heading", { level: 2 })).toHaveTextContent(
      "Test Page",
    );
  });

  test("shows the profile and logout options when the avatar is clicked", async () => {
    const user = userEvent.setup();

    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    await user.click(screen.getByRole("button", { name: /account menu/i }));

    const menu = screen.getByRole("menu", { name: /account menu/i });
    expect(menu).toHaveTextContent(/user user/i);
    expect(within(menu).getByRole("menuitem", { name: /log out/i })).toBeVisible();
  });

  test("hides the inventory link when visibleTabs.inventory is false", () => {
    mockUseUiNavigationData.mockReturnValue(
      successUiNavigationData({
        visibleTabs: {
          inventory: false,
          myLabGroups: true,
          published: false,
          system: false,
        },
      }),
    );

    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    expect(screen.queryByRole("link", { name: /inventory/i })).not.toBeInTheDocument();
  });

  test("points My RSpace at the PI group page when myLabGroups is enabled", () => {
    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    expect(screen.getByRole("link", { name: /my rspace/i })).toHaveAttribute(
      "href",
      "/groups/viewPIGroup",
    );
  });

  test("points My RSpace at the profile page when myLabGroups is disabled", () => {
    mockUseUiNavigationData.mockReturnValue(
      successUiNavigationData({
        visibleTabs: {
          inventory: true,
          myLabGroups: false,
          published: false,
          system: false,
        },
      }),
    );

    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    expect(screen.getByRole("link", { name: /my rspace/i })).toHaveAttribute(
      "href",
      "/userform",
    );
  });

  test("hides the system link when visibleTabs.system is false", () => {
    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    expect(screen.queryByRole("link", { name: /system/i })).not.toBeInTheDocument();
  });

  test("hides the published menu item when visibleTabs.published is false", async () => {
    const user = userEvent.setup();

    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    await user.click(screen.getByRole("button", { name: /account menu/i }));

    expect(
      screen.queryByRole("menuitem", { name: /published/i }),
    ).not.toBeInTheDocument();
  });

  test("renders the account button before help on page variants", () => {
    render(<SimplePageWithAppBar variant="page" currentPage="Test Page" />);

    const accountButton = screen.getByRole("button", { name: /account menu/i });
    const helpButton = screen.getByRole("button", { name: /open help/i });

    expect(
      Boolean(
        accountButton.compareDocumentPosition(helpButton) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ),
    ).toBe(true);
  });

  test("renders accessibility tips before help on dialog variants", () => {
    render(
      <SimplePageWithAppBar
        variant="dialog"
        currentPage="Test Page"
        accessibilityTips={{ supportsSkipToContent: true }}
      />,
    );

    const accessibilityTipsButton = screen.getByRole("button", {
      name: /accessibility tips/i,
    });
    const helpButton = screen.getByRole("button", { name: /open help/i });

    expect(
      Boolean(
        accessibilityTipsButton.compareDocumentPosition(helpButton) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ),
    ).toBe(true);
  });
});
