/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { axe, toHaveNoViolations } from "jest-axe";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "@/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RaIDConnections from "../RaIDConnections";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

// Mock data
const mockOauthTokenData = { token: "test-token-123" };

const mockIntegrationDataSuccess = {
  success: true,
  data: {
    available: true,
    enabled: true,
    options: {
      RAID_CONFIGURED_SERVERS: ["server1"],
      SERVER_1: {
        RAID_OAUTH_CONNECTED: "true",
        RAID_SERVER_URL: "https://raid.server1.com",
      },
    },
  },
};

const mockIntegrationDataNotAvailable = {
  success: true,
  data: {
    available: false,
    enabled: false,
    options: {},
  },
};

const mockIntegrationDataNotEnabled = {
  success: true,
  data: {
    available: true,
    enabled: false,
    options: {},
  },
};

const mockIntegrationDataNoConnectedServers = {
  success: true,
  data: {
    available: true,
    enabled: true,
    options: {
      RAID_CONFIGURED_SERVERS: ["server1"],
      SERVER_1: {
        RAID_OAUTH_CONNECTED: false,
        RAID_SERVER_URL: "https://raid.server1.com",
      },
    },
  },
};

const mockIntegrationDataError = {
  success: false,
  errorMsg: "Failed to load integration info",
};

const mockGroupDataProjectGroup = {
  id: 12345,
  name: "Test Project Group",
  type: "PROJECT_GROUP",
};

const mockGroupDataLabGroup = {
  id: 12346,
  name: "Test Lab Group",
  type: "LAB_GROUP",
};

const mockGroupDataProjectGroupWithRaid = {
  id: 12345,
  name: "Test Project Group",
  type: "PROJECT_GROUP",
  raid: {
    raidIdentifier: "https://raid.org/12345",
    raidTitle: "My Research Project",
  },
};

const mockGroupDataLabGroupWithRaid = {
  id: 12346,
  name: "Test Lab Group",
  type: "LAB_GROUP",
  raid: {
    raidIdentifier: "https://raid.org/67890",
    raidTitle: "Previous Lab Project",
  },
};

const mockGroupsError = new Error("Failed to load group info");

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
jest.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: jest.fn(),
}));

jest.mock("@/modules/raid/queries", () => ({
  useRaidIntegrationInfoAjaxQuery: jest.fn(),
}));

jest.mock("@/modules/groups/queries", () => ({
  useGetGroupByIdQuery: jest.fn(),
}));

// Mock child components
jest.mock("../RaIDConnectionsEntry", () => {
  return function MockRaIDConnectionsEntry({ groupId }: { groupId: string }) {
    return (
      <div data-testid="raid-connections-entry">
        RaID Connections Entry for group {groupId}
      </div>
    );
  };
});

jest.mock("@/components/ErrorBoundary", () => {
  return function MockErrorBoundary({ children }: { children: React.ReactNode }) {
    return <div data-testid="error-boundary">{children}</div>;
  };
});

