import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

// Shared, controllable preference store standing in for useUiPreference's persisted UI settings, so
// the test can assert exactly what Perform persisted (and keyed by which process name).
const prefs = vi.hoisted(() => ({ store: {} as Record<string, unknown> }));

vi.mock("@/hooks/api/useUiPreference", () => ({
  PREFERENCES: {
    INVENTORY_OPERATION_TEMPLATE_DEFAULTS: Symbol.for("INVENTORY_OPERATION_TEMPLATE_DEFAULTS"),
    INVENTORY_OPERATION_DOC_DEFAULTS: Symbol.for("INVENTORY_OPERATION_DOC_DEFAULTS"),
    INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
    INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
    INVENTORY_OPERATION_AMOUNT_DEFAULTS: Symbol.for("INVENTORY_OPERATION_AMOUNT_DEFAULTS"),
  },
  default: (pref: symbol, opts: { defaultValue: unknown }) => {
    const key = Symbol.keyFor(pref) ?? "";
    const value = key in prefs.store ? prefs.store[key] : opts.defaultValue;
    return [value, (v: unknown) => (prefs.store[key] = v)];
  },
}));

const performOperation = vi.fn((_req: unknown) => Promise.resolve({ id: 1, globalId: "SS9", name: "New" }));
vi.mock("../operationsApi", () => ({
  performOperation: (req: unknown) => performOperation(req),
  createTemplateFromOriginSample: () => Promise.resolve(999),
}));

const performSearch = vi.fn();
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: { search: { performSearch } },
    uiStore: { addAlert: vi.fn() },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));
vi.mock("@/util/alerts", () => ({ showToastWhilstPending: (_msg: string, p: Promise<unknown>) => p }));
vi.mock("@/stores/contexts/Alert", () => ({ mkAlert: (x: unknown) => x }));
vi.mock("@/components/SubmitSpinnerButton", () => ({
  default: ({ onClick, label, disabled }: { onClick: () => void; label: string; disabled?: boolean }) => (
    <button type="button" onClick={onClick} disabled={disabled}>
      {label}
    </button>
  ),
}));
// ContextDialog wraps the content in a MUI Dialog; render its children inline when open.
vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

// Stub the step bodies so the flow can be driven deterministically without the real inputs' stores.
// The stub ignores `section` for rendering (it shows every field regardless) so a test can read the
// amount values while still on the details step; it echoes `section` and `unitCategories` back via
// spans so the step split and the amount-unit category can be asserted.
vi.mock("../OperationDetailsStep", () => ({
  default: ({
    values,
    onChange,
    section,
    unitCategories,
    rememberProcessName,
    onRememberProcessNameChange,
    rememberAmounts,
    onRememberAmountsChange,
  }: {
    values: Record<string, unknown>;
    onChange: (v: Record<string, unknown>) => void;
    section?: string;
    unitCategories?: Array<string>;
    rememberProcessName?: boolean;
    onRememberProcessNameChange?: (r: boolean) => void;
    rememberAmounts?: boolean;
    onRememberAmountsChange?: (r: boolean) => void;
  }) => (
    <div>
      <span data-testid="section">{String(section)}</span>
      <span data-testid="unit-categories">{JSON.stringify(unitCategories ?? null)}</span>
      <input
        data-testid="proc"
        value={String(values.processName ?? "")}
        onChange={(e) => onChange({ ...values, processName: e.target.value })}
      />
      <button
        type="button"
        data-testid="fill-details"
        onClick={() =>
          onChange({
            ...values,
            sampleName: "New material",
            count: 1,
            eachAmount: { numericValue: 5, unitId: 3 },
            amountTaken: { numericValue: 1, unitId: 3 },
          })
        }
      />
      <span data-testid="remember-proc">{String(rememberProcessName)}</span>
      <button type="button" data-testid="toggle-remember-proc" onClick={() => onRememberProcessNameChange?.(true)} />
      <span data-testid="count">{String(values.count ?? "")}</span>
      <span data-testid="each-amount">{JSON.stringify(values.eachAmount ?? null)}</span>
      <span data-testid="amount-taken">{JSON.stringify(values.amountTaken ?? null)}</span>
      <span data-testid="remember-amounts">{String(rememberAmounts)}</span>
      <button
        type="button"
        data-testid="toggle-remember-amounts"
        onClick={() => onRememberAmountsChange?.(!rememberAmounts)}
      />
    </div>
  ),
}));
vi.mock("../TemplateStep", () => ({
  default: ({
    value,
    onChange,
  }: {
    value: { mode: string; templateId: number | null };
    onChange: (v: unknown) => void;
  }) => (
    <div>
      <span data-testid="tmpl-mode">{value.mode}</span>
      <span data-testid="tmpl-id">{String(value.templateId)}</span>
      <button
        type="button"
        data-testid="tmpl-pick5"
        onClick={() => onChange({ mode: "pick", templateId: 5, templateName: "T5", remember: true })}
      />
      <button
        type="button"
        data-testid="tmpl-pick-volume"
        onClick={() =>
          onChange({ mode: "pick", templateId: 7, templateName: "T7", quantityCategory: "volume", remember: true })
        }
      />
    </div>
  ),
}));
vi.mock("../DocumentationStep", () => ({
  default: ({
    onChange,
    onRememberChange,
  }: {
    onChange: (v: unknown) => void;
    onRememberChange: (r: boolean) => void;
  }) => (
    <div>
      <button type="button" data-testid="doc-choose" onClick={() => onChange({ globalId: "SD1", name: "D1" })} />
      <button type="button" data-testid="doc-remember" onClick={() => onRememberChange(true)} />
    </div>
  ),
}));
vi.mock("../OperationConfirmation", () => ({ default: () => <div data-testid="confirm" /> }));

