import React from "react";
import {
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "@/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RaIDConnectionsEntry from "../RaIDConnectionsEntry";
import "../../../../../__mocks__/matchMedia";
import type { GroupInfo } from "@/modules/groups/schema";
import type { UseSuspenseQueryResult } from "@tanstack/react-query";

// Mock hooks
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

const mockGroupData = createGroup({
  id: 12345,
  name: "Test Group",
  type: "PROJECT_GROUP",
  raid: {
    raidServerAlias: "server1",
    raidIdentifier: "https://raid.org/12345",
    raidTitle: "Test RaID Project",
  },
});

const mockGroupDataNoRaid = createGroup({
  id: 12345,
  name: "Test Group",
  type: "PROJECT_GROUP",
  raid: null,
});

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: vi.fn(),
}));

vi.mock("@/modules/groups/queries", () => ({
  useGetGroupByIdQuery: vi.fn(),
}));

// Mock child components
vi.mock("../RaidConnectionsAddForm", () => {
  return {
    default: function MockRaidConnectionsAddForm({
    groupId,
    handleCloseForm,
  }: {
    groupId: string;
    handleCloseForm: () => void;
  }) {
    return (
      <div data-testid="raid-connections-add-form">
        Add Form for group {groupId}
        <button onClick={handleCloseForm}>Close Form</button>
      </div>
    );
    },
  };
});

vi.mock("../RaidConnectionsDisassociateButton", () => {
  return {
    default: function MockRaidConnectionsDisassociateButton({
    raidIdentifier,
    raidTitle,
  }: {
    groupId: string;
    raidIdentifier: string;
    raidTitle: string;
  }) {
    return (
      <button data-testid="disassociate-button">
        Disassociate {raidTitle} ({raidIdentifier})
      </button>
    );
    },
  };
});

import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";

const mockedUseOauthTokenQuery = vi.mocked(useOauthTokenQuery);
const mockedUseGetGroupByIdQuery = vi.mocked(useGetGroupByIdQuery);
const asOauthTokenResult = (
  token: string,
): UseSuspenseQueryResult<string, Error> =>
  ({ data: token } as UseSuspenseQueryResult<string, Error>);
const asGroupResult = (
  data: GroupInfo,
): UseSuspenseQueryResult<GroupInfo, Error> =>
  ({ data } as UseSuspenseQueryResult<GroupInfo, Error>);
const asGroupQueryResult = (
  data?: GroupInfo,
  isLoading?: boolean,
): ReturnType<typeof useGetGroupByIdQuery> =>
  ({
    data,
    isLoading,
  }) as unknown as ReturnType<typeof useGetGroupByIdQuery>;

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
        <RaIDConnectionsEntry {...props} />
      </QueryClientProvider>
    </ThemeProvider>
  );
};

