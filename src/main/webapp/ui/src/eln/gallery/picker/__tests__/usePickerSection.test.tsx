import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { mockSetPreference } = vi.hoisted(() => ({ mockSetPreference: vi.fn() }));
vi.mock("@/hooks/api/useUiPreference", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/hooks/api/useUiPreference")>()),
  default: () => ["Chemistry", mockSetPreference],
}));

import usePickerSection from "../usePickerSection";

describe("usePickerSection", () => {
  beforeEach(() => {
    mockSetPreference.mockReset();
  });

  it("opens on the remembered section and remembers the next one", () => {
    const { result } = renderHook(() => usePickerSection());

    expect(result.current[0]).toBe("Chemistry");
    act(() => result.current[1]("Images"));
    expect(mockSetPreference).toHaveBeenCalledWith("Images");
  });

  it("opens on the section the caller asks for, whatever was remembered", () => {
    const { result } = renderHook(() => usePickerSection("NetworkFiles"));

    expect(result.current[0]).toBe("NetworkFiles");
  });

  it("lets the user leave that section without overwriting the remembered one", () => {
    const { result } = renderHook(() => usePickerSection("NetworkFiles"));

    act(() => result.current[1]("Images"));

    expect(result.current[0]).toBe("Images");
    expect(mockSetPreference).not.toHaveBeenCalled();
  });

  it("opens on that section again each time a picker that stays mounted is reopened", () => {
    const { result, rerender } = renderHook(({ open }) => usePickerSection("NetworkFiles", open), {
      initialProps: { open: true },
    });

    act(() => result.current[1]("Images"));
    rerender({ open: false });
    rerender({ open: true });

    expect(result.current[0]).toBe("NetworkFiles");
  });
});
