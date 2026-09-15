import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import OperationWizard from "../OperationWizard";
import { rawConfig } from "./testOperations";

/*
 * OperationWizard.test.tsx mocks useUiPreference at module level (vi.mock is file-wide), so the
 * wizard and the real preference hook are never exercised together there. Perform saves three
 * preference keys in one handler, and each save is a read-merge-write of the whole UI settings
 * object; before the write chain (code review, finding 8) the last POST dropped the other two.
 * This file therefore uses the real hook and provider over an MSW-backed store, so a regression in
 * either the wizard's save order or the hook's chaining shows up as a missing key on the server.
 */

const PREFERENCE_URL = "/userform/ajax/preference";
const OPERATIONS_URL = "/api/inventory/v1/operations";

/** The server's UI_JSON_SETTINGS object, as the preference endpoint would persist it. */
let stored: Record<string, { value: unknown; time: number }> = {};
let writes = 0;

beforeEach(() => {
  stored = {};
  writes = 0;
  server.use(
    http.get(PREFERENCE_URL, () => HttpResponse.json(stored)),
    // Merges the one posted key, as the server does: the client no longer sends the whole object.
    http.post(PREFERENCE_URL, async ({ request }) => {
      const form = await request.formData();
      const key = String(form.get("key"));
      stored = { ...stored, [key]: JSON.parse(String(form.get("value"))) as (typeof stored)[string] };
      writes += 1;
      return HttpResponse.json({});
    }),
    http.get(`${OPERATIONS_URL}/config`, () => HttpResponse.json(rawConfig)),
    http.post(OPERATIONS_URL, () => HttpResponse.json({ id: 1, globalId: "SS9", name: "New" }, { status: 201 })),
    http.get("/api/inventory/v1/samples/validateNameForNewSample", () => HttpResponse.json({ valid: true })),
  );
});

vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => ({
    authStore: { isSynchronizing: false },
    // A remembered specific template is re-checked against the server on restore (RSDEV-1231), so
    // the bundles here resolve their template through this.
    searchStore: {
      search: { performSearch: vi.fn() },
      getTemplate: () => Promise.resolve({ id: 5, name: "T5", quantityCategory: "volume", deleted: false, fields: [] }),
    },
    uiStore: { addAlert: vi.fn() },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));
vi.mock("@/util/alerts", () => ({ showToastWhilstPending: (_msg: string, p: Promise<unknown>) => p }));
// Keeps the real AlertContext (its default export) and overrides only mkAlert: the wizard now
// reports preference save failures through useContext(AlertContext), so a mock with mkAlert alone
// left every test in this file throwing "No default export is defined".
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

// Minimal step stubs: only the controls this flow drives. The wizard's own step behaviour is
// covered in OperationWizard.test.tsx; here the subject is what Perform persists.
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
  it("persists all three remembered preferences from one Perform, none overwriting another", async () => {
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
    await user.type(screen.getByTestId("proc"), "dna extraction");
    await user.click(screen.getByTestId("fill-amounts"));
    await user.click(nextButton()); // details -> template
    await user.click(screen.getByTestId("tmpl-pick5"));
    await user.click(nextButton()); // template -> amounts
    await user.click(nextButton()); // amounts -> documentation
    await user.click(screen.getByTestId("doc-choose"));
    await user.click(nextButton()); // documentation -> confirm
    await user.click(screen.getByTestId("toggle-remember"));
    await user.click(screen.getByRole("button", { name: /wizard\.perform/i }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    // three separate single-key writes, so all three keys must survive on the server
    await waitFor(() => expect(writes).toBe(3));
    expect(Object.keys(stored).sort()).toEqual([
      "INVENTORY_OPERATION_PROCESS_NAMES",
      "INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS",
      // Derive's own collection (RSDEV-1231), not the legacy shared
      // INVENTORY_OPERATION_PROCESS_VALUES: nothing writes that key anymore.
      "INVENTORY_OPERATION_PROCESS_VALUES_DERIVE",
    ]);
    expect(stored.INVENTORY_OPERATION_PROCESS_VALUES_DERIVE.value).toEqual({
      "derive dna extraction": {
        values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 5, templateName: "T5" },
        documentation: { globalId: "SD1", name: "D1" },
      },
    });
    expect(stored.INVENTORY_OPERATION_PROCESS_NAMES.value).toEqual({ derive: ["dna extraction"] });
    expect(stored.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS.value).toEqual({ derive: "dna extraction" });
  });

  it("still loads a bundle saved under the legacy shared key before the per-operation split", async () => {
    // Pre-RSDEV-1231 data lives under INVENTORY_OPERATION_PROCESS_VALUES; nothing migrates it, so the
    // wizard must keep reading it as a fallback until the user re-Performs and re-saves it under
    // their operation's own key.
    stored = {
      INVENTORY_OPERATION_PROCESS_VALUES: {
        value: {
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
    await user.type(screen.getByTestId("proc"), "dna extraction");

    // The legacy bundle's amount was restored, so the wizard offers Perform straight away (step one
    // reviews a complete remembered bundle) instead of requiring the amounts step to be filled again.
    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled());
  });

  it("restores a bundle saved only under the new per-operation key on selecting that operation", async () => {
    // Codex review, PR #1090: selectOperation used to call stateForKey(op, ...) with `processValues`
    // still bound to whatever operation (or none) was PREVIOUSLY selected - the per-operation split
    // only takes effect on the render after `operation` state actually changes. A bundle that exists
    // ONLY under the new key (no legacy fallback to lean on) was therefore never restored on the very
    // pick that should load it. The saved process-name default pre-fills the process name on
    // selection alone, so this covers the exact repro: no typing, no manual Remember toggle.
    stored = {
      INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS: { value: { derive: "dna extraction" }, time: 0 },
      INVENTORY_OPERATION_PROCESS_VALUES_DERIVE: {
        value: {
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

    // No typing, no toggling Remember: selecting the operation alone must find and restore the
    // per-operation bundle, offering Perform straight away.
    await waitFor(() => expect(screen.getByRole("button", { name: /wizard\.perform/i })).toBeEnabled());
    // The process name came back with the bundle. Step one is showing the confirmation (that is what
    // the fast path means), so the details field holding it is only rendered once we step in.
    await user.click(screen.getByRole("button", { name: /wizard\.reviewEdit/i }));
    expect(screen.getByTestId("proc")).toHaveValue("dna extraction");
  });
});
