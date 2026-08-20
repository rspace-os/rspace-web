import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

// Shared, controllable preference store standing in for useUiPreference's persisted UI settings, so
// the test can assert exactly what Perform persisted (and keyed by which process name).
const prefs = vi.hoisted(() => ({ store: {} as Record<string, unknown> }));

vi.mock("@/hooks/api/useUiPreference", () => ({
  PREFERENCES: {
    INVENTORY_OPERATION_PROCESS_VALUES: Symbol.for("INVENTORY_OPERATION_PROCESS_VALUES"),
    INVENTORY_OPERATION_PROCESS_NAMES: Symbol.for("INVENTORY_OPERATION_PROCESS_NAMES"),
    INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: Symbol.for("INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS"),
  },
  default: (pref: symbol, opts: { defaultValue: unknown }) => {
    const key = Symbol.keyFor(pref) ?? "";
    const value = key in prefs.store ? prefs.store[key] : opts.defaultValue;
    return [value, (v: unknown) => (prefs.store[key] = v)];
  },
}));

const performOperation = vi.fn((_req: unknown) => Promise.resolve({ id: 1, globalId: "SS9", name: "New" }));
const sampleNameAvailable = vi.fn((_name: string) => Promise.resolve(true));
vi.mock("../operationsApi", () => ({
  performOperation: (req: unknown) => performOperation(req),
  sampleNameAvailable: (name: string) => sampleNameAvailable(name),
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

// Stub the step bodies so the flow can be driven deterministically. The details stub renders all its
// controls regardless of `section` (so a test can fill amounts while still on the details step) and
// echoes `section`/`unitCategories`/`remember` back via spans.
vi.mock("../OperationDetailsStep", () => ({
  default: ({
    values,
    onChange,
    section,
    unitCategories,
    remember,
    onRememberChange,
  }: {
    values: Record<string, unknown>;
    onChange: (v: Record<string, unknown>) => void;
    section?: string;
    unitCategories?: Array<string>;
    remember?: boolean;
    onRememberChange?: (r: boolean) => void;
  }) => (
    <div>
      <span data-testid="section">{String(section)}</span>
      <span data-testid="unit-categories">{JSON.stringify(unitCategories ?? null)}</span>
      <input
        data-testid="proc"
        value={String(values.processName ?? "")}
        onChange={(e) => onChange({ ...values, processName: e.target.value })}
      />
      <span data-testid="sample-name">{String(values.sampleName ?? "")}</span>
      <button
        type="button"
        data-testid="edit-sample"
        onClick={() => onChange({ ...values, sampleName: "Custom name" })}
      />
      <button
        type="button"
        data-testid="fill-amounts"
        onClick={() =>
          onChange({
            ...values,
            count: 1,
            eachAmount: { numericValue: 5, unitId: 3 },
            amountTaken: { numericValue: 1, unitId: 3 },
          })
        }
      />
      <button
        type="button"
        data-testid="fill-over-amounts"
        onClick={() =>
          onChange({
            ...values,
            count: 1,
            eachAmount: { numericValue: 5, unitId: 3 },
            amountTaken: { numericValue: 5, unitId: 3 },
          })
        }
      />
      <span data-testid="remember">{String(remember)}</span>
      <button type="button" data-testid="toggle-remember" onClick={() => onRememberChange?.(!remember)} />
      <span data-testid="count">{String(values.count ?? "")}</span>
      <span data-testid="each-amount">{JSON.stringify(values.eachAmount ?? null)}</span>
      <span data-testid="amount-taken">{JSON.stringify(values.amountTaken ?? null)}</span>
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
        onClick={() => onChange({ mode: "pick", templateId: 5, templateName: "T5" })}
      />
      <button
        type="button"
        data-testid="tmpl-pick-volume"
        onClick={() => onChange({ mode: "pick", templateId: 7, templateName: "T7", quantityCategory: "volume" })}
      />
    </div>
  ),
}));
vi.mock("../DocumentationStep", () => ({
  default: ({ onChange }: { onChange: (v: unknown) => void }) => (
    <div>
      <button type="button" data-testid="doc-choose" onClick={() => onChange({ globalId: "SD1", name: "D1" })} />
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
  sampleNameAvailable.mockClear();
  sampleNameAvailable.mockResolvedValue(true);
});

/** Pick Derive, type a process name (which auto-derives the sample name), and fill the amounts. */
async function fillDerive(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
  await user.type(screen.getByTestId("proc"), processName);
  await user.click(screen.getByTestId("fill-amounts"));
}

/** Drive Derive from the picker all the way to the Confirm step (template picked, amounts filled). */
async function reachConfirm(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await fillDerive(user, processName);
  await user.click(nextButton()); // details -> template
  await user.click(screen.getByTestId("tmpl-pick5"));
  await user.click(nextButton()); // template -> amounts
  await user.click(nextButton()); // amounts -> documentation
  await user.click(nextButton()); // documentation -> confirm
}

describe("OperationWizard step flow", () => {
  it("keeps Next disabled on the details step until a process name (and derived sample name) exist", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    expect(nextButton()).toBeDisabled(); // no process name yet
    await user.type(screen.getByTestId("proc"), "dna");
    expect(nextButton()).toBeEnabled();
  });

  it("keeps Next disabled on the details step when the origin subsample has an amount of 0", async () => {
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({ quantity: { numericValue: 0, unitId: 3 } })]}
      />,
    );
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    // a process name would normally enable Next (see the test above), but a zero-amount origin blocks it
    await user.type(screen.getByTestId("proc"), "dna");
    expect(nextButton()).toBeDisabled();
  });

  it("auto-derives the sample name from the origin sample name and the process name", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    expect(screen.getByTestId("sample-name")).toHaveTextContent("A sample dna extraction");
  });

  it("de-duplicates the derived sample name against existing names with a numeric suffix", async () => {
    // "A sample dna" and its _1 are taken, so the wizard must land on _2.
    const taken = ["A sample dna", "A sample dna_1"];
    sampleNameAvailable.mockImplementation((name: string) => Promise.resolve(!taken.includes(name)));
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await waitFor(() => expect(screen.getByTestId("sample-name")).toHaveTextContent("A sample dna_2"));
  });

  it("stops re-deriving the sample name once the user edits it by hand", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(screen.getByTestId("edit-sample")); // manual override
    await user.type(screen.getByTestId("proc"), "x"); // process name changes again
    expect(screen.getByTestId("sample-name")).toHaveTextContent("Custom name");
  });

  it("puts the template on its own step, gated until a choice is made", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // -> template step
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("unselected");
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("tmpl-pick5"));
    expect(nextButton()).toBeEnabled();
  });

  it("blocks Next on the amounts step when the amount taken exceeds the origin (over-removal)", async () => {
    // origin (makeMockSubSample) holds 1 ml; taking 5 ml must be blocked (adr/0005).
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(screen.getByTestId("fill-over-amounts"));
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("fill-amounts")); // within the origin's quantity
    expect(nextButton()).toBeEnabled();
  });

  it("uses the picked template's quantity category for the amount units on the amounts step", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick-volume"));
    await user.click(nextButton()); // -> amounts
    expect(screen.getByTestId("section")).toHaveTextContent("amounts");
    expect(screen.getByTestId("unit-categories")).toHaveTextContent('["volume"]');
  });

  it("blocks Perform for a terminal operation (Destroy) on an empty origin, skipping template/amounts", async () => {
    // Destroy declares steps ["confirm"], so it lands straight on the confirm step (no template or
    // amounts step). Its empty-origin guard lives in stepValid(), which must gate Perform - not just
    // show a message. Regression guard for the Perform button being gated only by `submitting`.
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({ quantity: { numericValue: 0, unitId: 3 } })]}
      />,
    );
    await user.click(screen.getByRole("button", { name: /operations\.destroy\.label/i }));
    expect(screen.queryByText(/step\.template/)).not.toBeInTheDocument();
    expect(screen.queryByText(/step\.amounts/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeDisabled();
  });

  it("enables Perform for a terminal operation (Destroy) on a non-empty origin", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.destroy\.label/i }));
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
  });

  it("names the operation and its process name in the heading; just the operation for a fixed one", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByText(/operations\.derive\.label: dna/)).toBeInTheDocument();
    await user.click(backButton()); // back to picker
    await user.click(screen.getByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByText(/operations\.cryopreserve\.label$/)).toBeInTheDocument();
  });
});

