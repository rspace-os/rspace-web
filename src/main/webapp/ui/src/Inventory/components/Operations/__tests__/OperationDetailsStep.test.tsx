import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import React from "react";
import { describe, expect, it, vi } from "vitest";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import OperationDetailsStep from "../OperationDetailsStep";
import type { InventoryOperation } from "../operations";
import type { OperationInputs } from "../types";
import { operations } from "./testOperations";

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

const CELSIUS_UNIT = 8;

const origin = {
  quantity: { numericValue: 10, unitId: 3 },
  quantityCategory: "volume",
} as unknown as SubSampleModel;

const values: OperationInputs = {
  eachAmount: { numericValue: 5, unitId: 3 },
  amountTaken: { numericValue: 10, unitId: 3 },
};
const processOperation = {
  ...operation,
  inputs: [
    { key: "processName", type: "text", labelKey: "operations.fields.processName", required: true },
    ...operation.inputs,
  ],
  effect: { ...operation.effect, processNameFrom: "processName" },
} as unknown as InventoryOperation;
const nameOperation = {
  ...operation,
  inputs: [
    { key: "processName", type: "text", labelKey: "operations.fields.processName", required: true },
    { key: "sampleName", type: "text", labelKey: "operations.fields.sampleName", required: true },
    ...operation.inputs,
  ],
  effect: { ...operation.effect, processNameFrom: "processName", nameFrom: "sampleName" },
} as unknown as InventoryOperation;

