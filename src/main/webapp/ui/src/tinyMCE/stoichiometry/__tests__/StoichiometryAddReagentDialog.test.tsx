import { ThemeProvider } from "@mui/material/styles";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import materialTheme from "@/theme";
import StoichiometryAddReagentDialog from "@/tinyMCE/stoichiometry/StoichiometryAddReagentDialog";

function renderDialog(props?: Partial<React.ComponentProps<typeof StoichiometryAddReagentDialog>>) {
  const defaultProps: React.ComponentProps<typeof StoichiometryAddReagentDialog> = {
    open: true,
    onClose: vi.fn(),
    onAddReagent: vi.fn(),
  };

  return {
    ...render(
      <ThemeProvider theme={materialTheme}>
        <StoichiometryAddReagentDialog {...defaultProps} {...props} />
      </ThemeProvider>,
    ),
    props: {
      ...defaultProps,
      ...props,
    },
  };
}

describe("StoichiometryAddReagentDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows validation warnings and blocks submission when required fields are missing", async () => {
    const user = userEvent.setup();
    const onAddReagent = vi.fn();

    renderDialog({ onAddReagent });

    await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));

    expect(onAddReagent).not.toHaveBeenCalled();
    expect(await screen.findByText("common:stoichiometry.addReagent.validation.smilesRequired")).toBeVisible();
    expect(screen.getByText("common:stoichiometry.addReagent.validation.nameRequired")).toBeVisible();
  });

  it("submits the SMILES string and trimmed name, then closes the dialog", async () => {
    const user = userEvent.setup();
    const onAddReagent = vi.fn();
    const onClose = vi.fn();

    renderDialog({ onAddReagent, onClose });

    await user.type(screen.getByRole("textbox", { name: "common:stoichiometry.addReagent.name" }), "  Water  ");
    await user.type(screen.getByRole("textbox", { name: "common:stoichiometry.addReagent.smilesString" }), "O");
    await user.click(screen.getByRole("button", { name: "common:stoichiometry.addReagent.addChemical" }));

    await waitFor(() => {
      expect(onAddReagent).toHaveBeenCalledWith("O", "Water");
    });
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("clears form state when cancelled", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderDialog({ onClose });

    const nameInput = screen.getByRole("textbox", { name: "common:stoichiometry.addReagent.name" });
    const smilesInput = screen.getByRole("textbox", { name: "common:stoichiometry.addReagent.smilesString" });

    await user.type(nameInput, "Acetone");
    await user.type(smilesInput, "CC(=O)C");
    await user.click(screen.getByRole("button", { name: "common:actions.cancel" }));

    expect(onClose).toHaveBeenCalledTimes(1);
    expect(nameInput).toHaveValue("");
    expect(smilesInput).toHaveValue("");
  });
});
