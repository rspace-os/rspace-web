import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render as renderWithoutQueryClient, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

// The wizard fetches the operation definitions with React Query (mocked fetchOperationsConfig
// below), so every render needs a QueryClient; a fresh one per render keeps tests isolated.
function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

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
  // The real definitions, exactly as fetching the backend's config would resolve them.
  fetchOperationsConfig: async () => (await import("./testOperations")).operations,
}));

const performSearch = vi.fn();
const addAlert = vi.fn();
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    searchStore: { search: { performSearch } },
    uiStore: { addAlert },
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
// echoes `section`/`unitCategories` back via spans (the remember state is echoed by the
// confirmation stub, where the checkbox lives).
vi.mock("../OperationDetailsStep", () => ({
  default: ({
    values,
    onChange,
    section,
    unitCategories,
    onRememberChange,
    onAmountModeChange,
    onPerSubsampleAmountsChange,
  }: {
    values: Record<string, unknown>;
    onChange: (v: Record<string, unknown>) => void;
    section?: string;
    unitCategories?: Array<string>;
    onRememberChange?: (r: boolean) => void;
    onAmountModeChange?: (mode: string) => void;
    onPerSubsampleAmountsChange?: (amounts: Record<string, { numericValue: number; unitId: number }>) => void;
  }) => (
    <div>
      <span data-testid="section">{String(section)}</span>
      <span data-testid="unit-categories">{JSON.stringify(unitCategories ?? null)}</span>
      <input
        data-testid="proc"
        value={String(values.processName ?? "")}
        onChange={(e) => onChange({ ...values, processName: e.target.value })}
      />
      <span data-testid="details-has-toggle">{String(Boolean(onRememberChange))}</span>
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
      <span data-testid="count">{String(values.count ?? "")}</span>
      <span data-testid="each-amount">{JSON.stringify(values.eachAmount ?? null)}</span>
      <span data-testid="amount-taken">{JSON.stringify(values.amountTaken ?? null)}</span>
      <button type="button" data-testid="mode-per" onClick={() => onAmountModeChange?.("perSubsample")} />
      <button
        type="button"
        data-testid="fill-per-first"
        onClick={() => onPerSubsampleAmountsChange?.({ SS1: { numericValue: 1, unitId: 3 } })}
      />
      <button
        type="button"
        data-testid="fill-per-both"
        onClick={() =>
          onPerSubsampleAmountsChange?.({
            SS1: { numericValue: 1, unitId: 3 },
            SS2: { numericValue: 1, unitId: 3 },
          })
        }
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
        onClick={() => onChange({ mode: "pick", templateId: 5, templateName: "T5" })}
      />
      <button
        type="button"
        data-testid="tmpl-pick-volume"
        onClick={() => onChange({ mode: "pick", templateId: 7, templateName: "T7", quantityCategory: "volume" })}
      />
      <button
        type="button"
        data-testid="tmpl-pick-mass"
        onClick={() => onChange({ mode: "pick", templateId: 8, templateName: "T8", quantityCategory: "mass" })}
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
// The confirmation stub echoes the remember state and offers a toggle: the single "remember"
// checkbox lives on the summary & confirm step (and the step-one fast path, which renders the same
// confirmation).
vi.mock("../OperationConfirmation", () => ({
  default: ({ remember, onRememberChange }: { remember?: boolean; onRememberChange?: (remember: boolean) => void }) => (
    <div data-testid="confirm">
      <span data-testid="remember">{String(remember)}</span>
      {onRememberChange ? (
        <button type="button" data-testid="toggle-remember" onClick={() => onRememberChange(!remember)} />
      ) : null}
    </div>
  ),
}));

const nextButton = () => screen.getByRole("button", { name: /actions\.next/i });
const backButton = () => screen.getByRole("button", { name: /actions\.back/i });

beforeEach(() => {
  for (const k of Object.keys(prefs.store)) delete prefs.store[k];
  performOperation.mockClear();
  performSearch.mockClear();
  addAlert.mockClear();
  sampleNameAvailable.mockClear();
  sampleNameAvailable.mockResolvedValue(true);
});

/** Pick Derive, type a process name (which auto-derives the sample name), and fill the amounts. */
async function fillDerive(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    // a process name would normally enable Next (see the test above), but a zero-amount origin blocks it
    await user.type(screen.getByTestId("proc"), "dna");
    expect(nextButton()).toBeDisabled();
  });

  it("auto-derives the sample name from the origin sample name and the process name", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    expect(screen.getByTestId("sample-name")).toHaveTextContent("A sample dna extraction");
  });

  it("de-duplicates the derived sample name against existing names with a numeric suffix", async () => {
    // "A sample dna" and its _1 are taken, so the wizard must land on _2.
    const taken = ["A sample dna", "A sample dna_1"];
    sampleNameAvailable.mockImplementation((name: string) => Promise.resolve(!taken.includes(name)));
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await waitFor(() => expect(screen.getByTestId("sample-name")).toHaveTextContent("A sample dna_2"));
  });

  it("stops re-deriving the sample name once the user edits it by hand", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(screen.getByTestId("edit-sample")); // manual override
    await user.type(screen.getByTestId("proc"), "x"); // process name changes again
    expect(screen.getByTestId("sample-name")).toHaveTextContent("Custom name");
  });

  it("preselects the parent's template for a first-time run when the parent has one", async () => {
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // details -> template
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("fromSample");
    expect(nextButton()).toBeEnabled();
  });

  it("prefills the amount units from the origin subsample", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":1,"unitId":3}');
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":1,"unitId":3}');
  });

  it("resets the created amount's unit when a picked template changes the measurement category", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await fillDerive(user, "dna"); // fills both amounts with unit 3
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick-mass")); // a category the origin's unit is not in
    await user.click(nextButton()); // -> amounts
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":5,"unitId":0}');
    // the amount taken FROM the origin stays in the origin's own category, so its unit is untouched
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":1,"unitId":3}');
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
    // origin (makeMockSubSample) holds 1 ml; taking 5 ml must be blocked (DevDocs/adr/0007).
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
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
    await user.click(await screen.findByRole("button", { name: /operations\.destroy\.label/i }));
    expect(screen.queryByText(/step\.template/)).not.toBeInTheDocument();
    expect(screen.queryByText(/step\.amounts/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeDisabled();
  });

  it("blocks the details step for Pool when ANY pooled origin is empty, not just the smallest", async () => {
    // Pool's default amount mode is "all" (take each origin's full quantity), which would silently
    // no-op an empty origin; the backend also rejects empty origins outright (DevDocs/adr/0007), so
    // the wizard must gate on every origin's quantity.
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({}), makeMockSubSample({ quantity: { numericValue: 0, unitId: 3 } })]}
      />,
    );
    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    expect(nextButton()).toBeDisabled();
  });

  it("lets Pool proceed past the details step when every pooled origin holds an amount", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({}), makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    expect(nextButton()).toBeEnabled();
  });

  it("enables Perform for a terminal operation (Destroy) on a non-empty origin", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.destroy\.label/i }));
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
  });

  it("surfaces a rejected Perform as an alert and keeps the wizard open for retry", async () => {
    performOperation.mockRejectedValueOnce(new Error("backend rejected the request"));
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "boom");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    // the wizard stays open on the confirmation so the user can retry; nothing is lost
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
  });

  it("sends a Destroy request with no new sample and the computed disposed date on the origin", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.destroy\.label/i }));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const request = performOperation.mock.calls[0][0] as {
      operationType: string;
      newSample: unknown;
      origins: Array<{
        amountTaken: { numericValue: number; unitId: number };
        extraFields?: Array<{ newFieldRequest: boolean; content: string }>;
      }>;
    };
    expect(request.operationType).toBe("destroy");
    expect(request.newSample).toBeNull();
    // Destroy empties the origin: the amount taken is its full current quantity
    expect(request.origins[0].amountTaken).toEqual({ numericValue: 1, unitId: 3 });
    // ... and stamps the computed ISO disposal date as a new origin field
    expect(request.origins[0].extraFields?.[0]).toEqual(
      expect.objectContaining({ newFieldRequest: true, content: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/) }),
    );
  });

  it("blocks the amounts step in per-subsample mode until every origin has an amount", async () => {
    const user = userEvent.setup();
    const first = makeMockSubSample({});
    const second = makeMockSubSample({ id: 2, globalId: "SS2" });
    render(<OperationWizard open onClose={vi.fn()} origins={[first, second]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    await user.click(screen.getByTestId("mode-per"));
    expect(nextButton()).toBeDisabled(); // no per-origin amounts entered yet
    await user.click(screen.getByTestId("fill-per-first"));
    expect(nextButton()).toBeDisabled(); // the second origin still has no amount
    await user.click(screen.getByTestId("fill-per-both"));
    expect(nextButton()).toBeEnabled();
  });

  it("names the operation and its process name in the heading; just the operation for a fixed one", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByText(/operations\.derive\.label: dna/)).toBeInTheDocument();
    await user.click(backButton()); // back to picker
    await user.click(await screen.findByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByText(/operations\.cryopreserve\.label$/)).toBeInTheDocument();
  });
});