/** The derive operation reshaped into a single temperature input, which is all these tests read. */
const tempOp = (bounds: { minCelsius?: number; maxCelsius?: number }): InventoryOperation =>
  ({
    ...operation,
    inputs: [{ key: "storageTemp", type: "temperature", labelKey: "operations.fields.storageTemp", ...bounds }],
    effect: { ...operation.effect, storageTempFrom: "storageTemp" },
  }) as unknown as InventoryOperation;

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

  it("bounds the count input to whole numbers within the definition's own min and max", () => {
    const aliquot = operations.find((o) => o.key === "aliquot");
    if (!aliquot) throw new Error("the aliquot definition must exist in operations");
    const countInput = aliquot.inputs.find((i) => i.key === "count");
    if (!countInput) throw new Error("aliquot must declare a count input");
    render(
      <OperationDetailsStep
        operation={aliquot}
        origin={origin}
        values={{ ...values, count: 2 }}
        onChange={() => undefined}
        section="amounts"
      />,
    );
    const count = screen.getByRole("spinbutton", { name: /fields\.count/i });
    expect(count).toHaveAttribute("min", String(countInput.min));
    expect(count).toHaveAttribute("max", String(countInput.max));
    expect(count).toHaveAttribute("step", "1");
    // and the config really does bound it, or the assertions above pass vacuously
    expect(countInput.max).toBeGreaterThan(0);
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

  it("renders no remember checkbox: it lives on the summary and confirm step", () => {
    render(
      <OperationDetailsStep
        operation={processOperation}
        origin={origin}
        values={{ ...values, processName: "dna" }}
        onChange={() => undefined}
      />,
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
    expect(message.closest(".MuiAlert-colorError")).not.toBeNull();
  });

  it("does not flag the origin amount when the subsample has a positive quantity", () => {
    render(
      <OperationDetailsStep operation={processOperation} origin={origin} values={values} onChange={() => undefined} />,
    );
    expect(screen.queryByText(/originAmountZero/)).not.toBeInTheDocument();
  });

  it("shows an error on the temperature field when it exceeds the configured maximum", () => {
    const cryoOp = tempOp({ maxCelsius: -18 });
    const { rerender } = render(
      <OperationDetailsStep
        operation={cryoOp}
        origin={origin}
        values={{ storageTemp: { numericValue: -10, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByText(/storageTempMax/)).toBeInTheDocument();

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

  it("shows an error on the temperature field when it is below the configured minimum", () => {
    const reviveOp = tempOp({ minCelsius: 4 });
    const { rerender } = render(
      <OperationDetailsStep
        operation={reviveOp}
        origin={origin}
        values={{ storageTemp: { numericValue: 2, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByText(/storageTempMin/)).toBeInTheDocument();

    rerender(
      <OperationDetailsStep
        operation={reviveOp}
        origin={origin}
        values={{ storageTemp: { numericValue: 4, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.queryByText(/storageTempMin/)).not.toBeInTheDocument();
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

describe("OperationDetailsStep (amount modes)", () => {
  const poolOp = {
    ...operation,
    requiresMultiple: true,
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

  it("flags a per-origin amount that exceeds THAT origin, naming only the offending field", async () => {
    renderPool({
      amountMode: "perSubsample",
      // Vial A holds 5, Vial B holds 8: only A is over-drawn.
      perSubsampleAmounts: { SS1: { numericValue: 9, unitId: 3 }, SS2: { numericValue: 1, unitId: 3 } },
    });
    const overDrawn = screen.getByRole("spinbutton", { name: /Vial A/ });
    expect(overDrawn).toBeInvalid();
    expect(screen.getByText(/amountTakenExceedsOrigin/)).toBeInTheDocument();
    expect(screen.getByRole("spinbutton", { name: /Vial B/ })).toBeValid();
  });

  it("reports a typed per-origin amount through onPerSubsampleAmountsChange, keeping the others", async () => {
    const onPerSubsample = vi.fn();
    renderPool({
      amountMode: "perSubsample",
      perSubsampleAmounts: { SS2: { numericValue: 1, unitId: 3 } },
      onPerSubsampleAmountsChange: onPerSubsample,
    });
    fireEvent.change(screen.getByRole("spinbutton", { name: /Vial A/ }), { target: { value: "2" } });
    expect(onPerSubsample).toHaveBeenCalledWith({
      SS1: { numericValue: 2, unitId: 3 },
      SS2: { numericValue: 1, unitId: 3 },
    });
  });

  it("flags any positive per-origin amount for an origin whose quantity was never set", () => {
    const unset = { globalId: "SS9", name: "Vial Z", quantity: null } as unknown as SubSampleModel;
    renderPool({
      amountMode: "perSubsample",
      origins: [poolOrigins[0], unset],
      perSubsampleAmounts: { SS1: { numericValue: 1, unitId: 3 }, SS9: { numericValue: 1, unitId: 3 } },
    });
    expect(screen.getByRole("spinbutton", { name: /Vial Z/ })).toBeInvalid();
    expect(screen.getByRole("spinbutton", { name: /Vial A/ })).toBeValid();
    expect(screen.getByText(/amountTakenExceedsOrigin/)).toBeInTheDocument();
  });
});

describe("OperationDetailsStep count errors and temperature unit", () => {
  const aliquot = operations.find((o) => o.key === "aliquot");
  if (!aliquot) throw new Error("the aliquot definition must exist in operations");
  const renderCount = (count: number, onChange: (v: OperationInputs) => void = () => undefined) =>
    render(
      <OperationDetailsStep
        operation={aliquot}
        origin={origin}
        values={{ ...values, count }}
        onChange={onChange}
        section="amounts"
      />,
    );
  const countField = () => screen.getByRole("spinbutton", { name: /fields\.count/i });

  it("marks a count above the definition's max invalid and names the allowed range", () => {
    renderCount(101);
    expect(countField()).toBeInvalid();
    expect(screen.getByText(/fields\.countRange/)).toBeInTheDocument();
  });

  it("marks a zero count invalid, and a count within range valid with no helper text", () => {
    const { rerender } = renderCount(0);
    expect(countField()).toBeInvalid();
    expect(screen.getByText(/fields\.countRange/)).toBeInTheDocument();
    rerender(
      <OperationDetailsStep
        operation={aliquot}
        origin={origin}
        values={{ ...values, count: 2 }}
        onChange={() => undefined}
        section="amounts"
      />,
    );
    expect(countField()).toBeValid();
    expect(screen.queryByText(/fields\.countRange/)).not.toBeInTheDocument();
  });

  it("reports a cleared count as 0 (Number('') is 0), which the field then flags as out of range", () => {
    const onChange = vi.fn();
    renderCount(2, onChange);
    fireEvent.change(countField(), { target: { value: "" } });
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ count: 0 }));
  });

  it("stores a typed temperature in Celsius (unit 8) without clamping a sub-zero value", () => {
    const cryoOp = tempOp({ maxCelsius: -18 });
    const onChange = vi.fn();
    render(
      <OperationDetailsStep
        operation={cryoOp}
        origin={origin}
        values={{ storageTemp: { numericValue: -80, unitId: CELSIUS_UNIT } }}
        onChange={onChange}
      />,
    );
    fireEvent.change(screen.getByRole("textbox", { name: /fields\.storageTemp/i }), { target: { value: "-20" } });
    expect(onChange).toHaveBeenCalledWith({ storageTemp: { numericValue: -20, unitId: CELSIUS_UNIT } });
  });
});

describe("OperationDetailsStep inline field errors", () => {
  const renderWith = (props: Partial<React.ComponentProps<typeof OperationDetailsStep>>) =>
    render(
      <OperationDetailsStep
        operation={operation}
        origin={origin}
        values={values}
        onChange={() => undefined}
        section="amounts"
        {...props}
      />,
    );

  it("flags an amount taken that exceeds what the origin holds, on that field", () => {
    renderWith({ values: { ...values, amountTaken: { numericValue: 11, unitId: 3 } } });
    expect(screen.getByRole("spinbutton", { name: /fields\.amountTaken/i })).toBeInvalid();
    expect(screen.getByText(/amountTakenExceedsOrigin/)).toBeInTheDocument();
  });

  it("leaves the amount-taken field clean when it is within the origin", () => {
    renderWith({});
    expect(screen.getByRole("spinbutton", { name: /fields\.amountTaken/i })).toBeValid();
    expect(screen.queryByText(/amountTakenExceedsOrigin/)).not.toBeInTheDocument();
  });

  it("prefers the unstorable-temperature message over the out-of-range one on the same field", () => {
    // -300 is below absolute zero AND below the configured minimum of 4.
    const reviveOp = tempOp({ minCelsius: 4, maxCelsius: 120 });
    render(
      <OperationDetailsStep
        operation={reviveOp}
        origin={origin}
        values={{ storageTemp: { numericValue: -300, unitId: CELSIUS_UNIT } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByText(/storageTempInvalid/)).toBeInTheDocument();
    expect(screen.queryByText(/storageTempMin/)).not.toBeInTheDocument();
  });
});

describe("OperationDetailsStep sub-zero temperature entry", () => {
  const cryoOp = tempOp({ maxCelsius: -18 });

  // The wizard feeds every edit straight back down as new props, so the field has to survive that
  // round trip rather than only hold the sign in its own state.
  const Stateful = ({ onChange }: { onChange: (values: OperationInputs) => void }) => {
    const [vals, setVals] = React.useState<OperationInputs>({
      storageTemp: { numericValue: -18, unitId: CELSIUS_UNIT },
    });
    return (
      <OperationDetailsStep
        operation={cryoOp}
        origin={origin}
        values={vals}
        onChange={(next) => {
          setVals(next);
          onChange(next);
        }}
      />
    );
  };

  const tempField = () => screen.getByRole("textbox", { name: /fields\.storageTemp/i });

  it("keeps the minus sign when every digit is deleted", async () => {
    const user = userEvent.setup();
    render(<Stateful onChange={() => undefined} />);
    await user.click(tempField());
    await user.keyboard("{End}{Backspace}{Backspace}");
    expect(tempField()).toHaveValue("-");
  });

  it("reports a temperature stripped back to its sign as incomplete, not as zero", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Stateful onChange={onChange} />);
    await user.click(tempField());
    await user.keyboard("{End}{Backspace}{Backspace}");
    expect(onChange).toHaveBeenLastCalledWith({
      storageTemp: { numericValue: Number.NaN, unitId: CELSIUS_UNIT },
    });
  });

  it("accepts a temperature typed minus sign first", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Stateful onChange={onChange} />);
    await user.clear(tempField());
    await user.type(tempField(), "-80");
    expect(tempField()).toHaveValue("-80");
    expect(onChange).toHaveBeenLastCalledWith({ storageTemp: { numericValue: -80, unitId: CELSIUS_UNIT } });
  });
});
