import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render as renderWithoutQueryClient, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import { InEnglish } from "@/__tests__/realI18n";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

function render(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return renderWithoutQueryClient(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const prefs = vi.hoisted(() => ({ store: {} as Record<string, unknown> }));

vi.mock("@/hooks/api/useUiPreference", () => ({
  default: (pref: symbol, opts: { defaultValue: unknown }) => {
    const key = Symbol.keyFor(pref) ?? "";
    const value = key in prefs.store ? prefs.store[key] : opts.defaultValue;
    return [value, (v: unknown) => (prefs.store[key] = v)];
  },
}));
vi.mock("../processValues", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../processValues")>();
  return {
    ...actual,
    fetchLatestOperationPreferences: () =>
      Promise.resolve(actual.normalizeOperationPreferences(prefs.store.INVENTORY_OPERATIONS)),
  };
});

/** The wizard's one preference, INVENTORY_OPERATIONS, as held by the mocked hook above. */
function ops(): { values: Record<string, unknown>; names: Record<string, unknown>; defaults: Record<string, unknown> } {
  prefs.store.INVENTORY_OPERATIONS ??= { values: {}, names: {}, defaults: {} };
  return prefs.store.INVENTORY_OPERATIONS as ReturnType<typeof ops>;
}

const OPERATION_URL = "/api/inventory/v1/operations/:key";
const posted: Array<Record<string, unknown>> = [];
const postedTo: Array<string> = [];
const taken: Array<string> = [];
const created = { sample: { id: 1, globalId: "SS9", name: "New" } };
const operationHandlers = [
  http.post(OPERATION_URL, async ({ request, params }) => {
    postedTo.push(String(params.key));
    posted.push((await request.json()) as Record<string, unknown>);
    return HttpResponse.json(created, { status: 201 });
  }),
  http.get("/api/inventory/v1/samples/validateNameForNewSample", ({ request }) => {
    const name = new URL(request.url).searchParams.get("name") ?? "";
    return HttpResponse.json({ valid: !taken.includes(name) });
  }),
];

const performSearch = vi.fn();
const getTemplate = vi.fn();
const template = (o: Record<string, unknown> = {}) => ({
  id: 9,
  name: "Parent template",
  quantityCategory: "volume",
  deleted: false,
  fields: [],
  ...o,
});
const mandatoryField = (name: string, content: string) => ({ name, mandatory: true, content, selectedOptions: null });
const addAlert = vi.fn();
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: { search: { performSearch }, getTemplate },
    uiStore: { addAlert },
    unitStore: {
      getUnit: (id: number) => ({
        label: id === 7 ? "g" : "ml",
        category: id === 7 ? "mass" : "volume",
      }),
    },
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
// ContextDialog wraps the content in a MUI Dialog; render its children inline when open. The
// "dialog-close" button stands in for the dialog's own close paths (Escape).
vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children, onClose }: { open: boolean; children: React.ReactNode; onClose: () => void }) =>
    open ? (
      <div>
        <button type="button" data-testid="dialog-close" onClick={onClose} />
        {children}
      </div>
    ) : null,
}));

