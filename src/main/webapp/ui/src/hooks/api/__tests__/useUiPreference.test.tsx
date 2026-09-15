import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import AlertContext from "@/stores/contexts/Alert";
import useUiPreference, { PREFERENCES, UiPreferences } from "../useUiPreference";

/**
 * Wraps a `renderHook` under test with both providers a real page supplies: `UiPreferences` (always
 * present) and `AlertContext` with a caller-supplied `addAlert` (present everywhere - Gallery and
 * Sysadmin mount the generic Alerts component, Inventory its own adapter - unlike `getRootStore`,
 * which the hook no longer depends on precisely because it is NOT always bootstrapped outside
 * Inventory, Codex review, PR #1090).
 */
function withAlerts(addAlert: (alert: unknown) => void) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <AlertContext.Provider value={{ addAlert: addAlert as never, removeAlert: () => {} }}>
        <UiPreferences>{children}</UiPreferences>
      </AlertContext.Provider>
    );
  };
}

describe("useUiPreference", () => {
  it("writes one key at a time and never re-reads the whole object first", async () => {
    // Code review, finding 3: each setter used to read the full preference object, merge one key
    // and POST the lot back. Two writers that overlapped (two tabs, or two setters in one handler)
    // read the same snapshot and the later POST dropped the other's key. The server merges the one
    // key now, so the read before each write is gone and cannot go stale.
    let stored: Record<string, unknown> = {};
    const postedKeys: Array<string> = [];
    let reads = 0;
    server.use(
      http.get("/userform/ajax/preference", () => {
        reads += 1;
        return HttpResponse.json(stored);
      }),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const key = String(form.get("key"));
        stored = { ...stored, [key]: JSON.parse(String(form.get("value"))) as unknown };
        postedKeys.push(key);
        return HttpResponse.json({ data: JSON.stringify(stored) });
      }),
    );

    const { result } = renderHook(
      () => ({
        viewMode: useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
        sortBy: useUiPreference<string | null>(PREFERENCES.GALLERY_SORT_BY, { defaultValue: null }),
        sortOrder: useUiPreference<string | null>(PREFERENCES.GALLERY_SORT_ORDER, { defaultValue: null }),
      }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => {
      result.current.viewMode[1]("grid");
      result.current.sortBy[1]("name");
      result.current.sortOrder[1]("asc");
    });

    await waitFor(() => expect(postedKeys).toHaveLength(3));
    expect(postedKeys.sort()).toEqual(["GALLERY_SORT_BY", "GALLERY_SORT_ORDER", "GALLERY_VIEW_MODE"]);
    expect(Object.keys(stored).sort()).toEqual(["GALLERY_SORT_BY", "GALLERY_SORT_ORDER", "GALLERY_VIEW_MODE"]);
    // one read: the provider mounting. A read per write is what went stale.
    expect(reads).toBe(1);
  });

  it("sends the preference name, the key and the timestamped value", async () => {
    const fields: Array<Record<string, string>> = [];
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        fields.push({
          preference: String(form.get("preference")),
          key: String(form.get("key")),
          value: String(form.get("value")),
        });
        return HttpResponse.json({});
      }),
    );

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => {
      result.current[1]("grid");
    });

    await waitFor(() => expect(fields).toHaveLength(1));
    expect(fields[0].preference).toBe("UI_JSON_SETTINGS");
    expect(fields[0].key).toBe("GALLERY_VIEW_MODE");
    // the time is stored so an eviction policy stays possible later
    const sent = JSON.parse(fields[0].value) as { value: string; time: number };
    expect(sent.value).toBe("grid");
    expect(typeof sent.time).toBe("number");
  });

  it("writes two updates of ONE key in the order they were made", async () => {
    // The per-key chain (previous.then(...)) exists so two writes of one key land in order: without
    // it a slow first POST and a fast second one leave the server holding the OLDER value. No test
    // covered it - replacing the chain with a bare `void write` passed every test in this file
    // (parallel review, Q13).
    const order: Array<string> = [];
    let releaseFirst: () => void = () => undefined;
    const firstInFlight = new Promise<void>((resolve) => (releaseFirst = resolve));
    let seen = 0;
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const value = JSON.parse(String(form.get("value"))) as { value: string };
        // The first write stalls until released, so a chain-less implementation would let the
        // second overtake it.
        if (++seen === 1) await firstInFlight;
        order.push(value.value);
        return HttpResponse.json({ data: "{}" });
      }),
    );

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => {
      result.current[1]("grid");
      result.current[1]("list");
    });
    releaseFirst();

    await waitFor(() => expect(order).toHaveLength(2));
    expect(order).toEqual(["grid", "list"]);
  });

  it("keeps writing a key after one of its writes fails", async () => {
    // The .catch on the chain is what stops a failed write wedging that key forever: the rejected
    // promise would otherwise become the `previous` every later write of this key chains onto, so
    // every subsequent save is dropped. Unverified before (parallel review, Q13).
    const posted: Array<string> = [];
    let attempts = 0;
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const value = JSON.parse(String(form.get("value"))) as { value: string };
        if (++attempts === 1) return HttpResponse.error();
        posted.push(value.value);
        return HttpResponse.json({ data: "{}" });
      }),
    );
    const reportedErrors = vi.spyOn(console, "error").mockImplementation(() => undefined);

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => result.current[1]("grid"));
    await waitFor(() => expect(reportedErrors).toHaveBeenCalled());
    act(() => result.current[1]("list"));

    await waitFor(() => expect(posted).toEqual(["list"]));
    reportedErrors.mockRestore();
  });

  it("does not let a stalled write of one key block a different key", async () => {
    // The chain exists to order repeated writes of the SAME key and to report a failure once.
    // Sharing one chain across every key in a provider would make a single hung request block every
    // other preference in that provider for the rest of the session, which is the same head-of-line
    // problem, one level down.
    let releaseHungWrite: () => void = () => {};
    const hungWrite = new Promise<void>((resolve) => {
      releaseHungWrite = resolve;
    });
    const posted: Array<string> = [];
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const key = String(form.get("key"));
        if (key === "GALLERY_VIEW_MODE") await hungWrite;
        posted.push(key);
        return HttpResponse.json({});
      }),
    );

    const { result } = renderHook(
      () => ({
        viewMode: useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
        columns: useUiPreference<string | null>(PREFERENCES.SYSADMIN_USERS_TABLE_COLUMNS, { defaultValue: null }),
      }),
      { wrapper: UiPreferences },
    );
    // the provider renders null children until its own fetch resolves, so gate on a value from
    // inside it rather than on `result.current` itself
    await waitFor(() => expect(result.current?.viewMode).toBeDefined());

    act(() => {
      result.current.viewMode[1]("grid");
      result.current.columns[1]("wide");
    });

    await waitFor(() => expect(posted).toEqual(["SYSADMIN_USERS_TABLE_COLUMNS"]));
    releaseHungWrite();
  });

  it("does not let one provider's stalled write block another provider's", async () => {
    // The write chain orders one page's writes of the same key, but a module-level chain would
    // serialise every preference write in the app: one hung request would stall unrelated writes
    // (Gallery view mode, sysadmin columns) for the rest of the session.
    let releaseHungWrite: () => void = () => {};
    const hungWrite = new Promise<void>((resolve) => {
      releaseHungWrite = resolve;
    });
    const posted: Array<string> = [];
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const key = String(form.get("key"));
        if (key === "GALLERY_VIEW_MODE") await hungWrite;
        posted.push(key);
        return HttpResponse.json({});
      }),
    );

    const stalled = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    const independent = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.SYSADMIN_USERS_TABLE_COLUMNS, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(stalled.result.current).not.toBeNull());
    await waitFor(() => expect(independent.result.current).not.toBeNull());

    act(() => {
      stalled.result.current[1]("grid");
    });
    act(() => {
      independent.result.current[1]("wide");
    });

    await waitFor(() => expect(posted).toEqual(["SYSADMIN_USERS_TABLE_COLUMNS"]));
    releaseHungWrite();
  });

  it("reports a failed write instead of swallowing it", async () => {
    // Logging alone left a failed save invisible to the user: the session kept working off the
    // optimistic local state, so nothing looked wrong until a later login found the save had never
    // landed (RSDEV-1231, Codex review, PR #1090). A visible alert is the other half of "reports".
    const addAlert = vi.fn();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", () => HttpResponse.error()),
    );

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: withAlerts(addAlert) },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => {
      result.current[1]("grid");
    });

    await waitFor(() => expect(consoleError).toHaveBeenCalled());
    expect(addAlert).toHaveBeenCalled();
    const alert = addAlert.mock.calls[0][0] as { variant: string };
    expect(alert.variant).toBe("warning");
    consoleError.mockRestore();
  });

  it("keeps writing a key even when raising the failure alert itself throws", async () => {
    // getRootStore().uiStore.addAlert used to throw here whenever RootStore had not been
    // bootstrapped - true for every page outside Inventory, since only Inventory's Alerts adapter
    // wires UiStore.addAlert to anything real (Codex review, PR #1090). A throw inside this .catch
    // handler rejected the promise stored in `pendingWrites`, so every LATER write of that key
    // chained onto an already-rejected promise and silently skipped its POST, forever. Whatever
    // raises the alert must never be able to do that again, however badly it misbehaves.
    const throwingAddAlert = vi.fn(() => {
      throw new Error("no alert host mounted");
    });
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const posted: Array<string> = [];
    let attempts = 0;
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        const value = JSON.parse(String(form.get("value"))) as { value: string };
        if (++attempts === 1) return HttpResponse.error();
        posted.push(value.value);
        return HttpResponse.json({});
      }),
    );

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: withAlerts(throwingAddAlert) },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => result.current[1]("grid"));
    await waitFor(() => expect(throwingAddAlert).toHaveBeenCalled());
    act(() => result.current[1]("list"));

    await waitFor(() => expect(posted).toEqual(["list"]));
    consoleError.mockRestore();
  });
});
