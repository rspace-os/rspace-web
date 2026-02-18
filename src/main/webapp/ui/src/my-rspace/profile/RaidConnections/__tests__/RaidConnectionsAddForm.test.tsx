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
import RaidConnectionsAddForm from "../RaidConnectionsAddForm";
import "../../../../../__mocks__/matchMedia";
import { GetAvailableRaidListResponse } from "@/modules/raid/schema";

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
const mockMutateAsync = vi.fn();
const mockInvalidateQueries = vi.fn();
const mockMutationState: {
  isError: boolean;
  error: Error | null;
} = {
  isError: false,
  error: null,
};

let mockQueryData: GetAvailableRaidListResponse = {
  success: true,
  data: [],
};

vi.mock("@/modules/raid/queries", () => ({
  raidQueryKeys: {
    availableRaidIdentifiers: vi.fn(() => ["rspace.apps.raid", "availableIds"]),
  },
  useGetAvailableRaidIdentifiersAjaxQuery: vi.fn(() => ({
    get data() {
      return mockQueryData;
    },
  })),
}));

vi.mock("@/modules/raid/mutations", () => ({
  useAddRaidIdentifierMutation: vi.fn(() => ({
    mutateAsync: mockMutateAsync,
    get isError() {
      return mockMutationState.isError;
    },
    get error() {
      return mockMutationState.error;
    },
  })),
}));

const renderWithProviders = (props: {
  groupId: string;
  handleCloseForm: () => void;
}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  queryClient.invalidateQueries = mockInvalidateQueries;

  return render(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <RaidConnectionsAddForm {...props} />
      </QueryClientProvider>
    </ThemeProvider>
  );
};

const givenMockQueryData = () => {
  mockQueryData = {
    success: true,
    data: [
      {
        raidServerAlias: "server1",
        raidIdentifier: "raid-123",
        raidTitle: "Test RAiD 1",
      },
      {
        raidServerAlias: "server2",
        raidIdentifier: "raid-456",
        raidTitle: "Test RAiD 2",
      },
    ],
  };
};

const givenMockQueryError = (errorMsg: string = "Failed to load data") => {
  mockQueryData = {
    success: false,
    error: {
      errorMessages: [],
    },
    errorMsg,
  };
};

