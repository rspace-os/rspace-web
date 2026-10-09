import { render, waitFor } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { useBroadcastChannel } from "../broadcast";

function Listener({ onMessage }: { onMessage: (e: MessageEvent) => void }) {
  useBroadcastChannel("broadcast-test", onMessage);
  return null;
}

describe("useBroadcastChannel", () => {
  test("Should call the handler from the latest render.", async () => {
    const first = vi.fn();
    const latest = vi.fn();
    const { rerender, unmount } = render(<Listener onMessage={first} />);
    rerender(<Listener onMessage={latest} />);

    const bc = new BroadcastChannel("broadcast-test");
    bc.postMessage("hello");
    bc.close();

    await waitFor(() => expect(latest).toHaveBeenCalledTimes(1));
    expect(first).not.toHaveBeenCalled();
    unmount();
  });
});
