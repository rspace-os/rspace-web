import React from "react";
import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "@/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RaIDConnections from "../RaIDConnections";
import "../../../../../__mocks__/matchMedia";
import type { GroupInfo } from "@/modules/groups/schema";
import type { IntegrationRaidInfo } from "@/modules/raid/schema";
import type { UseSuspenseQueryResult } from "@tanstack/react-query";

type IntegrationRaidInfoData = Extract<
  IntegrationRaidInfo,
  { success: true }
>["data"];

// Mock data
const mockOauthTokenData = "test-token-123";

const createGroup = (
  overrides: Partial<GroupInfo> & Pick<GroupInfo, "id" | "name" | "type">,
): GroupInfo => ({
  globalId: `GR${overrides.id}`,
  sharedFolderId: 1,
  sharedSnippetFolderId: 2,
  members: [],
  raid: null,
  ...overrides,
});

const createIntegrationInfo = (
  overrides: Partial<IntegrationRaidInfo>,
): IntegrationRaidInfo => {
  if (overrides.success === false) {
    return {
      success: false,
      errorMsg: "Integration error",
      error: { errorMessages: [] },
      ...overrides,
    } as IntegrationRaidInfo;
  }

  const baseOptions = ({
    RAID_CONFIGURED_SERVERS: [
      { url: "https://raid.server1.com", alias: "server1" },
    ],
    SERVER_1: {
      RAID_OAUTH_CONNECTED: true,
      RAID_URL: "https://raid.server1.com",
      RAID_ALIAS: "server1",
    },
  } as unknown) as IntegrationRaidInfoData["options"];
  const baseData: IntegrationRaidInfoData = {
    name: "RAID",
    displayName: "RaID",
    available: true,
    enabled: true,
    oauthConnected: true,
    options: baseOptions,
  };

  const { data: overrideData, success: _success, ...rest } =
    overrides as { data?: Partial<IntegrationRaidInfoData>; success?: true };
  const dataOverrides = overrideData ?? {};

  return {
    success: true,
    ...rest,
    data: {
      ...baseData,
      ...dataOverrides,
    },
  };
};

const asOauthTokenResult = (
  token: string,
): UseSuspenseQueryResult<string, Error> =>
  ({ data: token } as UseSuspenseQueryResult<string, Error>);
const asRaidIntegrationResult = (
  data: IntegrationRaidInfo,
): UseSuspenseQueryResult<IntegrationRaidInfo, Error> =>
  ({ data } as UseSuspenseQueryResult<IntegrationRaidInfo, Error>);
const asGroupResult = (
  data: GroupInfo,
): UseSuspenseQueryResult<GroupInfo, Error> =>
  ({ data } as UseSuspenseQueryResult<GroupInfo, Error>);
const asGroupErrorResult = (
  error: Error,
): UseSuspenseQueryResult<GroupInfo, Error> =>
  ({ error } as UseSuspenseQueryResult<GroupInfo, Error>);

const mockIntegrationDataSuccess = createIntegrationInfo({});

const mockIntegrationDataNotAvailable = createIntegrationInfo({
  success: true,
  data: {
    name: "RAID",
    displayName: "RaID",
    available: false,
    enabled: false,
    oauthConnected: false,
    options: {},
  },
});

const mockIntegrationDataNotEnabled = createIntegrationInfo({
  success: true,
  data: {
    name: "RAID",
    displayName: "RaID",
    available: true,
    enabled: false,
    oauthConnected: false,
    options: {},
  },
});

const mockIntegrationDataNoConnectedServers = createIntegrationInfo({
  success: true,
  data: {
    name: "RAID",
    displayName: "RaID",
    available: true,
    enabled: true,
    oauthConnected: false,
    options: ({
      RAID_CONFIGURED_SERVERS: [
        { url: "https://raid.server1.com", alias: "server1" },
      ],
      SERVER_1: {
        RAID_OAUTH_CONNECTED: false,
        RAID_URL: "https://raid.server1.com",
        RAID_ALIAS: "server1",
      },
    } as unknown) as IntegrationRaidInfoData["options"],
  },
});

const mockIntegrationDataError: IntegrationRaidInfo = {
  success: false,
  errorMsg: "Failed to load integration info",
  error: { errorMessages: [] },
};

