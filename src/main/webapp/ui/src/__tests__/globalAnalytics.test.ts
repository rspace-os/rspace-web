import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { expect, it, vi } from "vitest";

it("queues legacy events until analytics is available, then forwards with default properties", () => {
  const legacyWindow: {
    analytics?: Window["analytics"];
    RS: { trackEvent: (name: string, properties?: object) => void };
  } = {
    RS: { trackEvent: vi.fn() },
  };
  // Ignore unrelated ready/click handlers and jQuery button aliases.
  const jquery = Object.assign(() => ({ click: () => {} }), { fn: {} });
  runInNewContext(readFileSync(resolve(__dirname, "../../../scripts/global.js"), "utf8"), {
    window: legacyWindow,
    $: jquery,
  });

  legacyWindow.RS.trackEvent("Before init", { queued: true });
  legacyWindow.RS.trackEvent("Without properties");
  expect(legacyWindow.analytics).toHaveProperty("_queue", [
    ["Before init", { queued: true }],
    ["Without properties", {}],
  ]);

  const track = vi.fn();
  legacyWindow.analytics = { track };
  legacyWindow.RS.trackEvent("After init", { queued: false });
  legacyWindow.RS.trackEvent("Without properties");
  expect(track).toHaveBeenNthCalledWith(1, "After init", { queued: false });
  expect(track).toHaveBeenNthCalledWith(2, "Without properties", {});
});
