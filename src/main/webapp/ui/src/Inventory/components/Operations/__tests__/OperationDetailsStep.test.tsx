import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import OperationDetailsStep from "../OperationDetailsStep";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationInputs } from "../types";

// Stub UnitSelect (real one reads the MobX unitStore) with a native <select> that exposes its
// disabled state and plays a chosen unitId back through handleChange.
vi.mock("@/components/Inputs/UnitSelect", () => ({
  default: ({
    disabled,
    value,
    handleChange,
  }: {
    disabled?: boolean;
    value: number;
    handleChange: React.ChangeEventHandler<HTMLSelectElement>;
  }) => (
    <select data-testid="unit-select" disabled={disabled} value={value} onChange={handleChange}>
      <option value={2} />
      <option value={3} />
    </select>
  ),
}));

const operation = {
  key: "derive",
  labelKey: "operations.derive.label",
  descriptionKey: "operations.derive.description",
  minSelected: 1,
  maxSelected: 1,
  documentationStep: true,
  inputs: [
    { key: "eachAmount", type: "quantity", labelKey: "operations.fields.eachAmount" },
    { key: "amountTaken", type: "quantity", labelKey: "operations.fields.amountTaken" },
  ],
  effect: {
    nameFrom: "sampleName",
    countFrom: "count",
    eachAmountFrom: "eachAmount",
    amountTakenFrom: "amountTaken",
    links: [],
  },
} as unknown as InventoryOperation;

const origin = {
  quantity: { numericValue: 10, unitId: 3 },
  quantityCategory: "volume",
} as unknown as SubSampleModel;

const values: OperationInputs = {
  eachAmount: { numericValue: 5, unitId: 3 },
  amountTaken: { numericValue: 10, unitId: 3 },
};

describe("OperationDetailsStep", () => {
  it("renders the quantity unit dropdowns enabled (user can pick the unit)", () => {
    render(<OperationDetailsStep operation={operation} origin={origin} values={values} onChange={() => undefined} />);
    const selects = screen.getAllByTestId("unit-select");
    expect(selects).toHaveLength(2);
    for (const select of selects) expect(select).not.toBeDisabled();
  });

  it("does not allow a negative amount (clamps it to zero)", () => {
    const onChange = vi.fn();
    render(<OperationDetailsStep operation={operation} origin={origin} values={values} onChange={onChange} />);
    fireEvent.change(screen.getAllByRole("spinbutton")[0], { target: { value: "-5" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ eachAmount: { numericValue: 0, unitId: 3 } }));
  });

  it("caps the amount at the maximum so it cannot overflow", () => {
    const onChange = vi.fn();
    render(<OperationDetailsStep operation={operation} origin={origin} values={values} onChange={onChange} />);
    fireEvent.change(screen.getAllByRole("spinbutton")[0], { target: { value: "999999999999999999999" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ eachAmount: { numericValue: 1e9, unitId: 3 } }));
  });

  it("stores the chosen unit on that input without touching its numeric value", async () => {
    const onChange = vi.fn();
    render(<OperationDetailsStep operation={operation} origin={origin} values={values} onChange={onChange} />);
    // change the first (eachAmount) unit dropdown from ml (3) to l (2)
    await userEvent.setup().selectOptions(screen.getAllByTestId("unit-select")[0], "2");
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        eachAmount: { numericValue: 5, unitId: 2 },
        amountTaken: { numericValue: 10, unitId: 3 },
      }),
    );
  });
});
