import type { ReactNode } from "react";
import { vi } from "vitest";

vi.mock("@/hooks/api/useUiPreference", async () => {
  const actual = await vi.importActual("@/hooks/api/useUiPreference");
  return {
    ...actual,
    UiPreferences: ({ children }: { children: ReactNode }) => children,
    default: (_preference: unknown, opts: { defaultValue: unknown }) => [
      opts.defaultValue,
      vi.fn(),
    ],
  };
});