/* eslint-disable @typescript-eslint/no-unsafe-assignment */
const { useOauthTokenQuery } = jest.requireMock("@/modules/common/hooks/auth");
const { useRaidIntegrationInfoAjaxQuery } = jest.requireMock("@/modules/raid/queries");
const { useGetGroupByIdQuery } = jest.requireMock("@/modules/groups/queries");
/* eslint-enable @typescript-eslint/no-unsafe-assignment */

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
    jest.clearAllMocks();
    /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
    useOauthTokenQuery.mockReturnValue({ data: mockOauthTokenData });
    /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
  });

  describe("Accessibility", () => {
    it("Should have no axe violations when RaID is available", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      const { baseElement } = renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      expect(await axe(baseElement, { rules: { region: { enabled: false } } })).toHaveNoViolations();
    });

    it("Should have no axe violations when RaID is unavailable", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      const { baseElement } = renderWithProviders(defaultProps);

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      expect(await axe(baseElement, { rules: { region: { enabled: false } } })).toHaveNoViolations();
    });
  });

  describe("Rendering and Layout", () => {
    it("Should render the component title", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByRole("heading", { name: /RaID Connections/i })).toBeInTheDocument();
    });

    it("Should render ErrorBoundary component", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByTestId("error-boundary")).toBeInTheDocument();
    });
  });

  describe("Integration Data Error Handling", () => {
    it("Should display error message when integration data fails to load", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataError });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Error loading RaID integration info: Failed to load integration info/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when integration data fails", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataError });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("Group Data Error Handling", () => {
    it("Should display error message when group data fails to load", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ error: mockGroupsError });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Error loading group info: Failed to load group info/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when group data fails", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ error: mockGroupsError });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("RaID Availability States", () => {
    it("Should display unavailable message when RaID is not available", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Please contact your system administrator to enable RaID/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when RaID is not enabled", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotEnabled });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when no RaID servers are connected", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNoConnectedServers });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID has been enabled, but no RaID servers have been connected yet/i)).toBeInTheDocument();
    });

    it("Should display unavailable message when group is not a project group", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataLabGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is disabled for this project - only project groups can have RaID Connections/i)).toBeInTheDocument();
    });

    it("Should not render RaIDConnectionsEntry when RaID is unavailable", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
    });
  });

  describe("RaID Available State", () => {
    it("Should render RaIDConnectionsEntry when all conditions are met", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });
    });

    it("Should pass groupId prop to RaIDConnectionsEntry", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByText(/RaID Connections Entry for group 12345/i)).toBeInTheDocument();
      });
    });

    it("Should not display unavailable message when RaID is available", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
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
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      // Should render the entry component, indicating hasConnectedServers is true
      expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
    });

    it("Should correctly identify no connected servers when RAID_OAUTH_CONNECTED is false", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNoConnectedServers });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      // Should not render the entry component, indicating hasConnectedServers is false
      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });

    it("Should filter out RAID_CONFIGURED_SERVERS key when checking for connected servers", () => {
      const mockIntegrationDataWithConfiguredServers = {
        success: true,
        data: {
          available: true,
          enabled: true,
          options: {
            RAID_CONFIGURED_SERVERS: ["server1", "server2"],
            SERVER_1: {
              RAID_OAUTH_CONNECTED: "true",
              RAID_SERVER_URL: "https://raid.server1.com",
            },
          },
        },
      };

      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataWithConfiguredServers });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      // Should render the entry component, ignoring RAID_CONFIGURED_SERVERS in the check
      expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
    });

    it("Should handle empty options object", () => {
      const mockIntegrationDataEmptyOptions = {
        success: true,
        data: {
          available: true,
          enabled: true,
          options: {},
        },
      };

      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataEmptyOptions });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      // Should not render the entry component
      expect(screen.queryByTestId("raid-connections-entry")).not.toBeInTheDocument();
      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });
  });

  describe("Priority of Unavailable Messages", () => {
    it("Should prioritize non-project group message over other unavailability reasons", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataLabGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      // Should show the non-project group message first
      expect(screen.getByText(/only project groups can have RaID Connections/i)).toBeInTheDocument();
      // Should not show other messages
      expect(screen.queryByText(/RaID is not available for this RSpace instance/i)).not.toBeInTheDocument();
    });

    it("Should show not available message when group is project group but RaID is not available", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should show not enabled message when RaID is available but not enabled", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotEnabled });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
    });

    it("Should show no connected servers message when enabled but no servers connected", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNoConnectedServers });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
    });
  });

  describe("Previously Connected RaID Display", () => {
    it("Should display previous RaID connection when group is not a project group", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataLabGroupWithRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is disabled for this project - only project groups can have RaID Connections/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/Previous Lab Project \(https:\/\/raid\.org\/67890\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when RaID is not available", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroupWithRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when RaID is not enabled", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotEnabled });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroupWithRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not enabled for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should display previous RaID connection when no servers are connected", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNoConnectedServers });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroupWithRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/no RaID servers have been connected yet/i)).toBeInTheDocument();
      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/My Research Project \(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });

    it("Should NOT display previous RaID connection when group has no RaID data", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/RaID is not available for this RSpace instance/i)).toBeInTheDocument();
      expect(screen.queryByText(/Previously connected to:/i)).not.toBeInTheDocument();
    });

    it("Should NOT display previous RaID connection when RaID is available", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroupWithRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-entry")).toBeInTheDocument();
      });

      expect(screen.queryByText(/Previously connected to:/i)).not.toBeInTheDocument();
    });

    it("Should display previous RaID connection with empty title", () => {
      const mockGroupDataWithEmptyTitle = {
        id: 12345,
        name: "Test Project Group",
        type: "PROJECT_GROUP",
        raid: {
          raidIdentifier: "https://raid.org/12345",
          raidTitle: "",
        },
      };

      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataNotAvailable });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataWithEmptyTitle });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Previously connected to:/i)).toBeInTheDocument();
      expect(screen.getByText(/\(https:\/\/raid\.org\/12345\)/i)).toBeInTheDocument();
    });
  });

  describe("Integration with Hooks", () => {
    it("Should call useOauthTokenQuery", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);
       
      expect(useOauthTokenQuery).toHaveBeenCalled();
    });

    it("Should call useRaidIntegrationInfoAjaxQuery", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(useRaidIntegrationInfoAjaxQuery).toHaveBeenCalled();
       
    });

    it("Should call useGetGroupByIdQuery with correct parameters", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useRaidIntegrationInfoAjaxQuery.mockReturnValue({ data: mockIntegrationDataSuccess });
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataProjectGroup });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(useGetGroupByIdQuery).toHaveBeenCalledWith({
        id: "12345",
        token: mockOauthTokenData,
      });
    });
  });
});
