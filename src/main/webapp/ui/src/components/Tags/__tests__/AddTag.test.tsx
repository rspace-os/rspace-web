import "@/__tests__/__mocks__/muiTransitions";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import AddTag from "../AddTag";
import { FINAL_DATA_SIGNAL } from "../ParseEncodedTagStrings";

describe("AddTag", () => {
  beforeEach(() => {
    fetchMock.mockResponse(JSON.stringify({ data: [] }));
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  test("opening the popover does not log a missing-input-element error", async () => {
    const user = userEvent.setup();
    fetchMock.mockResponse(JSON.stringify({ data: ["alpha", "beta", FINAL_DATA_SIGNAL] }));
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<AddTag enforceOntologies={false} onSelection={() => {}} value={[]} />);

    await user.click(screen.getByText("common:tags.addTag"));

    await waitFor(() => {
      expect(screen.getByLabelText("common:tags.filterSuggestedTags")).toBeVisible();
    });

    // give effects a tick to flush
    await new Promise((r) => setTimeout(r, 50));

    const messages = errorSpy.mock.calls.map((c) => c.map((arg) => String(arg)).join(" "));

    expect(messages).toEqual([]);
  });
});
