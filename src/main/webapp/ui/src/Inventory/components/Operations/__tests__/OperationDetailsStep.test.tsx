import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, it, vi } from "vitest";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import OperationDetailsStep from "../OperationDetailsStep";
import type { InventoryOperation } from "../operationsConfig";
import type { OperationInputs } from "../types";
import { operations } from "./testOperations";

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

const CELSIUS_UNIT = 8;

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

  it("bounds the count input to whole numbers within the definition's own min and max", () => {
    // The count decides how many subsamples the operation creates; the backend rejects a
    // fractional count and caps it (DevDocs/adr/0007), so the input must not invite a value the
    // request builder would then throw on (code review, finding 12).
    //
    // Driven by the REAL aliquot definition, not a hand-written fixture: the cap used to be a
    // frontend constant, so lowering it server-side left the wizard offering counts the endpoint
    // rejects, with the 400 arriving only at Perform (parallel review, FE9). The fixture this test
    // used carried no max at all, which is how the drift went unnoticed.
    const aliquot = operations.find((o) => o.key === "aliquot");
    if (!aliquot) throw new Error("the aliquot definition must exist in operations_config.json");
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

  it("shows an error on the temperature field when it is below the configured minimum", () => {
    const reviveOp = {
      ...operation,
      inputs: [{ key: "storageTemp", type: "temperature", labelKey: "operations.fields.storageTemp", minCelsius: 4 }],
      effect: { ...operation.effect, storageTempFrom: "storageTemp" },
    } as unknown as InventoryOperation;
    const { rerender } = render(
      <OperationDetailsStep
        operation={reviveOp}
        origin={origin}
        values={{ storageTemp: { numericValue: 2, unitId: 8 } }}
        onChange={() => undefined}
      />,
    );
    expect(screen.getByText(/storageTempMin/)).toBeInTheDocument();

    // at or above the minimum, no error
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

// The "amount to take" modes for a multi-origin operation (DevDocs/adr/0007).
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

  it("flags a per-origin amount that exceeds THAT origin, naming only the offending field", async () => {
    // The per-origin over-removal check and its wiring into error/helperText had no test: the
    // predicate was unit-tested in isolation, but nothing asserted the message reaches the field,
    // and the callback was stubbed as () => undefined everywhere so the field's own change path was
    // unexercised too (parallel review, Q15).
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
});

describe("OperationDetailsStep inline field errors", () => {
  // Each predicate below is unit-tested in operationValidation.test.ts; what was untested is that
  // its outcome reaches the FIELD, and that the four-way helper-text precedence picks the right one
  // (parallel review, Q15).
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
    // the origin holds 10; take 11
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
    // -300 is below absolute zero AND below the configured minimum: the precedence chain puts
    // storageTempInvalid first, because a value the backend cannot store at all is the more
    // specific complaint.
    const reviveOp = {
      ...operation,
      inputs: [
        {
          key: "storageTemp",
          type: "temperature",
          labelKey: "operations.fields.storageTemp",
          minCelsius: 4,
          maxCelsius: 120,
        },
      ],
      effect: { ...operation.effect, storageTempFrom: "storageTemp" },
    } as unknown as InventoryOperation;
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
