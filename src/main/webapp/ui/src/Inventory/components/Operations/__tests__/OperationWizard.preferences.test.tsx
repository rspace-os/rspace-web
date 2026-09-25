import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";

/*
 * OperationWizard.test.tsx mocks useUiPreference, so this file runs the wizard with the real hook
 * and provider over an MSW store that, like the server, replaces the whole settings object on save.
 */

const PREFERENCE_URL = "/userform/ajax/preference";
const OPERATION_URL = "/api/inventory/v1/operations/:key";

let stored: Record<string, { value: unknown; time: number }> = {};
let writes = 0;

beforeEach(() => {
  stored = {};
  writes = 0;
  server.use(
    http.get(PREFERENCE_URL, () => HttpResponse.json(stored)),
    http.post(PREFERENCE_URL, async ({ request }) => {
      const form = await request.formData();
      stored = JSON.parse(String(form.get("value"))) as typeof stored;
      writes += 1;
      return HttpResponse.json({});
    }),
    http.post(OPERATION_URL, () =>
      HttpResponse.json({ sample: { id: 1, globalId: "SS9", name: "New" } }, { status: 201 }),
    ),
    http.get("/api/inventory/v1/samples/validateNameForNewSample", () => HttpResponse.json({ valid: true })),
  );
});

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    searchStore: {
      search: { performSearch: vi.fn(), fetcher: { permalink: null, performInitialSearch: vi.fn() } },
      getTemplate: () => Promise.resolve({ id: 5, name: "T5", quantityCategory: "volume", deleted: false, fields: [] }),
    },
    uiStore: { addAlert: vi.fn() },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));
vi.mock("@/util/alerts", () => ({ showToastWhilstPending: (_msg: string, p: Promise<unknown>) => p }));
vi.mock("@/stores/contexts/Alert", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/stores/contexts/Alert")>()),
  mkAlert: (x: unknown) => x,
}));
vi.mock("@/components/SubmitSpinnerButton", () => ({
  default: ({ onClick, label, disabled }: { onClick: () => void; label: string; disabled?: boolean }) => (
    <button type="button" onClick={onClick} disabled={disabled}>
      {label}
    </button>
  ),
}));
vi.mock("../../ContextMenu/ContextDialog", () => ({
  default: ({ open, children }: { open: boolean; children: React.ReactNode }) => (open ? <div>{children}</div> : null),
}));

vi.mock("../OperationDetailsStep", () => ({
  default: ({
    values,
    onChange,
  }: {
    values: Record<string, unknown>;
    onChange: (v: Record<string, unknown>) => void;
  }) => (
    <div>
      <input
        data-testid="proc"
        value={String(values.processName ?? "")}
        onChange={(e) => onChange({ ...values, processName: e.target.value })}
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
    </div>
  ),
}));
vi.mock("../TemplateStep", () => ({
  default: ({ onChange }: { onChange: (v: unknown) => void }) => (
    <button
      type="button"
      data-testid="tmpl-pick5"
      onClick={() => onChange({ mode: "pick", templateId: 5, templateName: "T5" })}
    />
  ),
}));
vi.mock("../DocumentationStep", () => ({
  default: ({ onChange }: { onChange: (v: unknown) => void }) => (
    <button type="button" data-testid="doc-choose" onClick={() => onChange({ globalId: "SD1", name: "D1" })} />
  ),
}));
vi.mock("../OperationConfirmation", () => ({
  default: ({ remember, onRememberChange }: { remember?: boolean; onRememberChange?: (r: boolean) => void }) => (
    <div data-testid="confirm">
      {onRememberChange ? (
        <button type="button" data-testid="toggle-remember" onClick={() => onRememberChange(!remember)} />
      ) : null}
    </div>
  ),
}));

const nextButton = () => screen.getByRole("button", { name: /actions\.next/i });

describe("OperationWizard with the real preference hook", () => {
  async function performDeriveWithRemember(processName: string, beforePerform: () => void = () => {}) {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <UiPreferences>
          <OperationWizard open onClose={onClose} origins={[origin]} />
        </UiPreferences>
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await user.type(screen.getByTestId("proc"), processName);
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    await user.click(nextButton()); // amounts -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // documentation -> confirm
    await user.click(screen.getByTestId("toggle-remember"));
    beforePerform();
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    await waitFor(() => expect(writes).toBe(1));
  }

  it("saves the remembered values, process name and default together in one save", async () => {
    await performDeriveWithRemember("dna extraction");

    expect(stored.INVENTORY_OPERATIONS.value).toEqual({
      values: {
        "derive dna extraction": {
          values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
          template: { mode: "pick", templateId: 5, templateName: "T5" },
          documentation: { globalId: "SD1", name: "D1" },
        },
      },
      names: { derive: ["dna extraction"] },
      defaults: { derive: "dna extraction" },
    });
  });

  it("keeps what another tab saved after this page loaded", async () => {
    await performDeriveWithRemember("dna extraction", () => {
      stored = {
        INVENTORY_OPERATIONS: {
          value: { values: { aliquot: { from: "other tab" } }, names: { derive: ["PCR"] }, defaults: {} },
          time: 0,
        },
      };
    });

    const saved = stored.INVENTORY_OPERATIONS.value as {
      values: Record<string, unknown>;
      names: Record<string, Array<string>>;
    };
    expect(saved.values.aliquot).toEqual({ from: "other tab" });
    expect(Object.keys(saved.values)).toContain("derive dna extraction");
    expect(saved.names.derive).toEqual(["PCR", "dna extraction"]);
  });

  it("restores a saved bundle on selecting that operation", async () => {
    // No typing and no manual Remember toggle: only the restore on selecting the operation can
    // load this bundle.
    stored = {
      INVENTORY_OPERATIONS: {
        value: {
          values: {
            "derive dna extraction": {
              values: {
                count: 1,
                eachAmount: { numericValue: 5, unitId: 3 },
                amountTaken: { numericValue: 1, unitId: 3 },
              },
              template: { mode: "pick", templateId: 5, templateName: "T5" },
              documentation: { globalId: "SD1", name: "D1" },
            },
          },
          names: {},
          defaults: { derive: "dna extraction" },
        },
        time: 0,
      },
    };
    const user = userEvent.setup();
    const origin = makeMockSubSample({});
    vi.spyOn(origin, "fetchAdditionalInfo").mockResolvedValue(undefined);
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <UiPreferences>
          <OperationWizard open onClose={vi.fn()} origins={[origin]} />
        </UiPreferences>
      </QueryClientProvider>,
    );

    await user.click(await screen.findByRole("button", { name: /operations\.derive\.label/i }));
    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled());
    // The fast path opens on the confirmation, so the details field is only rendered once we
    // step in.
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    expect(screen.getByTestId("proc")).toHaveValue("dna extraction");
  });
});
