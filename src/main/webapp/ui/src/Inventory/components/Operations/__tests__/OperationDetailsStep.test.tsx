import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
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
    categories,
  }: {
    disabled?: boolean;
    value: number;
    handleChange: React.ChangeEventHandler<HTMLSelectElement>;
    categories: Array<string>;
  }) => (
    <select
      data-testid="unit-select"
      data-categories={JSON.stringify(categories)}
      disabled={disabled}
      value={value}
      onChange={handleChange}
    >
      <option value={2} />
      <option value={3} />
    </select>
  ),
}));

const operation = {
  key: "derive",
  labelKey: "operations.derive.label",
  descriptionKey: "operations.derive.description",
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

// A Derive-shaped operation that also declares a process-name field (a details-section input).
const processOperation = {
  ...operation,
  inputs: [
    { key: "processName", type: "text", labelKey: "operations.fields.processName", required: true },
    ...operation.inputs,
  ],
  effect: { ...operation.effect, processNameFrom: "processName" },
} as unknown as InventoryOperation;

// A Derive-shaped operation with both a process-name and a derived sample-name field.
const nameOperation = {
  ...operation,
  inputs: [
    { key: "processName", type: "text", labelKey: "operations.fields.processName", required: true },
    { key: "sampleName", type: "text", labelKey: "operations.fields.sampleName", required: true },
    ...operation.inputs,
  ],
  effect: { ...operation.effect, processNameFrom: "processName", nameFrom: "sampleName" },
} as unknown as InventoryOperation;

describe("OperationDetailsStep", () => {
  it("renders the quantity unit dropdowns enabled (user can pick the unit)", () => {
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={() => undefined}
        section="amounts"
      />,
    );
    const selects = screen.getAllByTestId("unit-select");
    expect(selects).toHaveLength(2);
    for (const select of selects) expect(select).not.toBeDisabled();
  });

  it("uses the override category for the created amount but keeps amount-taken on the origin's type", () => {
    const massOrigin = {
      quantity: { numericValue: 10, unitId: 5 },
      quantityCategory: "mass",
    } as unknown as SubSampleModel;
    render(
      <OperationDetailsStep
        operation={operation}
        origin={massOrigin}
        values={values}
        onChange={() => undefined}
        section="amounts"
        unitCategories={["volume"]}
      />,
    );
    const selects = screen.getAllByTestId("unit-select");
    // input order matches the config: eachAmount (created) then amountTaken (removed from the origin)
    expect(selects[0]).toHaveAttribute("data-categories", '["volume"]');
    expect(selects[1]).toHaveAttribute("data-categories", '["mass"]');
  });

  it("renders the amount fields on the amounts section and the names on the details section", () => {
    const { rerender } = render(
      <OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={() => undefined} />,
    );
    expect(screen.getByRole("combobox", { name: /fields\.processName/i })).toBeInTheDocument();
    expect(screen.queryAllByTestId("unit-select")).toHaveLength(0);
    rerender(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={values}
        onChange={() => undefined}
        section="amounts"
      />,
    );
    expect(screen.queryByRole("combobox", { name: /fields\.processName/i })).not.toBeInTheDocument();
    expect(screen.getAllByTestId("unit-select")).toHaveLength(2);
  });

  it("does not allow a negative amount (clamps it to zero)", () => {
    const onChange = vi.fn();
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={onChange}
        section="amounts"
      />,
    );
    fireEvent.change(screen.getAllByRole("spinbutton")[0], { target: { value: "-5" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ eachAmount: { numericValue: 0, unitId: 3 } }));
  });

  it("caps the amount at the maximum so it cannot overflow", () => {
    const onChange = vi.fn();
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={onChange}
        section="amounts"
      />,
    );
    fireEvent.change(screen.getAllByRole("spinbutton")[0], { target: { value: "999999999999999999999" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ eachAmount: { numericValue: 1e9, unitId: 3 } }));
  });

  it("stores the chosen unit on that input without touching its numeric value", async () => {
    const onChange = vi.fn();
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={onChange}
        section="amounts"
      />,
    );
    await userEvent.setup().selectOptions(screen.getAllByTestId("unit-select")[0], "2");
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({
        eachAmount: { numericValue: 5, unitId: 2 },
        amountTaken: { numericValue: 10, unitId: 3 },
      }),
    );
  });

  it("reports a typed process name through onChange (controlled free-solo field)", async () => {
    const onChange = vi.fn();
    render(<OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={onChange} />);
    await userEvent.setup().type(screen.getByRole("combobox", { name: /fields\.processName/i }), "x");
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ processName: "x" }));
  });

  it("shows the single remember checkbox (naming the process) and toggles it", async () => {
    const onRemember = vi.fn();
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={{ ...values, processName: "dna" }}
        onChange={() => undefined}
        remember={false}
        onRememberChange={onRemember}
      />,
    );
    // the label references the chosen process name (values are remembered per process name);
    // anchor the match so it hits the label, not the sibling rememberProcessValuesHelp helper text
    expect(screen.getByText(/rememberProcessValues$/)).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(onRemember).toHaveBeenCalledWith(true);
  });

  it("presents the remember checkbox as a plain control with helper text, not inside an info alert", () => {
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={{ ...values, processName: "dna" }}
        onChange={() => undefined}
        remember={false}
        onRememberChange={vi.fn()}
      />,
    );
    // no coloured info panel (hence no non-interactive info icon) wrapping the checkbox
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    // the explanatory helper text carries the emphasis instead
    expect(screen.getByText(/rememberProcessValuesHelp/)).toBeInTheDocument();
  });

  it("omits the remember checkbox when no handler is provided (e.g. the amounts section)", () => {
    render(
      <OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={() => undefined} />,
    );
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("flags an origin subsample that has an amount of 0 on the details step", () => {
    const zeroOrigin = {
      quantity: { numericValue: 0, unitId: 3 },
      quantityCategory: "volume",
    } as unknown as SubSampleModel;
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={zeroOrigin}
        values={values}
        onChange={() => undefined}
      />,
    );
    const message = screen.getByText(/originAmountZero/);
    expect(message).toBeInTheDocument();
    // shown as a prominent MUI error alert (red background), not easy-to-miss plain text
    expect(message.closest(".MuiAlert-colorError")).not.toBeNull();
  });

  it("does not flag the origin amount when the subsample has a positive quantity", () => {
    render(
      <OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={() => undefined} />,
    );
    expect(screen.queryByText(/originAmountZero/)).not.toBeInTheDocument();
  });

  it("shows an error on the temperature field when it exceeds the configured maximum", () => {
    const cryoOp = {
      ...operation,
      inputs: [{ key: "storageTemp", type: "temperature", labelKey: "operations.fields.storageTemp", maxCelsius: -18 }],
      effect: { ...operation.effect, storageTempFrom: "storageTemp" },
    } as unknown as InventoryOperation;
    const { rerender } = render(
      <OperationDetailsStep
        operation={cryoOp}
        origin={origin}
        values={{ storageTemp: { numericValue: -10, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByText(/storageTempMax/)).toBeInTheDocument();

    // at or below the maximum, no error
    rerender(
      <OperationDetailsStep
        operation={cryoOp}
        origin={origin}
        values={{ storageTemp: { numericValue: -80, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.queryByText(/storageTempMax/)).not.toBeInTheDocument();
  });

  it("disables the derived sample-name field with a hint until a process name is entered", () => {
    const { rerender } = render(
      <OperationDetailsStep
        operation={nameOperation}
        origin={origin}
        values={{ ...values, processName: "", sampleName: "" }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByRole("textbox", { name: /fields\.sampleName/i })).toBeDisabled();
    expect(screen.getByText(/fields\.processNameRequired/)).toBeInTheDocument();
    rerender(
      <OperationDetailsStep
        operation={nameOperation}
        origin={origin}
        values={{ ...values, processName: "dna", sampleName: "A sample dna" }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByRole("textbox", { name: /fields\.sampleName/i })).toBeEnabled();
  });
});

// The "amount to take" modes for a multi-origin operation (adr/0009).
describe("OperationDetailsStep (amount modes)", () => {
  const poolOp = {
    ...operation,
    requiresMultiple: true,
    takeAmountPerSubsample: true,
  } as unknown as InventoryOperation;
  const poolOrigins = [
    { globalId: "SS1", name: "Vial A", quantity: { numericValue: 5, unitId: 3 }, quantityCategory: "volume" },
    { globalId: "SS2", name: "Vial B", quantity: { numericValue: 8, unitId: 3 }, quantityCategory: "volume" },
  ] as unknown as Array<SubSampleModel>;

  const renderPool = (overrides: Partial<React.ComponentProps<typeof OperationDetailsStep>> = {}) =>
    render(
      <OperationDetailsStep
        operation={poolOp}
        origin={origin}
        values={values}
        onChange={() => undefined}
        section="amounts"
        amountMode="same"
        origins={poolOrigins}
        onAmountModeChange={() => undefined}
        perSubsampleAmounts={{}}
        onPerSubsampleAmountsChange={() => undefined}
        {...overrides}
      />,
    );

  it("offers the three amount-to-take modes for a multi-origin operation", () => {
    renderPool();
    expect(screen.getAllByRole("radio")).toHaveLength(3);
  });

  it("does not show the amount-to-take radios for a single-origin operation", () => {
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={() => undefined}
        section="amounts"
      />,
    );
    expect(screen.queryAllByRole("radio")).toHaveLength(0);
  });

  it("reports the chosen mode through onAmountModeChange", async () => {
    const onMode = vi.fn();
    renderPool({ onAmountModeChange: onMode });
    await userEvent.setup().click(screen.getByRole("radio", { name: /amountModePerSubsample/i }));
    expect(onMode).toHaveBeenCalledWith("perSubsample");
  });

  it("shows one amount field per origin in 'per subsample' mode", () => {
    renderPool({ amountMode: "perSubsample" });
    expect(screen.getByRole("spinbutton", { name: /Vial A/ })).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: /Vial B/ })).toBeInTheDocument();
  });
});
