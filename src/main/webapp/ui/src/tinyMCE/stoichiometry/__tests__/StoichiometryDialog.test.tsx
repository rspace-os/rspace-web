import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import StoichiometryDialog from "@/tinyMCE/stoichiometry/dialog/StoichiometryDialog";

const mockMutateCalculateStoichiometry = vi.fn();
const mockResetCalculateStoichiometry = vi.fn();
const mockEditableSection = vi.fn();

vi.mock("@/components/DialogBoundary", () => ({
  Dialog: ({
    open,
    children,
    "aria-labelledby": ariaLabelledBy,
  }: {
    open: boolean;
    children: React.ReactNode;
    "aria-labelledby"?: string;
  }) =>
    open ? (
      <div role="dialog" aria-labelledby={ariaLabelledBy}>
        {children}
      </div>
    ) : null,
}));

vi.mock("@/hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({ tag: "success", value: true }),
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({ getToken: vi.fn() }),
}));

vi.mock("@/modules/stoichiometry/mutations", () => ({
  useCalculateStoichiometryMutation: () => ({
    mutate: mockMutateCalculateStoichiometry,
    reset: mockResetCalculateStoichiometry,
    isPending: false,
    isError: false,
    error: null,
  }),
}));

vi.mock("@/tinyMCE/stoichiometry/dialog/EditableStoichiometryDialogSection", () => ({
  default: (props: unknown) => {
    mockEditableSection(props);
    return <div role="region" aria-label="Editable stoichiometry section" />;
  },
}));

vi.mock("../../../components/AppBar", () => ({
  default: () => null,
}));

describe("StoichiometryDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("starts calculation and calls onTableCreated when no table exists", async () => {
    const user = userEvent.setup();
    const onTableCreated = vi.fn();

    mockMutateCalculateStoichiometry.mockImplementation(
      (
        _variables: unknown,
        options?: { onSuccess?: (result: { id: number; revision: number }) => void },
      ) => {
        options?.onSuccess?.({ id: 7, revision: 3 });
      },
    );

    render(
      <StoichiometryDialog
        open
        onClose={() => {}}
        chemId={12345}
        recordId={1}
        stoichiometryId={undefined}
        stoichiometryRevision={undefined}
        onTableCreated={onTableCreated}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Calculate Stoichiometry" }),
    );

    expect(mockResetCalculateStoichiometry).toHaveBeenCalled();
    expect(mockMutateCalculateStoichiometry).toHaveBeenCalledTimes(1);
    const [variables, options] = mockMutateCalculateStoichiometry.mock.calls[0] as [
      { chemId: number; recordId: number },
      { onSuccess?: (result: { id: number; revision: number }) => void } | undefined,
    ];
    expect(variables).toEqual({ chemId: 12345, recordId: 1 });
    expect(options?.onSuccess).toEqual(expect.any(Function));
    expect(onTableCreated).toHaveBeenCalledWith(7, 3);
    await waitFor(() => {
      expect(
        screen.getByRole("region", { name: "Editable stoichiometry section" }),
      ).toBeVisible();
    });
  });

  it("renders the editable section immediately when stoichiometry already exists", () => {
    render(
      <StoichiometryDialog
        open
        onClose={() => {}}
        chemId={12345}
        recordId={1}
        stoichiometryId={1}
        stoichiometryRevision={2}
      />,
    );

    expect(
      screen.getByRole("region", { name: "Editable stoichiometry section" }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: "Calculate Stoichiometry" }),
    ).not.toBeInTheDocument();
    expect(mockEditableSection).toHaveBeenCalledWith(
      expect.objectContaining({
        currentStoichiometry: { id: 1, revision: 2 },
      }),
    );
  });
});