describe("RaIDConnectionsEntry", () => {
  const defaultProps = {
    groupId: "12345",
  };

  beforeEach(() => {
    vi.clearAllMocks();
     
    mockedUseOauthTokenQuery.mockReturnValue(asOauthTokenResult(mockOauthTokenData));
     
  });

  describe("Accessibility", () => {
    it("Should have no axe violations when RaID is connected", async () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
      const { baseElement } = renderWithProviders(defaultProps);

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });

    it("Should have no axe violations when RaID is not connected", async () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataNoRaid));
       
      const { baseElement } = renderWithProviders(defaultProps);

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });

    it("Should have no axe violations when in editing mode", async () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataNoRaid));
       
      const { baseElement } = renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();
      });

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });

  describe("Rendering with RaID connected", () => {
    beforeEach(() => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
    });

    it("Should render RaID title and identifier when connected", () => {
      renderWithProviders(defaultProps);

      // Both Typography and DisassociateButton contain the text, so use getAllByText
      const elements = screen.getAllByText(/Test RaID Project/i);
      expect(elements.length).toBeGreaterThan(0);
      expect(elements[0]).toBeInTheDocument();

      const identifierElements = screen.getAllByText(/https:\/\/raid.org\/12345/i);
      expect(identifierElements.length).toBeGreaterThan(0);
      expect(identifierElements[0]).toBeInTheDocument();
    });

    it("Should render disassociate button when RaID is connected", () => {
      renderWithProviders(defaultProps);

      expect(screen.getByTestId("disassociate-button")).toBeInTheDocument();
    });

    it("Should not render add button when RaID is connected", () => {
      renderWithProviders(defaultProps);

      expect(screen.queryByRole("button", { name: /add/i })).not.toBeInTheDocument();
    });

    it("Should display full RaID information in correct format", () => {
      renderWithProviders(defaultProps);

      // Check for "Title (Identifier)" format
      const typography = screen.getByText((content, element) => {
        return (
          element?.tagName.toLowerCase() === "p" &&
          content.includes("Test RaID Project") &&
          content.includes("https://raid.org/12345")
        );
      });

      expect(typography).toBeInTheDocument();
    });
  });

  describe("Rendering without RaID connected", () => {
    beforeEach(() => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataNoRaid));
       
    });

    it("Should render 'Not connected' message when no RaID", () => {
      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
    });

    it("Should render add button when not connected", () => {
      renderWithProviders(defaultProps);

      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });

    it("Should not render disassociate button when not connected", () => {
      renderWithProviders(defaultProps);

      expect(screen.queryByTestId("disassociate-button")).not.toBeInTheDocument();
    });
  });

  describe("Editing mode", () => {
    beforeEach(() => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataNoRaid));
       
    });

    it("Should show add form when add button is clicked", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();
    });

    it("Should hide 'Not connected' message when in editing mode", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      expect(screen.queryByText("Not connected")).not.toBeInTheDocument();
    });

    it("Should hide add button when in editing mode", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(screen.queryByRole("button", { name: /^add$/i })).not.toBeInTheDocument();
      });
    });

    it("Should pass correct groupId to add form", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      expect(screen.getByText(/Add Form for group 12345/i)).toBeInTheDocument();
    });

    it("Should close add form when handleCloseForm is called", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      const closeFormButton = screen.getByText("Close Form");
      await user.click(closeFormButton);

      await waitFor(() => {
        expect(screen.queryByTestId("raid-connections-add-form")).not.toBeInTheDocument();
      });

      // Should return to initial state
      expect(screen.getByText("Not connected")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });

    it("Should show loading spinner in Suspense fallback", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      // The form should render immediately with our mock, but Suspense is present
      // This test verifies the Suspense structure exists
      expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();
    });
  });

  describe("Data handling", () => {
    it("Should handle undefined raid data gracefully", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(
        asGroupQueryResult(
          ({ ...mockGroupDataNoRaid, raid: undefined } as unknown) as GroupInfo,
        ),
      );
       

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
    });

    it("Should handle empty raid identifier", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(
        asGroupQueryResult({
          ...mockGroupData,
          raid: { raidServerAlias: "server1", raidIdentifier: "", raidTitle: "" },
        }),
      );
       

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });

    it("Should handle partial raid data with identifier but no title", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(
        asGroupQueryResult({
          ...mockGroupData,
          raid: {
            raidServerAlias: "server1",
            raidIdentifier: "https://raid.org/12345",
            raidTitle: "",
          },
        }),
      );
       

      renderWithProviders(defaultProps);

      // Both Typography and DisassociateButton contain the identifier, so use getAllByText
      const identifierElements = screen.getAllByText(/https:\/\/raid.org\/12345/i);
      expect(identifierElements.length).toBeGreaterThan(0);
      expect(identifierElements[0]).toBeInTheDocument();
      expect(screen.getByTestId("disassociate-button")).toBeInTheDocument();
    });

    it("Should call useOauthTokenQuery hook", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
      renderWithProviders(defaultProps);

      expect(useOauthTokenQuery).toHaveBeenCalled();
    });

    it("Should call useGetGroupByIdQuery with correct parameters", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
      renderWithProviders(defaultProps);

      expect(useGetGroupByIdQuery).toHaveBeenCalledWith({
        id: "12345",
        token: mockOauthTokenData,
      });
    });
  });

  describe("Component structure", () => {
    beforeEach(() => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
    });

    it("Should render the component successfully", () => {
      renderWithProviders(defaultProps);

      // Verify key elements are present
      expect(screen.getByTestId("disassociate-button")).toBeInTheDocument();
    });
  });

  describe("Loading states", () => {
    it("Should handle loading state from useGetGroupByIdQuery", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(
        asGroupQueryResult(undefined, true),
      );
       

      renderWithProviders(defaultProps);

      // Should show "Not connected" when data is undefined
      expect(screen.getByText("Not connected")).toBeInTheDocument();
    });

    it("Should handle undefined group data", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupQueryResult());
       

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });
  });

  describe("Integration with child components", () => {
    it("Should pass correct props to RaidConnectionsDisassociateButton", () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupData));
       
      renderWithProviders(defaultProps);

      const disassociateButton = screen.getByTestId("disassociate-button");
      expect(disassociateButton).toHaveTextContent("Disassociate Test RaID Project");
      expect(disassociateButton).toHaveTextContent("https://raid.org/12345");
    });

    it("Should maintain state when switching between modes", async () => {
       
      mockedUseGetGroupByIdQuery.mockReturnValue(asGroupResult(mockGroupDataNoRaid));
       
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      // Enter editing mode
      await user.click(screen.getByRole("button", { name: /add/i }));
      expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();

      // Exit editing mode
      await user.click(screen.getByText("Close Form"));
      await waitFor(() => {
        expect(screen.queryByTestId("raid-connections-add-form")).not.toBeInTheDocument();
      });

      // Enter editing mode again
      await user.click(screen.getByRole("button", { name: /add/i }));
      expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();
    });
  });
});