describe("OperationWizard remember bundle", () => {
  it("offers the remember checkbox on the summary & confirm step, not the details step", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await fillDerive(user, "dna");
    // details: no remember handler is passed, so the step renders no checkbox
    expect(screen.getByTestId("details-has-toggle")).toHaveTextContent("false");
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    expect(screen.getByTestId("toggle-remember")).toBeInTheDocument();
    expect(screen.getByTestId("remember")).toHaveTextContent("false");
  });

  it("persists the whole bundle keyed by process name when remember is ticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByTestId("toggle-remember")); // tick remember on the confirm step
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    // Pin the assembled request: the wizard must hand buildOperationRequest's output to the API
    // with the chosen template and the optional documentation link included.
    const request = performOperation.mock.calls[0][0] as {
      operationType: string;
      origins: Array<{ id: number; amountTaken: { numericValue: number; unitId: number } }>;
      newSample: {
        templateId: number | null;
        extraFields: Array<{ link?: { relationType: string; targetGlobalId: string } }>;
      } | null;
    };
    expect(request.operationType).toBe("derive");
    expect(request.origins).toEqual([expect.objectContaining({ id: 1, amountTaken: { numericValue: 1, unitId: 3 } })]);
    expect(request.newSample?.templateId).toBe(5);
    const links = (request.newSample?.extraFields ?? [])
      .filter((field) => field.link)
      .map((field) => [field.link?.relationType, field.link?.targetGlobalId]);
    expect(links).toEqual(
      expect.arrayContaining([
        ["IsDerivedFrom", "SS1"],
        ["IsDocumentedBy", "SD1"],
      ]),
    );
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    // the loaded remember flag itself is observable on the confirm step / fast path (tested below)
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads the saved bundle
    expect(screen.getByTestId("count")).toHaveTextContent("4");
    await user.type(screen.getByTestId("proc"), "x"); // "dnax" is unsaved
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    // fresh amounts are prefilled with the origin subsample's unit
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":1,"unitId":3}');
  });

  it("unticking remember (on the confirmation) resets the form but never deletes the saved bundle", async () => {
    // amountTaken must not exceed the mock origin's quantity (1), or over-removal blocks the
    // step-one fast path this test rides to reach the confirmation.
    const saved = {
      "derive dna": {
        values: { count: 4, eachAmount: { numericValue: 7, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 9, templateName: "T9" },
        documentation: null,
      },
    };
    prefs.store.INVENTORY_OPERATION_PROCESS_VALUES = saved;
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads + ticks the saved bundle
    // once the derived sample name settles, the whole run is valid, so the step-one fast path shows
    // the confirmation, which carries the remember checkbox
    await waitFor(() => expect(screen.getByTestId("remember")).toHaveTextContent("true"), { timeout: 3000 });
    await user.click(screen.getByTestId("toggle-remember")); // untick
    // unticking drops the fast path (nothing is remembered any more): back to the details step,
    // with the form reset to defaults
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(prefs.store.INVENTORY_OPERATION_PROCESS_VALUES).toEqual(saved); // store untouched
  });

  it("pre-fills the last-used process name and, on Review / edit, shows its bundle", async () => {
    // A complete remembered bundle loads on open, so the wizard offers the step-one fast path (DevDocs/adr/0007):
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    // Fast path: the confirmation (carrying the ticked remember checkbox) and an enabled Perform
    // show; the details form is not rendered yet.
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(screen.getByTestId("remember")).toHaveTextContent("true");
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
    expect(screen.queryByTestId("proc")).not.toBeInTheDocument();
    // Review / edit drops into the normal wizard with the bundle pre-filled.
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    expect(screen.getByTestId("proc")).toHaveValue("boil");
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
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
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
    await user.click(await screen.findByRole("button", { name: /operations\.cryopreserve\.label/i }));
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByTestId("toggle-remember")); // tick on the confirm step
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const stored = prefs.store.INVENTORY_OPERATION_PROCESS_VALUES as Record<string, unknown>;
    expect(Object.keys(stored)).toEqual(["cryopreserve"]);
  });
});
