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

  const processOperation = {
    ...operation,
    inputs: [
      { key: "processName", type: "text", labelKey: "operations.fields.processName", required: true },
      ...operation.inputs,
    ],
    effect: { ...operation.effect, processNameFrom: "processName" },
  } as unknown as InventoryOperation;

  it("reports a typed process name through onChange (controlled free-solo field)", async () => {
    const onChange = vi.fn();
    render(<OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={onChange} />);
    await userEvent.setup().type(screen.getByRole("combobox", { name: /fields\.processName/i }), "x");
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ processName: "x" }));
  });

  it("shows the current process name and a remember checkbox that toggles", async () => {
    const onRemember = vi.fn();
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={{ ...values, processName: "dna" }}
        onChange={() => undefined}
        rememberProcessName={false}
        onRememberProcessNameChange={onRemember}
      />,
    );
    // the process-name field reflects the current value (so it survives navigation within a run)
    expect(screen.getByRole("combobox", { name: /fields\.processName/i })).toHaveValue("dna");
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(onRemember).toHaveBeenCalledWith(true);
  });

  it("omits the process-name remember checkbox when no handler is provided", () => {
    render(
      <OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={() => undefined} />,
    );
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("shows an amounts remember checkbox that names the process and toggles", async () => {
    const onRememberAmounts = vi.fn();
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={{ ...values, processName: "dna" }}
        onChange={() => undefined}
        rememberAmounts
        onRememberAmountsChange={onRememberAmounts}
      />,
    );
    // the label references the chosen process name (persisted per process name)
    expect(screen.getByText(/rememberAmountsForProcess/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(onRememberAmounts).toHaveBeenCalledWith(false);
  });

  it("uses a generic amounts remember label when the operation has no process name", () => {
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={() => undefined}
        onRememberAmountsChange={() => undefined}
      />,
    );
    expect(screen.getByText(/fields\.rememberAmounts$/)).toBeInTheDocument();
  });
});
