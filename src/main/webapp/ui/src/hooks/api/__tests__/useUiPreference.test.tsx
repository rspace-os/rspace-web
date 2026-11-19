import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw/http";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import useUiPreference, { PREFERENCES, UiPreferences } from "../useUiPreference";

const wrapper = ({ children }: { children: React.ReactNode }) => <UiPreferences>{children}</UiPreferences>;

describe("useUiPreference", () => {
  test("the setter rejects and logs when the server does not save", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    server.use(
      http.get("/userform/ajax/preference", () => HttpResponse.json({})),
      http.post("/userform/ajax/preference", () => new HttpResponse(null, { status: 500 })),
    );
    const { result } = renderHook(
      () => useUiPreference<string | null>(PREFERENCES.WORKSPACE_DEFAULT_SEARCH, { defaultValue: null }),
      { wrapper },
    );
    await waitFor(() => expect(result.current).not.toBeNull());

    await expect(result.current[1]("x")).rejects.toThrow();
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});