describe("OperationWizard remember bundle", () => {
  it("persists the whole bundle keyed by process name when remember is ticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);

    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("toggle-remember")); // tick remember
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_VALUES).toEqual({
      "derive dna extraction": {
        values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 5, templateName: "T5" },
        documentation: { globalId: "SD1", name: "D1" },
      },
    });
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_NAMES).toEqual({ derive: ["dna extraction"] });
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS).toEqual({ derive: "dna extraction" });
  });

  it("persists nothing when remember is left unticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "dna extraction"); // remember never ticked
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_VALUES).toBeUndefined();
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_NAMES).toBeUndefined();
  });

  it("loads a saved bundle (ticked) when its process name is entered", async () => {
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = {
      "derive dna": {
        values: { count: 4, eachAmount: { numericValue: 7, unitId: 3 }, amountTaken: { numericValue: 2, unitId: 3 } },
        template: { mode: "pick", templateId: 9, templateName: "T9" },
        documentation: null,
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByTestId("remember")).toHaveTextContent("true");
    expect(screen.getByTestId("count")).toHaveTextContent("4");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":3}');
  });

  it("resets to blank defaults (unticked) for a new, unsaved process name", async () => {
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = {
      "derive dna": {
        values: { count: 4, eachAmount: { numericValue: 7, unitId: 3 }, amountTaken: { numericValue: 2, unitId: 3 } },
        template: { mode: "pick", templateId: 9, templateName: "T9" },
        documentation: null,
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads the saved bundle
    expect(screen.getByTestId("count")).toHaveTextContent("4");
    await user.type(screen.getByTestId("proc"), "x"); // "dnax" is unsaved
    expect(screen.getByTestId("remember")).toHaveTextContent("false");
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":1,"unitId":0}');
  });

  it("unticking remember resets the form but never deletes the saved bundle", async () => {
    const saved = {
      "derive dna": {
        values: { count: 4, eachAmount: { numericValue: 7, unitId: 3 }, amountTaken: { numericValue: 2, unitId: 3 } },
        template: { mode: "pick", templateId: 9, templateName: "T9" },
        documentation: null,
      },
    };
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = saved;
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads + ticks
    expect(screen.getByTestId("remember")).toHaveTextContent("true");
    await user.click(screen.getByTestId("toggle-remember")); // untick
    expect(screen.getByTestId("remember")).toHaveTextContent("false");
    expect(screen.getByTestId("count")).toHaveTextContent("1"); // form reset to defaults
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_VALUES).toEqual(saved); // store untouched
  });

  it("pre-fills the last-used process name and, on Review / edit, shows its bundle", async () => {
    // A complete remembered bundle loads on open, so the wizard offers the step-one fast path (adr/0009):
    // the confirmation and Perform, with the details form only behind "Review / edit".
    prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS = { derive: "boil" };
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = {
      "derive boil": {
        values: { count: 3, eachAmount: { numericValue: 8, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "none", templateId: null },
        documentation: null,
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    // Fast path: the confirmation and an enabled Perform show; the details form is not rendered yet.
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
    expect(screen.queryByTestId("proc")).not.toBeInTheDocument();
    // Review / edit drops into the normal wizard with the bundle pre-filled.
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    expect(screen.getByTestId("proc")).toHaveValue("boil");
    expect(screen.getByTestId("remember")).toHaveTextContent("true");
    expect(screen.getByTestId("count")).toHaveTextContent("3");
  });

  it("performs a remembered run directly from the step-one fast path", async () => {
    prefs.store.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS = { derive: "boil" };
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = {
      "derive boil": {
        values: { count: 3, eachAmount: { numericValue: 8, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "none", templateId: null },
        documentation: null,
      },
    };
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(screen.getByRole("button", { name: /operations\.derive\.label/i }));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(performOperation).toHaveBeenCalledTimes(1);
  });

  it("persists a Cryopreserve bundle keyed by the operation (fixed process name)", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(screen.getByRole("button", { name: /operations\.cryopreserve\.label/i }));
    await user.click(screen.getByTestId("toggle-remember")); // tick
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const stored = prefs.store.INVENTORY_OPERATION_PROCESS_VALUES as Record<string, unknown>;
    expect(Object.keys(stored)).toEqual(["cryopreserve"]);
  });
});
