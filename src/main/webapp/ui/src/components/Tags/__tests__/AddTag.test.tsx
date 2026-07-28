import "@/__tests__/__mocks__/muiTransitions";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import AddTag from "../AddTag";
import { ontologyTagsHandler } from "./mocks/tagsComboboxMocks";

describe("AddTag", () => {
  beforeEach(() => {
    server.use(ontologyTagsHandler({ allTags: [], requestedPositions: [] }));
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
    server.use(ontologyTagsHandler({ allTags: ["alpha", "beta"], requestedPositions: [] }));
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    render(<AddTag enforceOntologies={false} onSelection={() => {}} value={[]} />);

    await user.click(screen.getByText("common:tags.addTag"));

    await waitFor(() => {
      expect(screen.getByLabelText("common:tags.filterSuggestedTags")).toBeVisible();
    });

    // Flush the component's 300 ms debounce before jsdom is torn down.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 350));
    });

    const messages = errorSpy.mock.calls.map((c) => c.map((arg) => String(arg)).join(" "));

    expect(messages).toEqual([]);
  });
});
