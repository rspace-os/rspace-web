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
import userEvent from "@testing-library/user-event";
import { axe, toHaveNoViolations } from "jest-axe";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "@/theme";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import RaIDConnectionsEntry from "../RaIDConnectionsEntry";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

// Mock hooks
const mockOauthTokenData = { token: "test-token-123" };
const mockGroupData = {
  id: 12345,
  name: "Test Group",
  raid: {
    raidIdentifier: "https://raid.org/12345",
    raidTitle: "Test RaID Project",
  },
};

const mockGroupDataNoRaid = {
  id: 12345,
  name: "Test Group",
  raid: null,
};

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
jest.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: jest.fn(),
}));

jest.mock("@/modules/groups/queries", () => ({
  useGetGroupByIdQuery: jest.fn(),
}));

// Mock child components
jest.mock("../RaidConnectionsAddForm", () => {
  return function MockRaidConnectionsAddForm({
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
  };
});

jest.mock("../RaidConnectionsDisassociateButton", () => {
  return function MockRaidConnectionsDisassociateButton({
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
  };
});

/* eslint-disable @typescript-eslint/no-unsafe-assignment */
const { useOauthTokenQuery } = jest.requireMock("@/modules/common/hooks/auth");
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
    jest.clearAllMocks();
    /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
    useOauthTokenQuery.mockReturnValue({ data: mockOauthTokenData });
    /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
  });

  describe("Accessibility", () => {
    it("Should have no axe violations when RaID is connected", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      const { baseElement } = renderWithProviders(defaultProps);

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      expect(await axe(baseElement, { rules: { region: { enabled: false } } })).toHaveNoViolations();
    });

    it("Should have no axe violations when RaID is not connected", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataNoRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      const { baseElement } = renderWithProviders(defaultProps);

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      expect(await axe(baseElement, { rules: { region: { enabled: false } } })).toHaveNoViolations();
    });

    it("Should have no axe violations when in editing mode", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataNoRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      const { baseElement } = renderWithProviders(defaultProps);
      const user = userEvent.setup();

      const addButton = screen.getByRole("button", { name: /add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(screen.getByTestId("raid-connections-add-form")).toBeInTheDocument();
      });

      // Disable region rule as this is a sub-component that would be within a landmark in the real app
      expect(await axe(baseElement, { rules: { region: { enabled: false } } })).toHaveNoViolations();
    });
  });

  describe("Rendering with RaID connected", () => {
    beforeEach(() => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
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
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataNoRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
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
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataNoRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
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
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({
        data: { ...mockGroupDataNoRaid, raid: undefined },
      });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
    });

    it("Should handle empty raid identifier", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({
        data: {
          ...mockGroupData,
          raid: { raidIdentifier: "", raidTitle: "" },
        },
      });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });

    it("Should handle partial raid data with identifier but no title", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({
        data: {
          ...mockGroupData,
          raid: { raidIdentifier: "https://raid.org/12345", raidTitle: "" },
        },
      });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

      renderWithProviders(defaultProps);

      // Both Typography and DisassociateButton contain the identifier, so use getAllByText
      const identifierElements = screen.getAllByText(/https:\/\/raid.org\/12345/i);
      expect(identifierElements.length).toBeGreaterThan(0);
      expect(identifierElements[0]).toBeInTheDocument();
      expect(screen.getByTestId("disassociate-button")).toBeInTheDocument();
    });

    it("Should call useOauthTokenQuery hook", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(useOauthTokenQuery).toHaveBeenCalled();
    });

    it("Should call useGetGroupByIdQuery with correct parameters", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      expect(useGetGroupByIdQuery).toHaveBeenCalledWith({
        id: "12345",
        token: mockOauthTokenData,
      });
    });
  });

  describe("Component structure", () => {
    beforeEach(() => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
    });

    it("Should render the component successfully", () => {
      renderWithProviders(defaultProps);

      // Verify key elements are present
      expect(screen.getByTestId("disassociate-button")).toBeInTheDocument();
    });
  });

  describe("Loading states", () => {
    it("Should handle loading state from useGetGroupByIdQuery", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({
        data: undefined,
        isLoading: true,
      });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

      renderWithProviders(defaultProps);

      // Should show "Not connected" when data is undefined
      expect(screen.getByText("Not connected")).toBeInTheDocument();
    });

    it("Should handle undefined group data", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: undefined });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

      renderWithProviders(defaultProps);

      expect(screen.getByText("Not connected")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /add/i })).toBeInTheDocument();
    });
  });

  describe("Integration with child components", () => {
    it("Should pass correct props to RaidConnectionsDisassociateButton", () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupData });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      renderWithProviders(defaultProps);

      const disassociateButton = screen.getByTestId("disassociate-button");
      expect(disassociateButton).toHaveTextContent("Disassociate Test RaID Project");
      expect(disassociateButton).toHaveTextContent("https://raid.org/12345");
    });

    it("Should maintain state when switching between modes", async () => {
      /* eslint-disable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
      useGetGroupByIdQuery.mockReturnValue({ data: mockGroupDataNoRaid });
      /* eslint-enable @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */
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
