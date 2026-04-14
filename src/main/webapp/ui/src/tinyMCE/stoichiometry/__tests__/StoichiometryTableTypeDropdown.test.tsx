import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import StoichiometryTableTypeDropdown from "@/tinyMCE/stoichiometry/table/StoichiometryTableTypeDropdown";

describe("StoichiometryTableTypeDropdown", () => {
  it("commits the selected type through its change callback", async () => {
    const user = userEvent.setup();
    const onChangeValue = vi.fn(() => Promise.resolve());
    const onClose = vi.fn();

    render(
      <StoichiometryTableTypeDropdown
        rowName="Cyclopentane"
        value="REACTANT"
        onChangeValue={onChangeValue}
        onClose={onClose}
      />,
    );

    await user.click(
      screen.getByRole("combobox", {
        name: "Select type for Cyclopentane",
      }),
    );
    await user.click(await screen.findByRole("option", { name: "Product" }));

    await waitFor(() => {
      expect(onChangeValue).toHaveBeenCalledWith("PRODUCT");
    });
    expect(onClose).toHaveBeenCalled();
  });
});