// Stub the step bodies so the flow can be driven deterministically. The details stub renders all its
// controls regardless of `section`, so a test can fill amounts while still on the details step.
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
      {/* Each fills the amounts with a different amount taken, in ml: 1 is within the mock origin,
          5 over-removes from it, and 3 is within a 5 ml origin but beyond a 1 ml one. */}
      {[1, 5, 3].map((taken) => (
        <button
          key={taken}
          type="button"
          data-testid={`fill-taken-${taken}`}
          onClick={() =>
            onChange({
              ...values,
              count: 1,
              eachAmount: { numericValue: 5, unitId: 3 },
              amountTaken: { numericValue: taken, unitId: 3 },
            })
          }
        />
      ))}
      <span data-testid="count">{String(values.count ?? "")}</span>
      <span data-testid="each-amount">{JSON.stringify(values.eachAmount ?? null)}</span>
      <span data-testid="amount-taken">{JSON.stringify(values.amountTaken ?? null)}</span>
      <button type="button" data-testid="mode-per" onClick={() => onAmountModeChange?.("perSubsample")} />
      <button type="button" data-testid="mode-same" onClick={() => onAmountModeChange?.("same")} />
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
    parentTemplateChecking,
    parentTemplateError,
    rememberedTemplateError,
  }: {
    value: { mode: string; templateId: number | null; templateName?: string };
    onChange: (v: unknown) => void;
    parentTemplateChecking?: boolean;
    parentTemplateError?: string | null;
    rememberedTemplateError?: string | null;
  }) => (
    <div>
      <span data-testid="tmpl-mode">{value.mode}</span>
      <span data-testid="tmpl-id">{String(value.templateId)}</span>
      <span data-testid="tmpl-name">{value.templateName ?? ""}</span>
      <span data-testid="tmpl-checking">{String(Boolean(parentTemplateChecking))}</span>
      <span data-testid="tmpl-parent-error">{parentTemplateError ?? ""}</span>
      <span data-testid="tmpl-remembered-error">{rememberedTemplateError ?? ""}</span>
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

const DEFAULT_REMEMBERED_VALUES = {
  count: 2,
  eachAmount: { numericValue: 1, unitId: 3 },
  amountTaken: { numericValue: 1, unitId: 3 },
};

const nextButton = () => screen.getByRole("button", { name: /actions\.next/i });
const backButton = () => screen.getByRole("button", { name: /actions\.back/i });

beforeEach(() => {
  for (const k of Object.keys(prefs.store)) delete prefs.store[k];
  posted.length = 0;
  postedTo.length = 0;
  taken.length = 0;
  performSearch.mockClear();
  addAlert.mockClear();
  // Reset, not merely cleared: mockClear leaves a queued mockResolvedValueOnce/mockRejectedValueOnce
  // for the next test, and one test here asserts getTemplate is never called.
  getTemplate.mockReset();
  // Defaults to a template with no defaultless mandatory field, i.e. a passing parent-template check.
  getTemplate.mockImplementation(() => Promise.resolve(template({ fields: [mandatoryField("Passage number", "1")] })));
  server.use(...operationHandlers);
});

async function fillDerive(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
  await user.type(screen.getByTestId("proc"), processName);
  await user.click(screen.getByTestId("fill-taken-1"));
}

async function reachConfirm(user: ReturnType<typeof userEvent.setup>, processName: string) {
  await fillDerive(user, processName);
  await user.click(nextButton()); // details -> template
  await user.click(screen.getByTestId("tmpl-pick5"));
  await user.click(nextButton()); // template -> amounts
  await user.click(nextButton()); // amounts -> documentation
  await user.click(nextButton()); // documentation -> confirm
}

/** The remembered bundle for "derive dna", the process name every test below types. */
function rememberDerive(template: unknown, values: unknown = DEFAULT_REMEMBERED_VALUES) {
  const bundle = { "derive dna": { values, template, documentation: null } };
  ops().values = bundle;
  return bundle;
}

/** A once-only rejection from the operation endpoint. */
function rejectOnce(status: number, body: Record<string, unknown>) {
  server.use(http.post(OPERATION_URL, () => HttpResponse.json(body, { status }), { once: true }));
}

/** An origin whose post-operation reload is stubbed out, as every performing test needs. */
function mockOrigin(overrides: Parameters<typeof makeMockSubSample>[0] = {}) {
  const origin = makeMockSubSample(overrides);
  vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
  return origin;
}

describe("OperationWizard step flow", () => {
  it("keeps Next disabled on the details step until a process name (and derived sample name) exist", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    expect(nextButton()).toBeDisabled();
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
    taken.push("A sample dna", "A sample dna_1");
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
    await user.click(screen.getByTestId("edit-sample"));
    await user.type(screen.getByTestId("proc"), "x");
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
    await waitFor(() => expect(screen.getByTestId("tmpl-id")).toHaveTextContent("9"));
    expect(nextButton()).toBeEnabled();
    expect(getTemplate).toHaveBeenCalledWith(9, null, expect.anything());
  });

  it("validates the parent template at step one, so the one-click fast path is still offered", async () => {
    // The gate is evaluated for every step but only the active step renders, so a check owned by
    // TemplateStep never ran on step one, which is exactly where the fast path lives.
    rememberDerive({ mode: "fromSample", templateId: null });
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeInTheDocument());
    expect(getTemplate).toHaveBeenCalledWith(9, null, expect.anything());
  });

  it("withholds the one-click fast path when the remembered template has been trashed", async () => {
    // RSpace soft-deletes, so the lookup for a trashed template SUCCEEDS and only the deleted flag
    // tells the wizard.
    getTemplate.mockResolvedValue(template({ name: "Cell line", deleted: true }));
    rememberDerive({ mode: "pick", templateId: 9, templateName: "Cell line" });
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    await waitFor(() => expect(getTemplate).toHaveBeenCalledWith(9, null, expect.anything()));
    expect(screen.queryByRole("button", { name: /wizard\.perform/i })).not.toBeInTheDocument();

    await user.click(nextButton());
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("unselected");
    expect(screen.getByTestId("tmpl-remembered-error")).toHaveTextContent(/rememberedDeleted/);
  });

  it("never substitutes the parent's template for a trashed remembered one", async () => {
    getTemplate.mockImplementation((id?: number) =>
      Promise.resolve(id === 9 ? template({ name: "Cell line", deleted: true }) : template({ id: id ?? 4 })),
    );
    rememberDerive({ mode: "pick", templateId: 9, templateName: "Cell line" });
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    // The parent has its own template, which is what made the fallback reachable.
    origin.sample.templateId = 4;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    await waitFor(() => expect(getTemplate).toHaveBeenCalledWith(9, null, expect.anything()));
    expect(screen.queryByRole("button", { name: /wizard\.perform/i })).not.toBeInTheDocument();
    await user.click(nextButton());
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("unselected");
    expect(screen.getByTestId("tmpl-remembered-error")).toHaveTextContent(/rememberedDeleted/);
    expect(nextButton()).toBeDisabled();
  });

  it("blocks 'use parent template' when the parent's own template is in the trash", async () => {
    getTemplate.mockResolvedValue(template({ name: "Cell line", deleted: true }));
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // details -> template

    await waitFor(() => expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent(/templateDeleted/));
    expect(screen.getByTestId("tmpl-id")).toHaveTextContent("null");
    expect(nextButton()).toBeDisabled();
  });

  it("shows the remembered template's current name after it has been renamed", async () => {
    // The template name is display-only; only the id travels, so a rename must show through.
    getTemplate.mockResolvedValue(template({ name: "Cell line v2" }));
    rememberDerive({ mode: "pick", templateId: 9, templateName: "Cell line" });
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeInTheDocument());
    // Step into the wizard (the fast path replaces Next with review/Perform) to read the banner.
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    await user.click(nextButton());
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("remembered");
    expect(screen.getByTestId("tmpl-name")).toHaveTextContent("Cell line v2");
  });

  it("blocks and explains when the parent template has a defaultless mandatory field", async () => {
    getTemplate.mockResolvedValueOnce(template({ fields: [mandatoryField("Batch", "")] }));
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // details -> template

    await waitFor(() => expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent(/mandatoryFieldsError/));
    expect(screen.getByTestId("tmpl-id")).toHaveTextContent("null");
    expect(nextButton()).toBeDisabled();
  });

  it("explains a failed parent-template lookup instead of disabling Next silently", async () => {
    getTemplate.mockRejectedValueOnce(new Error("gone"));
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton());

    await waitFor(() => expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent(/lookupFailed/));
    expect(nextButton()).toBeDisabled();
  });

  it("retires an in-flight parent-template check when the user switches away from that mode", async () => {
    let rejectLookup: (reason: Error) => void = () => {};
    getTemplate.mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          rejectLookup = reject;
        }),
    );
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // details -> template
    await waitFor(() => expect(screen.getByTestId("tmpl-checking")).toHaveTextContent("true"));

    await user.click(screen.getByTestId("tmpl-pick5"));
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("pick");
    expect(screen.getByTestId("tmpl-checking")).toHaveTextContent("false");

    await act(async () => {
      rejectLookup(new Error("late failure"));
      await Promise.resolve();
    });
    expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent("");
    expect(screen.getByTestId("tmpl-checking")).toHaveTextContent("false");
    expect(nextButton()).toBeEnabled();
  });

  it("drops a restored 'use parent template' bundle when this run has no parent template", async () => {
    rememberDerive({ mode: "fromSample", templateId: null });
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = null; // no parent template for this run
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(nextButton()); // details -> template

    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("unselected");
    expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent("");
    expect(getTemplate).not.toHaveBeenCalled();
    await user.click(screen.getByTestId("tmpl-pick5"));
    expect(nextButton()).toBeEnabled();
  });

  it("keeps the rest of the template selection when the check writes the parent's id", async () => {
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    origin.sample.templateId = 9;
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await fillDerive(user, "dna");
    await user.click(nextButton()); // details -> template
    await waitFor(() => expect(screen.getByTestId("tmpl-id")).toHaveTextContent("9"));

    await user.click(screen.getByTestId("tmpl-pick5"));
    expect(screen.getByTestId("tmpl-mode")).toHaveTextContent("pick");
    expect(screen.getByTestId("tmpl-id")).toHaveTextContent("5");
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
    // origin (makeMockSubSample) holds 1 ml; taking 5 ml must be blocked.
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(screen.getByTestId("fill-taken-5"));
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("fill-taken-1")); // within the origin's quantity
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
    // Destroy's empty-origin guard lives in stepValid(), which must gate Perform, not just show a message.
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
    // no-op an empty origin.
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
    rejectOnce(400, { message: "backend rejected the request" });
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "boom");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
  });

  // --- the edit-session lock the wizard holds on its origins ---

  it("keeps the wizard and its locks when the server reports the origin is held by someone else", async () => {
    // A 409 carries the holder in `message`, not in a field-scoped `errors` entry.
    rejectOnce(409, { message: "SS1 is currently being edited by Carol Holder.", errors: [""] });
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    const release = vi.spyOn(origin, "releaseLock").mockResolvedValue(true);
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "held");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alert = addAlert.mock.calls[0][0] as { message: string };
    expect(alert.message).toBe("SS1 is currently being edited by Carol Holder.");
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(release).not.toHaveBeenCalled();
    await waitFor(() => expect(origin.fetchAdditionalInfo).toHaveBeenCalled());
  });

  it("pushes the lock expiry out on each step, so a slow run does not lose its origins", async () => {
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    const extend = vi.spyOn(origin, "acquireEditLock").mockResolvedValue("WAS_ALREADY_LOCKED");
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await reachConfirm(user, "slow");

    expect(extend.mock.calls.length).toBeGreaterThan(0);
  });

  it("refuses Perform once the lock has lapsed rather than sending a request the server will refuse", async () => {
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "acquireEditLock").mockResolvedValue("WAS_ALREADY_LOCKED");
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await reachConfirm(user, "lapsed");
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();

    const before = posted.length;
    runInAction(() => {
      origin.lockExpired = true;
    });

    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeDisabled());
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    expect(posted).toHaveLength(before);
  });

  it("shows the field-scoped reason for a rejection and reloads the origin", async () => {
    // A BindException 400 puts "Errors detected: 1" in `message`; the actionable reason is in
    // `errors[0]`, behind the path it applies to.
    rejectOnce(400, {
      message: "Errors detected: 1",
      errors: ["origins[0].amountTaken: Cannot take more from an origin than it currently holds"],
    });
    const user = userEvent.setup();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await reachConfirm(user, "stale");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alert = addAlert.mock.calls[0][0] as { message: string; variant: string };
    expect(alert.variant).toBe("error");
    // Under cimode the marker renders as its key; the assembled English is asserted in the InEnglish test below.
    expect(alert.message).toBe("inventory:operations.wizard.originIndex");
    await waitFor(() => expect(origin.fetchAdditionalInfo).toHaveBeenCalledTimes(1));
  });

  it("still reports the operation as failed when reloading the origin also fails", async () => {
    rejectOnce(400, { message: "backend rejected the request" });
    const restoreConsole = silenceConsole(["warn"], ["Could not refresh the origins"]);
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockRejectedValue(new Error("network down"));
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "both fail");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    // wait for the refresh to have failed too, so the single-alert assertion below is about the
    // finished state rather than a moment before the second alert could have been added
    await waitFor(() => expect(origin.fetchAdditionalInfo).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alerts = addAlert.mock.calls.map((call) => call[0] as { variant: string; message: string });
    expect(alerts).toHaveLength(1);
    expect(alerts[0].message).toBe("backend rejected the request");
    expect(onClose).not.toHaveBeenCalled();
    restoreConsole();
  });

  it("treats a successful Perform as done even when refreshing the origin afterwards fails", async () => {
    // The POST committed, so a failed refresh must not be reported as a failed operation with the
    // wizard left open for a retry that would charge the origin twice.
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockRejectedValue(new Error("network down"));
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "refresh");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(posted).toHaveLength(1);
    const alerts = addAlert.mock.calls.map((call) => call[0] as { variant: string; title: string });
    expect(alerts.some((a) => a.variant === "error")).toBe(false);
    expect(alerts.some((a) => a.variant === "warning" && /refreshFailed/.test(a.title))).toBe(true);
  });

  it("hands the created sample to onPerformed before closing, even when the refresh afterwards fails", async () => {
    const user = userEvent.setup();
    const calls: Array<string> = [];
    const onPerformed = vi.fn(() => calls.push("performed"));
    const onClose = vi.fn(() => calls.push("closed"));
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockRejectedValue(new Error("network down"));
    render(<OperationWizard open onClose={onClose} onPerformed={onPerformed} origins={[origin]} />);
    await reachConfirm(user, "handoff");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    expect(onPerformed).toHaveBeenCalledWith(created.sample);
    expect(calls).toEqual(["performed", "closed"]);
  });

  it("sends Destroy as a whole-origin claim with no inputs, leaving the disposed date to the server", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.destroy\.label/i }));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(postedTo[0]).toBe("destroy");
    expect(posted[0]).toEqual({ origin: { globalId: "SS1" } });
  });

  it("names a rejected input by its label rather than the bare key the server reports", async () => {
    rejectOnce(400, { message: "Errors detected: 1", errors: ["sampleName: Required by this operation."] });
    const user = userEvent.setup();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={vi.fn()} origins={[origin]} />);
    await reachConfirm(user, "bare key");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alert = addAlert.mock.calls[0][0] as { message: string };
    // cimode renders a key without its parameters, so this can only show the join message was chosen.
    expect(alert.message).toMatch(/operations\.wizard\.fieldReason/);
    expect(alert.message).not.toMatch(/^sampleName:/);
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
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("fill-per-first"));
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("fill-per-both"));
    expect(nextButton()).toBeEnabled();
  });

  it("names the operation and its process name in the heading; just the operation for a fixed one", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByText(/operations\.wizard\.headingWithProcess/)).toBeInTheDocument();
    await user.click(backButton()); // back to picker
    await user.click(await screen.findByRole("button", { name: /operations\.cryopreserve\.label/i }));
    expect(screen.getByText(/operations\.cryopreserve\.label$/)).toBeInTheDocument();
  });

  it("blocks Cancel while a Perform is in flight, so the origin cannot be charged twice", async () => {
    let releasePost: () => void = () => {};
    const pending = new Promise<void>((resolve) => {
      releasePost = resolve;
    });
    server.use(
      http.post(
        OPERATION_URL,
        async () => {
          await pending;
          return HttpResponse.json(created, { status: 201 });
        },
        { once: true },
      ),
    );
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "slow");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(screen.getByRole("button", { name: /actions\.cancel/i })).toBeDisabled());
    await user.click(screen.getByTestId("dialog-close"));
    expect(onClose).not.toHaveBeenCalled();

    releasePost();
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
  });

  it("re-gates Perform on every step when un-ticking remember resets the earlier ones", async () => {
    // Un-ticking resets the template/documentation/values but stays on the confirm step.
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await reachConfirm(user, "dna");
    await user.click(screen.getByTestId("toggle-remember"));
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();

    await user.click(screen.getByTestId("toggle-remember")); // un-tick: template selection is reset
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeDisabled();
  });

  it("reads that heading as 'Derive: dna' in English", async () => {
    const user = userEvent.setup();
    render(
      <InEnglish>
        <OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />
      </InEnglish>,
    );
    await user.click(await screen.findByRole("button", { name: /^Derive/ }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByRole("heading", { name: "Derive: dna" })).toBeInTheDocument();
  });

  it("reads a rejected input as 'New sample name: ...' in English", async () => {
    rejectOnce(400, { message: "Errors detected: 1", errors: ["sampleName: Required by this operation."] });
    const user = userEvent.setup();
    const origin = mockOrigin();
    render(
      <InEnglish>
        <OperationWizard open onClose={vi.fn()} origins={[origin]} />
      </InEnglish>,
    );
    await user.click(await screen.findByRole("button", { name: /^Derive/ }));
    await user.type(screen.getByTestId("proc"), "bare key");
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(screen.getByRole("button", { name: "Next" })); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(screen.getByRole("button", { name: "Next" })); // template -> amounts
    await user.click(screen.getByRole("button", { name: "Next" })); // amounts -> documentation
    await user.click(screen.getByRole("button", { name: "Next" })); // documentation -> confirm
    await user.click(screen.getByRole("button", { name: "Perform" }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alert = addAlert.mock.calls[0][0] as { message: string };
    expect(alert.message).toBe("New sample name: Required by this operation.");
  });

  it("reads an origin rejection as '... (origin 1)' in English", async () => {
    rejectOnce(400, {
      message: "Errors detected: 1",
      errors: ["origins[0].amountTaken: Cannot take more from an origin than it currently holds"],
    });
    const user = userEvent.setup();
    const origin = mockOrigin();
    render(
      <InEnglish>
        <OperationWizard open onClose={vi.fn()} origins={[origin]} />
      </InEnglish>,
    );
    await user.click(await screen.findByRole("button", { name: /^Derive/ }));
    await user.type(screen.getByTestId("proc"), "stale");
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(screen.getByRole("button", { name: "Next" })); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(screen.getByRole("button", { name: "Next" })); // template -> amounts
    await user.click(screen.getByRole("button", { name: "Next" })); // amounts -> documentation
    await user.click(screen.getByRole("button", { name: "Next" })); // documentation -> confirm
    await user.click(screen.getByRole("button", { name: "Perform" }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    const alert = addAlert.mock.calls[0][0] as { message: string };
    expect(alert.message).toBe("Cannot take more from an origin than it currently holds (origin 1)");
  });

  it("inflects the parent-template block for one field and for several", async () => {
    for (const [fields, expected] of [
      [["Batch"], "the required field Batch has no default value"],
      [["Batch", "Concentration"], "the required fields Batch and Concentration have no default value"],
    ] as Array<[Array<string>, string]>) {
      getTemplate.mockResolvedValueOnce(template({ fields: fields.map((name) => mandatoryField(name, "")) }));
      const user = userEvent.setup();
      const origin = makeMockSubSample({});
      origin.sample.templateId = 9;
      const { unmount } = render(
        <InEnglish>
          <OperationWizard open onClose={vi.fn()} origins={[origin]} />
        </InEnglish>,
      );
      await user.click(await screen.findByRole("button", { name: /^Derive/ }));
      await user.type(screen.getByTestId("proc"), "dna");
      await user.click(screen.getByTestId("fill-taken-1"));
      await user.click(screen.getByRole("button", { name: "Next" })); // details -> template
      await waitFor(() => expect(screen.getByTestId("tmpl-parent-error")).toHaveTextContent(expected));
      unmount();
    }
  });
});

describe("OperationWizard remember bundle", () => {
  it("offers the remember checkbox on the summary & confirm step, not the details step", async () => {
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await fillDerive(user, "dna");
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
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByTestId("toggle-remember"));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(postedTo[0]).toBe("derive");
    expect(posted[0]).toEqual({
      origin: { globalId: "SS1", amountTaken: { numericValue: 1, unitId: 3 } },
      processName: "dna extraction",
      sampleName: expect.any(String),
      count: 1,
      eachAmount: { numericValue: 5, unitId: 3 },
      templateId: 5,
      documentedByGlobalId: "SD1",
    });
    expect(ops().values).toEqual({
      "derive dna extraction": {
        values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 5, templateName: "T5" },
        documentation: { globalId: "SD1", name: "D1" },
      },
    });
    expect(ops().names).toEqual({ derive: ["dna extraction"] });
    expect(ops().defaults).toEqual({ derive: "dna extraction" });
  });

  it("performs a Pool: per-origin amounts posted for every origin, and remembered", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const first = makeMockSubSample({});
    const second = makeMockSubSample({ id: 2, globalId: "SS2" });
    vi.spyOn(first, "fetchAdditionalInfo").mockResolvedValue(undefined);
    vi.spyOn(second, "fetchAdditionalInfo").mockResolvedValue(undefined);
    render(<OperationWizard open onClose={onClose} origins={[first, second]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    await user.click(screen.getByTestId("mode-per"));
    await user.click(screen.getByTestId("fill-per-both"));
    await user.click(nextButton()); // amounts -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // documentation -> confirm
    await user.click(screen.getByTestId("toggle-remember"));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());

    const request = posted[0] as { origins: Array<{ globalId: string; amountTaken: unknown }> };
    expect(postedTo[0]).toBe("pool");
    expect(request.origins).toEqual([
      { globalId: "SS1", amountTaken: { numericValue: 1, unitId: 3 } },
      { globalId: "SS2", amountTaken: { numericValue: 1, unitId: 3 } },
    ]);

    const bundle = ops().values as Record<
      string,
      { amountMode?: string; perSubsampleAmounts?: Record<string, unknown> }
    >;
    const pooled = Object.values(bundle)[0];
    expect(pooled.amountMode).toBe("perSubsample");
    expect(pooled.perSubsampleAmounts).toEqual({
      SS1: { numericValue: 1, unitId: 3 },
      SS2: { numericValue: 1, unitId: 3 },
    });
  });

  it("persists nothing when remember is left unticked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "dna extraction");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(prefs.store.INVENTORY_OPERATIONS).toBeUndefined();
  });

  it("loads a saved bundle (ticked) when its process name is entered", async () => {
    rememberDerive(
      { mode: "pick", templateId: 9, templateName: "T9" },
      {
        count: 4,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    );
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");
    expect(screen.getByTestId("count")).toHaveTextContent("4");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":3}');
  });

  it("resets a restored bundle's amounts when its units belong to another category", async () => {
    rememberDerive(null, {
      count: 4,
      eachAmount: { numericValue: 7, unitId: 3 },
      amountTaken: { numericValue: 2, unitId: 3 },
    });
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({ quantity: { numericValue: 10, unitId: 7 } })]}
      />,
    );
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    // BOTH amounts have their unit cleared (unitId 0), not defaulted to the origin's: an unset unit
    // forces the amounts step, where the user picks a unit in the right category. The saved numbers
    // are kept so the user sees what to re-enter.
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":2,"unitId":0}');
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":0}');
    expect(screen.getByTestId("count")).toHaveTextContent("4");
  });

  it("keeps a restored bundle intact when its units are a different unit of the SAME category", async () => {
    rememberDerive(null, {
      count: 4,
      eachAmount: { numericValue: 7, unitId: 4 },
      amountTaken: { numericValue: 2, unitId: 4 },
    });
    const user = userEvent.setup();
    render(
      <OperationWizard
        open
        onClose={vi.fn()}
        origins={[makeMockSubSample({ quantity: { numericValue: 10, unitId: 3 } })]}
      />,
    );
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna");

    // Litres on a millilitre origin is a unit choice, not a mismatch.
    expect(screen.getByTestId("amount-taken")).toHaveTextContent('{"numericValue":2,"unitId":4}');
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":7,"unitId":4}');
  });

  it("resets to blank defaults (unticked) for a new, unsaved process name", async () => {
    rememberDerive(
      { mode: "pick", templateId: 9, templateName: "T9" },
      {
        count: 4,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 2, unitId: 3 },
      },
    );
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads the saved bundle
    expect(screen.getByTestId("count")).toHaveTextContent("4");
    await user.type(screen.getByTestId("proc"), "x"); // "dnax" is unsaved
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(screen.getByTestId("each-amount")).toHaveTextContent('{"numericValue":1,"unitId":3}');
  });

  it("unticking remember (on the confirmation) resets the form but never deletes the saved bundle", async () => {
    // amountTaken must not exceed the mock origin's quantity (1), or over-removal blocks the
    // step-one fast path this test rides to reach the confirmation.
    const saved = rememberDerive(
      { mode: "pick", templateId: 9, templateName: "T9" },
      {
        count: 4,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 1, unitId: 3 },
      },
    );
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads + ticks the saved bundle
    await waitFor(() => expect(screen.getByTestId("remember")).toHaveTextContent("true"), { timeout: 3000 });
    await user.click(screen.getByTestId("toggle-remember")); // untick
    expect(screen.getByTestId("count")).toHaveTextContent("1");
    expect(ops().values).toEqual(saved); // store untouched
  });

  it("re-ticking remember keeps the values typed after unticking and saves those, not the old bundle", async () => {
    rememberDerive(
      { mode: "pick", templateId: 9, templateName: "T9" },
      {
        count: 4,
        eachAmount: { numericValue: 7, unitId: 3 },
        amountTaken: { numericValue: 1, unitId: 3 },
      },
    );
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), "dna"); // loads + ticks the saved bundle
    await waitFor(() => expect(screen.getByTestId("remember")).toHaveTextContent("true"), { timeout: 3000 });
    await user.click(screen.getByTestId("toggle-remember")); // untick: back to details, defaults
    await user.click(screen.getByTestId("fill-taken-1")); // new data: eachAmount 5
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByTestId("toggle-remember")); // re-tick: must keep the new data
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(posted[0]).toMatchObject({ count: 1, eachAmount: { numericValue: 5, unitId: 3 }, templateId: 5 });
    expect(ops().values).toEqual({
      "derive dna": {
        values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 5, templateName: "T5" },
        documentation: null,
      },
    });
  });

  it("pre-fills the last-used process name and, on Review / edit, shows its bundle", async () => {
    ops().defaults = { derive: "boil" };
    ops().values = {
      "derive boil": {
        values: { count: 3, eachAmount: { numericValue: 8, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "none", templateId: null },
        documentation: null,
      },
    };
    const user = userEvent.setup();
    render(<OperationWizard open onClose={vi.fn()} origins={[makeMockSubSample({})]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(screen.getByTestId("remember")).toHaveTextContent("true");
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
    expect(screen.queryByTestId("proc")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    expect(screen.getByTestId("proc")).toHaveValue("boil");
    expect(screen.getByTestId("count")).toHaveTextContent("3");
  });

  it("performs a remembered run directly from the step-one fast path", async () => {
    ops().defaults = { derive: "boil" };
    ops().values = {
      "derive boil": {
        values: { count: 3, eachAmount: { numericValue: 8, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "none", templateId: null },
        documentation: null,
      },
    };
    const onClose = vi.fn();
    const origin = mockOrigin();
    const user = userEvent.setup();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(posted).toHaveLength(1);
  });

  it("persists a Cryopreserve bundle keyed by the operation (fixed process name)", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.cryopreserve\.label/i }));
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(nextButton()); // -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // -> amounts
    await user.click(nextButton()); // -> documentation
    await user.click(nextButton()); // -> confirm
    await user.click(screen.getByTestId("toggle-remember")); // tick on the confirm step
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    const stored = ops().values as Record<string, unknown>;
    expect(Object.keys(stored)).toEqual(["cryopreserve"]);
  });
});

