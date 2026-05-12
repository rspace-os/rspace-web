import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import StoichiometryTableTypeDropdown from "@/tinyMCE/stoichiometry/table/StoichiometryTableTypeDropdown";

describe("StoichiometryTableTypeDropdown", () => {
  it("has no accessibility violations", async () => {
    const onChangeValue = vi.fn(() => Promise.resolve());
    const onClose = vi.fn();

    const { baseElement } = render(
      <StoichiometryTableTypeDropdown
        rowName="Cyclopentane"
        value="REACTANT"
        onChangeValue={onChangeValue}
        onClose={onClose}
      />,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });

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

