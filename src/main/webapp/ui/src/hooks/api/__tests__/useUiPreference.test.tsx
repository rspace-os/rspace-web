import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useUiPreference, { PREFERENCES, UiPreferences } from "../useUiPreference";

describe("useUiPreference", () => {
  it("serialises concurrent writes so every changed key survives on the server", async () => {
    // Code review, finding 8: each setter used to read the full preference object, merge one key and
    // POST it back independently, so three setters fired in one tick all read the same snapshot and
    // the last POST overwrote the other two keys.
    let stored: Record<string, unknown> = {};
    const posted: Array<Record<string, unknown>> = [];
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json(stored)),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        stored = JSON.parse(String(form.get("value"))) as Record<string, unknown>;
        posted.push(stored);
        return HttpResponse.json({});
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

    await waitFor(() => expect(posted).toHaveLength(3));
    expect(Object.keys(stored).sort()).toEqual(["GALLERY_SORT_BY", "GALLERY_SORT_ORDER", "GALLERY_VIEW_MODE"]);
  });
  it("does not let one provider's stalled write block another provider's", async () => {
    // The write chain exists to stop concurrent read-merge-writes clobbering each other, but a
    // module-level chain serialises every preference write in the app: one hung request would stall
    // unrelated writes (Gallery view mode, sysadmin columns) for the rest of the session.
    let releaseHungRead: () => void = () => {};
    const hungRead = new Promise<void>((resolve) => {
      releaseHungRead = resolve;
    });
    let readsAfterMount = 0;
    const posted: Array<string> = [];
    server.use(
      http.get("/userform/ajax/preference", async () => {
        readsAfterMount += 1;
        // The first two reads are the two providers mounting; the third is the stalled write's.
        if (readsAfterMount === 3) await hungRead;
        return HttpResponse.json({});
      }),
      http.post("/userform/ajax/preference", async ({ request }) => {
        const form = await request.formData();
        posted.push(String(form.get("value")));
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

    // The second provider must complete while the first is still waiting on its hung read.
    await waitFor(() => expect(posted.some((body) => body.includes("wide"))).toBe(true));
    releaseHungRead();
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