const mockGroupDataProjectGroup = createGroup({
  id: 12345,
  name: "Test Project Group",
  type: "PROJECT_GROUP",
});

const mockGroupDataLabGroup = createGroup({
  id: 12346,
  name: "Test Lab Group",
  type: "LAB_GROUP",
});

const mockGroupDataProjectGroupWithRaid = createGroup({
  id: 12345,
  name: "Test Project Group",
  type: "PROJECT_GROUP",
  raid: {
    raidServerAlias: "server1",
    raidIdentifier: "https://raid.org/12345",
    raidTitle: "My Research Project",
  },
});

const mockGroupDataLabGroupWithRaid = createGroup({
  id: 12346,
  name: "Test Lab Group",
  type: "LAB_GROUP",
  raid: {
    raidServerAlias: "server1",
    raidIdentifier: "https://raid.org/67890",
    raidTitle: "Previous Lab Project",
  },
});

const mockGroupsError = new Error("Failed to load group info");

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

vi.mock("@/modules/raid/queries", () => ({
  useRaidIntegrationInfoAjaxQuery: vi.fn(),
}));

vi.mock("@/modules/groups/queries", () => ({
  useGetGroupByIdQuery: vi.fn(),
}));

// Mock child components
vi.mock("../RaIDConnectionsEntry", () => {
  return {
    default: function MockRaIDConnectionsEntry({ groupId }: { groupId: string }) {
    return (
      <div data-testid="raid-connections-entry">
        RaID Connections Entry for group {groupId}
      </div>
    );
    },
  };
});

vi.mock("@/components/ErrorBoundary", () => {
  return {
    default: function MockErrorBoundary({ children }: { children: React.ReactNode }) {
    return <div data-testid="error-boundary">{children}</div>;
    },
  };
});

import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
const mockedUseRaidIntegrationInfoAjaxQuery = vi.mocked(useRaidIntegrationInfoAjaxQuery);
const mockedUseGetGroupByIdQuery = vi.mocked(useGetGroupByIdQuery);

const renderWithProviders = (props: { groupId: string }) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <RaIDConnections {...props} />
      </QueryClientProvider>
    </ThemeProvider>
  );
};

