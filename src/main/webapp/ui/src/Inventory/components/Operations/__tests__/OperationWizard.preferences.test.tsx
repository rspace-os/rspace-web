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
    http.post(PREFERENCE_URL, async ({ request }) => {
      const form = await request.formData();
      stored = JSON.parse(String(form.get("value"))) as typeof stored;
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
    searchStore: { search: { performSearch: vi.fn() } },
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
    // three separate read-merge-writes, so all three keys must survive on the server
    await waitFor(() => expect(writes).toBe(3));
    expect(Object.keys(stored).sort()).toEqual([
      "INVENTORY_OPERATION_PROCESS_NAMES",
      "INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS",
      "INVENTORY_OPERATION_PROCESS_VALUES",
    ]);
    expect(stored.INVENTORY_OPERATION_PROCESS_VALUES.value).toEqual({
      "derive dna extraction": {
        values: { count: 1, eachAmount: { numericValue: 5, unitId: 3 }, amountTaken: { numericValue: 1, unitId: 3 } },
        template: { mode: "pick", templateId: 5, templateName: "T5" },
        documentation: { globalId: "SD1", name: "D1" },
      },
    });
    expect(stored.INVENTORY_OPERATION_PROCESS_NAMES.value).toEqual({ derive: ["dna extraction"] });
    expect(stored.INVENTORY_OPERATION_PROCESS_NAME_DEFAULTS.value).toEqual({ derive: "dna extraction" });
  });
});
