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
import RaidConnectionsDisassociateButton from "../RaidConnectionsDisassociateButton";
import "@testing-library/jest-dom";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

// TODO: RSDEV-996 Replace with msw once we migrate to Vitest
const mockMutateAsync = jest.fn();
const mockReset = jest.fn();
const mockMutationState: {
  isError: boolean;
  error: Error | null;
} = {
  isError: false,
  error: null,
};

jest.mock("@/modules/raid/mutations", () => ({
  useRemoveRaidIdentifierMutation: jest.fn(() => ({
    mutateAsync: mockMutateAsync,
    reset: mockReset,
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
  raidIdentifier: string;
  raidTitle: string;
}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={queryClient}>
        <RaidConnectionsDisassociateButton {...props} />
      </QueryClientProvider>
    </ThemeProvider>
  );
};

describe("RaidConnectionsDisassociateButton", () => {
  const defaultProps = {
    groupId: "12345",
    raidIdentifier: "https://raid.org/12345",
    raidTitle: "Test RaID Project",
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockMutationState.isError = false;
    mockMutationState.error = null;
    // Setup window.RS for ConfirmationDialog
    if (!window.RS) {
      window.RS = {};
    }
  });

  describe("Accessibility", () => {
    it("Should have no axe violations when dialog is closed", async () => {
      const { baseElement } = renderWithProviders(defaultProps);

      expect(await axe(baseElement)).toHaveNoViolations();
    });

    it("Should have no axe violations when dialog is open", async () => {
      const { baseElement } = renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(await axe(baseElement)).toHaveNoViolations();
    });
  });

  describe("Rendering", () => {
    it("Should render the disassociate button", () => {
      renderWithProviders(defaultProps);

      expect(
        screen.getByRole("button", { name: /disassociate/i })
      ).toBeInTheDocument();
    });

    it("Should render button with error color variant", () => {
      renderWithProviders(defaultProps);

      const button = screen.getByRole("button", { name: /disassociate/i });
      expect(button).toHaveClass("MuiButton-colorError");
    });

    it("Should not show dialog initially", () => {
      renderWithProviders(defaultProps);

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  describe("Dialog Interaction", () => {
    it("Should open confirmation dialog when button is clicked", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });
    });

    it("Should display correct dialog title", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      expect(
        await screen.findByText("Confirm Disassociation")
      ).toBeInTheDocument();
    });

    it("Should display RaID title and identifier in dialog", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(screen.getByText(/Test RaID Project/i)).toBeInTheDocument();
      expect(screen.getByText(/https:\/\/raid.org\/12345/i)).toBeInTheDocument();
    });

    it("Should display warning message in dialog", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(
        screen.getByText(/Are you sure you want to disassociate/i)
      ).toBeInTheDocument();
    });

    it("Should call mutation reset when opening dialog", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      expect(mockReset).toHaveBeenCalledTimes(1);
    });

    it("Should close dialog when cancel action is triggered", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      // Find and click cancel button (implementation depends on ConfirmationDialog)
      const cancelButton = screen.getByRole("button", { name: /cancel/i });
      await user.click(cancelButton);

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });
  });

  describe("Disassociation Flow", () => {
    it("Should call mutateAsync when confirm is clicked", async () => {
      mockMutateAsync.mockResolvedValue({});
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      const confirmButton = screen.getByRole("button", { name: /confirm/i });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledTimes(1);
      });
    });

    it("Should close dialog after successful disassociation", async () => {
      mockMutateAsync.mockResolvedValue({});
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      const confirmButton = screen.getByRole("button", { name: /confirm/i });
      await user.click(confirmButton);

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    it("Should handle multiple open/close cycles", async () => {
      mockMutateAsync.mockResolvedValue({});
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      // First cycle
      await user.click(screen.getByRole("button", { name: /disassociate/i }));
      await waitFor(() => expect(screen.getByRole("dialog")).toBeVisible());

      const cancelButton = screen.getByRole("button", { name: /cancel/i });
      await user.click(cancelButton);
      await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());

      // Second cycle
      await user.click(screen.getByRole("button", { name: /disassociate/i }));
      await waitFor(() => expect(screen.getByRole("dialog")).toBeVisible());

      expect(mockReset).toHaveBeenCalledTimes(2);
    });
  });

  describe("Error Handling", () => {
    it("Should not display error message when mutation is successful", async () => {
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(screen.queryByText(/Error:/i)).not.toBeInTheDocument();
    });

    it("Should display error message when mutation fails", async () => {
      mockMutationState.isError = true;
      mockMutationState.error = new Error("Disassociation failed");
      renderWithProviders(defaultProps);
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));
      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
        expect(screen.getByText(/Error:/i)).toBeInTheDocument();
        expect(screen.getByText(/Disassociation failed/i)).toBeInTheDocument();
      });
    });
  });

  describe("Props Handling", () => {
    it("Should handle different groupId values", () => {
      renderWithProviders({
        ...defaultProps,
        groupId: "67890",
      });

      expect(
        screen.getByRole("button", { name: /disassociate/i })
      ).toBeInTheDocument();
    });

    it("Should handle different raidIdentifier values", async () => {
      const customIdentifier = "https://raid.org/custom-12345";
      renderWithProviders({
        ...defaultProps,
        raidIdentifier: customIdentifier,
      });
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(screen.getByText(new RegExp(customIdentifier, "i"))).toBeInTheDocument();
    });

    it("Should handle different raidTitle values", async () => {
      const customTitle = "My Custom RaID Project";
      renderWithProviders({
        ...defaultProps,
        raidTitle: customTitle,
      });
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(screen.getByText(new RegExp(customTitle, "i"))).toBeInTheDocument();
    });

    it("Should handle special characters in raidTitle", async () => {
      const titleWithSpecialChars = "RaID & Project <Test>";
      renderWithProviders({
        ...defaultProps,
        raidTitle: titleWithSpecialChars,
      });
      const user = userEvent.setup();

      await user.click(screen.getByRole("button", { name: /disassociate/i }));

      await waitFor(() => {
        expect(screen.getByRole("dialog")).toBeVisible();
      });

      expect(screen.getByText(/RaID & Project/i)).toBeInTheDocument();
    });
  });
});