describe("RaIDConnections", () => {
  const defaultProps = {
    groupId: "12345",
  };

  beforeEach(() => {
    vi.clearAllMocks();
     
    mockedUseOauthTokenQuery.mockReturnValue(asOauthTokenResult(mockOauthTokenData));
     
  });

  describe("Accessibility", () => {
    it("Should have no axe violations when RaID is available", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      const { baseElement } = renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });

    it("Should have no axe violations when RaID is unavailable", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      const { baseElement } = renderWithProviders(defaultProps);

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });

  describe("Rendering and Layout", () => {
    it("Should render the component title", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByRole("heading", { name: /RaID Connections/i })).toBeInTheDocument();
    });

    it("Should render ErrorBoundary component", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByTestId("error-boundary")).toBeInTheDocument();
    });
  });

  describe("Integration Data Error Handling", () => {
    it("Should display error message when integration data fails to load", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataError));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Error loading RaID integration info: Failed to load integration info/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when integration data fails", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataError));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("Group Data Error Handling", () => {
    it("Should display error message when group data fails to load", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupErrorResult(mockGroupsError));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Error loading group info: Failed to load group info/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when group data fails", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupErrorResult(mockGroupsError));
       
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("RaID Availability States", () => {
    it("Should display unavailable message when RaID is not available", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Please contact your system administrator to enable RaID/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when RaID is not enabled", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotEnabled));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when no RaID servers are connected", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNoConnectedServers));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID has been enabled, but no RaID servers have been connected yet/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when group is not a project group", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataLabGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is disabled for this project - only project groups can have RaID Connections/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when RaID is unavailable", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("RaID Available State", () => {
    it("Should render RaIDConnectionsEntry when all conditions are met", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });
    });

    it("Should pass groupId prop to RaIDConnectionsEntry", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByText(/RaID Connections Entry for group 12345/i)).toBeInTheDocument();
      });
    });

    it("Should not display unavailable message when RaID is available", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });

      expect(screen.queryByText(/RaID is not available/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/RaID is not enabled/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/no RaID servers have been connected/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/only project groups can have RaID Connections/i)).not.toBeInTheDocument();
    });
  });

  describe("hasConnectedServers Logic", () => {
    it("Should correctly identify connected servers", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      // Should render the entry component, indicating hasConnectedServers is true
      expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
    });

    it("Should correctly identify no connected servers when RAID_OAUTH_CONNECTED is false", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNoConnectedServers));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      // Should not render the entry component, indicating hasConnectedServers is false
      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });

    it("Should filter out RAID_CONFIGURED_SERVERS key when checking for connected servers", () => {
      const mockIntegrationDataWithConfiguredServers = createIntegrationInfo({
        success: true,
        data: {
          name: "RAID",
          displayName: "RaID",
          available: true,
          enabled: true,
          oauthConnected: true,
          options: ({
            RAID_CONFIGURED_SERVERS: [
              { url: "https://raid.server1.com", alias: "server1" },
              { url: "https://raid.server2.com", alias: "server2" },
            ],
            SERVER_1: {
              RAID_OAUTH_CONNECTED: true,
              RAID_URL: "https://raid.server1.com",
              RAID_ALIAS: "server1",
            },
          } as unknown) as IntegrationRaidInfoData["options"],
        },
      });

       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataWithConfiguredServers));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      // Should render the entry component, ignoring RAID_CONFIGURED_SERVERS in the check
      expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
    });

    it("Should handle empty options object", () => {
      const mockIntegrationDataEmptyOptions = createIntegrationInfo({
        success: true,
        data: {
          name: "RAID",
          displayName: "RaID",
          available: true,
          enabled: true,
          oauthConnected: false,
          options: {},
        },
      });

       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataEmptyOptions));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      // Should not render the entry component
      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });
  });

  describe("Priority of Unavailable Messages", () => {
    it("Should prioritize non-project group message over other unavailability reasons", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataLabGroup));
       
      renderWithProviders(defaultProps);

      // Should show the non-project group message first
      expect(screen.getByText(/only project groups can have RaID Connections/i)).toBeInTheDocument();
      // Should not show other messages
      expect(screen.queryByText(/RaID is not available for this RSpace instance/i)).not.toBeInTheDocument();
    });

    it("Should show not available message when group is project group but RaID is not available", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should show not enabled message when RaID is available but not enabled", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotEnabled));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should show no connected servers message when enabled but no servers connected", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNoConnectedServers));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });
  });

  describe("Previously Connected RaID Display", () => {
    it("Should display previous RaID connection when group is not a project group", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataLabGroupWithRaid));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is disabled for this project - only project groups can have RaID Connections/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/Previous Lab Project \(https:\/\/raid\.org\/67890\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when RaID is not available", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroupWithRaid));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when RaID is not enabled", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotEnabled));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroupWithRaid));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when no servers are connected", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNoConnectedServers));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroupWithRaid));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should NOT display previous RaID connection when group has no RaID data", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.queryByText(/Previously connected to:/i)).not.toBeInTheDocument();
    });

    it("Should NOT display previous RaID connection when RaID is available", async () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroupWithRaid));
       
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });

      expect(screen.queryByText(/Previously connected to:/i)).not.toBeInTheDocument();
    });

    it("Should display previous RaID connection with empty title", () => {
      const mockGroupDataWithEmptyTitle = createGroup({
        id: 12345,
        name: "Test Project Group",
        type: "PROJECT_GROUP",
        raid: {
          raidServerAlias: "server1",
          raidIdentifier: "https://raid.org/12345",
          raidTitle: "",
        },
      });

       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataNotAvailable));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataWithEmptyTitle));
       
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/\(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });
  });

  describe("Integration with Hooks", () => {
    it("Should call useOauthTokenQuery", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);
       
      expect(useOauthTokenQuery).toHaveBeenCalled();
    });

    it("Should call useRaidIntegrationInfoAjaxQuery", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(useRaidIntegrationInfoAjaxQuery).toHaveBeenCalled();
       
    });

    it("Should call useGetGroupByIdQuery with correct parameters", () => {
       
      mockedUseRaidIntegrationInfoAjaxQuery.mockReturnValue(asRaidIntegrationResult(mockIntegrationDataSuccess));
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataProjectGroup));
       
      renderWithProviders(defaultProps);

      expect(useGetGroupByIdQuery).toHaveBeenCalledWith({
        id: "12345",
        token: mockOauthTokenData,
      });
    });
  });
});