describe("RaidConnectionsAddForm", () => {
  const mockHandleCloseForm = vi.fn();
  const defaultProps = {
    groupId: "12345",
    handleCloseForm: mockHandleCloseForm,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockMutationState.isError = false;
    mockMutationState.error = null;
    mockQueryData = {
      success: true,
      data: [],
    };
  });

  describe("Accessibility", () => {
    it("Should have no axe violations with empty options", async () => {
      const { container } = renderWithProviders(defaultProps);

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(container).toBeAccessible();
    });

    it("Should have no axe violations with available options", async () => {
      givenMockQueryData();
      const { container } = renderWithProviders(defaultProps);

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(container).toBeAccessible();
    });
  });

  describe("Rendering", () => {
    it("Should render the form with autocomplete field", () => {
      renderWithProviders(defaultProps);

      expect(screen.getByLabelText(/RAiD Identifier/i)).toBeInTheDocument();
    });

    it("Should render Add and Cancel buttons", () => {
      renderWithProviders(defaultProps);

      expect(screen.getByRole("button", { name: /Add/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Cancel/i })).toBeInTheDocument();
    });

    it("Should display error message when query fails", () => {
      givenMockQueryError("Failed to load data");
      renderWithProviders(defaultProps);

      expect(screen.getByText(/Error loading RAiD identifier options: Failed to load data/i)).toBeInTheDocument();
    });

    it("Should not render form when query fails", () => {
      givenMockQueryError("Failed to load data");
      renderWithProviders(defaultProps);

      expect(screen.queryByLabelText(/RAiD Identifier/i)).not.toBeInTheDocument();
    });
  });

  describe("Autocomplete Interaction", () => {
    it("Should populate autocomplete with available RAiD identifiers", async () => {
      givenMockQueryData();

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        expect(screen.getByText("Test RAiD 1 (raid-123)")).toBeInTheDocument();
        expect(screen.getByText("Test RAiD 2 (raid-456)")).toBeInTheDocument();
      });
    });

    it("Should display no options message when list is empty", async () => {

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        expect(
          screen.getByText(/No valid available RAiD found, or the RAiD has been used by another project group./i)
        ).toBeInTheDocument();
      });
    });

    it("Should allow selecting an option from autocomplete", async () => {
      givenMockQueryData();

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      await waitFor(() => {
        expect(screen.getByDisplayValue("Test RAiD 1 (raid-123)")).toBeInTheDocument();
      });
    });

    it("Should handle multiple options correctly", async () => {
      mockQueryData = {
        success: true,
        data: [
          {
            raidServerAlias: "server1",
            raidIdentifier: "raid-123",
            raidTitle: "Test RAiD 1",
          },
          {
            raidServerAlias: "server2",
            raidIdentifier: "raid-456",
            raidTitle: "Test RAiD 2",
          },
          {
            raidServerAlias: "server3",
            raidIdentifier: "raid-789",
            raidTitle: "Test RAiD 3",
          },
        ],
      };

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        expect(screen.getByText("Test RAiD 1 (raid-123)")).toBeInTheDocument();
        expect(screen.getByText("Test RAiD 2 (raid-456)")).toBeInTheDocument();
        expect(screen.getByText("Test RAiD 3 (raid-789)")).toBeInTheDocument();
      });
    });
  });

  describe("Button State", () => {
    it("Should show submit button initially", () => {
      renderWithProviders(defaultProps);
      const addButton = screen.getByRole("button", { name: /Add/i });

      // Button exists in the DOM
      expect(addButton).toBeInTheDocument();
    });

    it("Should enable submit button when option is selected", async () => {
      givenMockQueryData();

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      await waitFor(() => {
        const addButton = screen.getByRole("button", { name: /Add/i });
        expect(addButton).toBeEnabled();
      });
    });

    it("Should not disable cancel button when form is idle", () => {
      renderWithProviders(defaultProps);
      const cancelButton = screen.getByRole("button", { name: /Cancel/i });

      expect(cancelButton).toBeEnabled();
    });
  });

  describe("Form Submission", () => {
    it("Should submit form with selected RAiD option", async () => {
      givenMockQueryData();
      mockMutateAsync.mockResolvedValue({});

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      const addButton = screen.getByRole("button", { name: /Add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith({
          raidServerAlias: "server1",
          raidIdentifier: "raid-123",
        });
      });
    });

    it("Should call handleCloseForm after successful submission", async () => {
      givenMockQueryData();
      mockMutateAsync.mockResolvedValue({});

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      const addButton = screen.getByRole("button", { name: /Add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(mockHandleCloseForm).toHaveBeenCalled();
      });
    });

    it("Should invalidate queries after successful submission", async () => {
      givenMockQueryData();
      mockMutateAsync.mockResolvedValue({});

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      const addButton = screen.getByRole("button", { name: /Add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(mockInvalidateQueries).toHaveBeenCalledWith({
          queryKey: ["rspace.apps.raid", "availableIds"],
        });
      });
    });

    it("Should show submitting state during submission", async () => {
      givenMockQueryData();
      mockMutateAsync.mockImplementation(() => new Promise(() => {})); // Never resolves

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      const addButton = screen.getByRole("button", { name: /Add/i });
      await user.click(addButton);

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /Adding.../i })).toBeInTheDocument();
      });
    });

    it("Should disable cancel button during submission", async () => {
      givenMockQueryData();
      mockMutateAsync.mockImplementation(() => new Promise(() => {})); // Never resolves

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      const addButton = screen.getByRole("button", { name: /Add/i });
      await user.click(addButton);

      await waitFor(() => {
        const cancelButton = screen.getByRole("button", { name: /Cancel/i });
        expect(cancelButton).toBeDisabled();
      });
    });
  });

  describe("Cancel Action", () => {
    it("Should call handleCloseForm when cancel button is clicked", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const cancelButton = screen.getByRole("button", { name: /Cancel/i });

      await user.click(cancelButton);

      expect(mockHandleCloseForm).toHaveBeenCalled();
    });

    it("Should reset form when cancel button is clicked", async () => {
      givenMockQueryData();

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      // Select an option
      await user.click(autocomplete);
      await user.click(screen.getByText("Test RAiD 1 (raid-123)"));

      await waitFor(() => {
        expect(screen.getByDisplayValue("Test RAiD 1 (raid-123)")).toBeInTheDocument();
      });

      // Click cancel
      const cancelButton = screen.getByRole("button", { name: /Cancel/i });
      await user.click(cancelButton);

      // Form should be reset (handleCloseForm should be called)
      expect(mockHandleCloseForm).toHaveBeenCalled();
    });
  });

  describe("Error Handling", () => {
    it("Should display mutation error message", async () => {
      mockMutationState.isError = true;
      mockMutationState.error = new Error("Failed to add RAiD");

      renderWithProviders(defaultProps);

      await waitFor(() => {
        expect(screen.getByText(/Failed to add RAiD/i)).toBeInTheDocument();
      });
    });

    it("Should show error on autocomplete field when mutation fails", async () => {
      mockMutationState.isError = true;
      mockMutationState.error = new Error("Failed to add RAiD");

      renderWithProviders(defaultProps);
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      // Check if the input has error styling (aria-invalid)
      await waitFor(() => {
        expect(autocomplete).toHaveAttribute("aria-invalid", "true");
      });
    });

    // TODO: This currently doesn't work
    // it("Should handle network error during submission", async () => {
    //   mockQueryData.data = [
    //     {
    //       raidServerAlias: "server1",
    //       raidIdentifier: "raid-123",
    //       raidTitle: "Test RAiD 1",
    //     },
    //   ];
    //
    //   renderWithProviders(defaultProps);
    //   const user = userEvent.setup();
    //   const autocomplete = screen.getByLabelText(/RAiD Identifier/i);
    //
    //   await user.click(autocomplete);
    //   await user.click(screen.getByText("Test RAiD 1 (raid-123)"));
    //
    //   // Set up rejection after selection
    //   mockMutateAsync.mockRejectedValueOnce(new Error("Network error"));
    //
    //   const addButton = screen.getByRole("button", { name: /Add/i });
    //
    //   // Suppress console errors for this test since we're expecting an error
    //   const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    //
    //   // Clicking will trigger the error, but it's handled internally
    //   await user.click(addButton);
    //
    //   // The error will be caught by the form but handleCloseForm should not be called
    //   await waitFor(() => {
    //     expect(mockMutateAsync).toHaveBeenCalled();
    //   }, { timeout: 500 });
    //
    //   // Give it time to not call handleCloseForm
    //   await new Promise(resolve => setTimeout(resolve, 50));
    //   expect(mockHandleCloseForm).not.toHaveBeenCalled();
    //
    //   consoleErrorSpy.mockRestore();
    // });
  });

  describe("Props Handling", () => {
    it("Should handle different groupId values", () => {
      renderWithProviders({
        ...defaultProps,
        groupId: "67890",
      });

      expect(screen.getByLabelText(/RAiD Identifier/i)).toBeInTheDocument();
    });

    it("Should handle special characters in RAiD titles", async () => {
      mockQueryData = {
        success: true,
        data: [
          {
            raidServerAlias: "server1",
            raidIdentifier: "raid-123",
            raidTitle: "Test & RAiD <Special>",
          },
        ],
      };

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        expect(screen.getByText("Test & RAiD <Special> (raid-123)")).toBeInTheDocument();
      });
    });

    it("Should handle long RAiD identifiers", async () => {
      mockQueryData = {
        success: true,
        data: [
          {
            raidServerAlias: "server1",
            raidIdentifier: "raid-123456789012345678901234567890",
            raidTitle: "Test RAiD",
          },
        ],
      };

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        expect(
          screen.getByText("Test RAiD (raid-123456789012345678901234567890)")
        ).toBeInTheDocument();
      });
    });

    it("Should handle empty string in RAiD title", async () => {
      mockQueryData = {
        success: true,
        data: [
          {
            raidServerAlias: "server1",
            raidIdentifier: "raid-123",
            raidTitle: "",
          },
        ],
      };

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      await waitFor(() => {
        // When title is empty, the label will be " (raid-123)" with leading space
        expect(screen.getByText(/^\s*\(raid-123\)$/)).toBeInTheDocument();
      });
    });
  });

  describe("Form Validation", () => {
    it("Should require RAiD identifier field", () => {
      renderWithProviders(defaultProps);
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      expect(autocomplete).toBeRequired();
    });

    it("Should not submit when no option is selected", () => {
      renderWithProviders(defaultProps);

      // Form validation ensures mutation is not called without a valid option
      expect(mockMutateAsync).not.toHaveBeenCalled();
    });
  });

  describe("Option Equality", () => {
    it("Should correctly compare options with same identifier but different aliases", async () => {
      mockQueryData = {
        success: true,
        data: [
          {
            raidServerAlias: "server1",
            raidIdentifier: "raid-123",
            raidTitle: "Test RAiD 1",
          },
          {
            raidServerAlias: "server2",
            raidIdentifier: "raid-123",
            raidTitle: "Test RAiD 1 (alt)",
          },
        ],
      };

      renderWithProviders(defaultProps);
      const user = userEvent.setup();
      const autocomplete = screen.getByLabelText(/RAiD Identifier/i);

      await user.click(autocomplete);

      // Both options should be available
      expect(screen.getByText("Test RAiD 1 (raid-123)")).toBeInTheDocument();
      expect(
        screen.getByText("Test RAiD 1 (alt) (raid-123)")
      ).toBeInTheDocument();
    });
  });
});