describe("OperationWizard rejection paths and multi-origin gating", () => {
  it("keeps a Pool open on a non-field rejection, shows its message and re-reads every origin", async () => {
    rejectOnce(409, { message: "The subsample's quantity changed", errors: [""] });
    const user = userEvent.setup();
    const onClose = vi.fn();
    const first = mockOrigin();
    const second = mockOrigin({ id: 2, globalId: "SS2" });
    render(<OperationWizard open onClose={onClose} origins={[first, second]} />);

    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    await user.click(screen.getByTestId("fill-taken-1"));
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    await user.click(nextButton()); // amounts -> documentation
    await user.click(nextButton()); // documentation -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    // A non-field rejection carries `errors: [""]` (ApiError's singleton list), so the message body
    // is the reason.
    const alerts = addAlert.mock.calls.map((call) => call[0] as { variant: string; message: string });
    expect(alerts).toHaveLength(1);
    expect(alerts[0].variant).toBe("error");
    expect(alerts[0].message).toBe("The subsample's quantity changed");
    await waitFor(() => expect(first.fetchAdditionalInfo).toHaveBeenCalledTimes(1));
    expect(second.fetchAdditionalInfo).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(posted).toHaveLength(0);
  });

  it("reports a connection failure once, re-reads the origin and stays open for a retry", async () => {
    server.use(http.post(OPERATION_URL, () => HttpResponse.error(), { once: true }));
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await reachConfirm(user, "offline");
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(addAlert).toHaveBeenCalled());
    await waitFor(() => expect(origin.fetchAdditionalInfo).toHaveBeenCalledTimes(1));
    const alerts = addAlert.mock.calls.map((call) => call[0] as { variant: string; message: string });
    expect(alerts).toHaveLength(1);
    expect(alerts[0].variant).toBe("error");
    expect(alerts[0].message).toBe("Network Error");
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByTestId("confirm")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled();
  });

  it("gates a shared Pool amount on the SMALLEST origin, not on the first selected", async () => {
    // The larger origin comes FIRST, so only a reduce over every origin finds the 1 ml one.
    const user = userEvent.setup();
    const large = makeMockSubSample({ quantity: { numericValue: 5, unitId: 3 } });
    const small = makeMockSubSample({ id: 2, globalId: "SS2", quantity: { numericValue: 1, unitId: 3 } });
    render(<OperationWizard open onClose={vi.fn()} origins={[large, small]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.pool\.label/i }));
    await user.click(screen.getByTestId("fill-taken-3")); // 3 ml from each: fine for 5 ml, over for 1 ml
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    // Pool opens on "take all", which never over-removes
    expect(nextButton()).toBeEnabled();
    await user.click(screen.getByTestId("mode-same"));
    expect(nextButton()).toBeDisabled();
    await user.click(screen.getByTestId("fill-taken-1")); // 1 ml from each: within the smallest
    expect(nextButton()).toBeEnabled();
  });

  it("performs Passage with an explicit zero decrement and no client-computed passage number", async () => {
    // Passage declares no amount-taken input and leaves the origin untouched.
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = mockOrigin();
    render(<OperationWizard open onClose={onClose} origins={[origin]} />);
    await user.click(await screen.findByRole("button", { name: /operations\.passage\.label/i }));
    expect(screen.getByTestId("amount-taken")).toHaveTextContent("null");
    await user.click(nextButton()); // details -> template (the sample name is seeded from the origin)
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts (count 1 and each amount 1 ml prefilled)
    await user.click(nextButton()); // amounts -> documentation
    await user.click(nextButton()); // documentation -> confirm
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(postedTo[0]).toBe("passage");
    expect(posted[0]).toEqual({
      origin: { globalId: "SS1" },
      sampleName: expect.any(String),
      count: 1,
      eachAmount: { numericValue: 1, unitId: 3 },
      templateId: 5,
    });
    expect(posted[0]).not.toHaveProperty("passageNumber");
  });
});
