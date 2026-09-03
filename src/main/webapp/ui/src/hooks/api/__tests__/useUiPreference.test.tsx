import { act, renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
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
});
