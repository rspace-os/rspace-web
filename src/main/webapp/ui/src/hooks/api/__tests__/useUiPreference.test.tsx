import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useUiPreference, { PREFERENCES, UiPreferences } from "../useUiPreference";

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
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", () => HttpResponse.error()),
    );

    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.GALLERY_VIEW_MODE, { defaultValue: null }),
      { wrapper: UiPreferences },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    act(() => {
      result.current[1]("grid");
    });

    await waitFor(() => expect(consoleError).toHaveBeenCalled());
    consoleError.mockRestore();
  });
});