const nextButton = () => screen.getByRole("button", { name: /actions\.next/i });
const backButton = () => screen.getByRole("button", { name: /actions\.back/i });

beforeEach(() => {
  for (const k of Object.keys(prefs.store)) delete prefs.store[k];
  performOperation.mockClear();
  performSearch.mockClear();
});

/** Complete the Details step (names, process name, template) so Next is enabled; stay on Details. */
async function completeDetailsStep(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
  await user.type(screen.getByTestId("proc"), processName);
  await user.click(screen.getByTestId("fill-details")); // sample name + amounts
  await user.click(screen.getByTestId("tmpl-pick5")); // template chosen
}

/** Complete the Details step then advance to the Amounts step. */
async function reachAmountsStep(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await completeDetailsStep(user, processName);
  await user.click(nextButton()); // -> amounts
}

describe("OperationWizard", () => {
  it("keeps Next disabled on the details step until a template choice is made", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(screen.getByTestId("fill-details")); // names valid, but no template chosen yet
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("unselected");
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("tmpl-pick5"));
    expect(nextButton()).toBeEnabled();
  });

  it("preserves the picked template when stepping forward to Amounts and back again", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await reachAmountsStep(user, "dna"); // template picked on Details, now on Amounts
    await user.click(backButton()); // back to Details
    // the in-session choice is not clobbered by the remembered-defaults pass
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("pick");
    expect(screen.getByTestId("tmpl-id")).toHaveTextContent("5");
  });

  it("on Perform persists template + documentation + process name keyed by the process name", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);

    await reachAmountsStep(user, "dna extraction"); // template picked (remember: true)
    await user.click(nextButton()); // -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(screen.getByTestId("doc-remember"));
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(performOperation).toHaveBeenCalledTimes(1);
    // remembered under the composite "operation + process name" key, not the bare operation
    expect(prefs.store.INVENTORY_OPERATION_TEMPLATE_DEFAULTS).toEqual({
      "derive dna extraction": { mode: "pick", templateId: 5, templateName: "T5" },
    });
    expect(prefs.store.INVENTORY_OPERATION_DOC_DEFAULTS).toEqual({
      "derive dna extraction": { globalId: "SD1", name: "D1" },
    });
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_NAMES).toEqual({ derive: ["dna extraction"] });
  });

  it("uses the picked template's quantity category for the amount units on the amounts step", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(screen.getByTestId("fill-details"));
    await user.click(screen.getByTestId("tmpl-pick-volume")); // a volume template
    await user.click(nextButton()); // -> amounts
    expect(screen.getByTestId("section")).toHaveTextContent("amounts");
    expect(screen.getByTestId("unit-categories")).toHaveTextContent('["volume"]');
  });

  it("names the operation and chosen process name in the heading on every step", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByText(/operations\.derive\.label: dna/)).toBeInTheDocument();
    await user.click(screen.getByTestId("fill-details"));
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts step; the heading persists across steps
    expect(screen.getByText(/operations\.derive\.label: dna/)).toBeInTheDocument();
  });

  it("names just the operation in the heading when it has no process name (cryopreserve)", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByText(/operations\.cryopreserve\.label/)).toBeInTheDocument();
  });

  it("keeps the entered process name when stepping back to Details", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await reachAmountsStep(user, "dna extraction");
    await user.click(backButton());
    expect(screen.getByTestId("proc")).toHaveValue("dna extraction");
  });

  it("pre-fills and re-ticks a remembered process name on a new run", async () => {
    prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS = { derive: "boil" };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(screen.getByTestId("proc")).toHaveValue("boil");
    expect(screen.getByTestId("remember-proc")).toHaveTextContent("true");
  });

  it("persists the process name as this operation's default when its remember box is ticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("toggle-remember-proc"));
    await user.click(screen.getByTestId("fill-details"));
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS).toEqual({ derive: "dna extraction" });
  });

  it("pre-fills saved amounts at the start for a remembered process name", async () => {
    prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS = { derive: "boil" };
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive boil": {
        count: 2,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(screen.getByTestId("proc")).toHaveValue("boil");
    // the amounts box loads its persisted (ticked) state and the amounts pre-fill (spec: a remembered
    // process name implies the amounts default loads too)
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("true");
    expect(screen.getByTestId("count")).toHaveTextContent("2");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":3}');
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":2,"unitId":3}');
  });

  it("populates the saved count and amounts when a saved process name is entered", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive dna": {
        count: 2,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByTestId("count")).toHaveTextContent("2");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":3}');
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":2,"unitId":3}');
  });

  it("clears previously loaded count and amounts when a new (unsaved) process name is typed", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive dna": {
        count: 2,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads count 2 + amounts {7,2}
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":3}');
    await user.type(screen.getByTestId("proc"), "x"); // "dnax" is unsaved -> loaded values clear
    expect(screen.getByTestId("count")).toHaveTextContent("1"); // back to the default of 1
    // A new process starts blank: the numeric fields default to 1 and the unit is unset (0), which
    // blocks the amounts step until the user picks a unit.
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":1,"unitId":0}');
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":1,"unitId":0}');
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("false");
  });

  it("does not overwrite amounts the user has edited when the process name later changes", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive dna": {
        count: 2,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.click(screen.getByTestId("fill-details")); // user sets amounts -> touched
    await user.type(screen.getByTestId("proc"), "dna"); // a saved name, but amounts must not be clobbered
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":5,"unitId":3}');
  });

  it("leaves the amounts box unchecked on a fresh Derive run until a saved process name is entered", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive dna": {
        count: 2,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("false"); // no process name yet
    await user.type(screen.getByTestId("proc"), "dna"); // a name with saved amounts
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("true"); // flips to ticked
  });

  it("persists the amounts under the process-name key when the box is ticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction"); // a new name -> box starts unticked
    await user.click(screen.getByTestId("fill-details")); // each {5,3}, taken {1,3}
    await user.click(screen.getByTestId("toggle-remember-amounts")); // tick "remember amounts"
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS).toEqual({
      "derive dna extraction": {
        count: 1,
        eachAmount: { numericValue: 5, unitId: 3 },
        amountTaken: { numericValue: 1, unitId: 3 },
      },
    });
  });

  it("does not persist amounts when the remember box is left unticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction"); // new name -> box stays unticked
    await user.click(screen.getByTestId("fill-details"));
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS).toEqual({});
  });

  it("forgets amounts on Perform when the remember-amounts box is unticked", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      "derive dna extraction": {
        count: 3,
        eachAmount: { numericValue: 9, unitId: 3 },
        amountTaken: { numericValue: 4, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("fill-details"));
    await user.click(screen.getByTestId("toggle-remember-amounts")); // untick (default is ticked)
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS).toEqual({});
  });

  it("persists Cryopreserve amounts keyed by the operation when the box is ticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origin={origin} />);
    await user.click(screen.getByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("false"); // unticked on a first run
    await user.click(screen.getByTestId("fill-details")); // each {5,3}, taken {1,3}
    await user.click(screen.getByTestId("toggle-remember-amounts")); // tick "remember amounts"
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS).toEqual({
      cryopreserve: {
        count: 1,
        eachAmount: { numericValue: 5, unitId: 3 },
        amountTaken: { numericValue: 1, unitId: 3 },
      },
    });
  });

  it("pre-fills Cryopreserve amounts and ticks the box at the start on a subsequent run", async () => {
    prefs.store.INVENTORY_OPERATION_AMOUNT_DEFAULTS = {
      cryopreserve: {
        count: 6,
        eachAmount: { numericValue: 8, unitId: 3 },
        amountTaken: { numericValue: 3, unitId: 3 },
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origin={makeMockSubSample({})} />);
    await user.click(screen.getByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByTestId("remember-amounts")).toHaveTextContent("true"); // ticked from the start
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":8,"unitId":3}');
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":3,"unitId":3}');
  });
});
