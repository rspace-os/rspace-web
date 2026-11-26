import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { afterEach, expect, it, vi } from "vitest";

afterEach(() => vi.useRealTimers());

it("shares polling, waits for completion, and stops on hide", () => {
  vi.useFakeTimers();
  const html = vi.fn();
  const requests: { succeed: () => void; settle: () => void }[] = [];
  const get = vi.fn((_url: string, success: (progress: object) => void) => {
    const completions: (() => void)[] = [];
    requests.push({
      succeed: () => success({ percentComplete: 50, description: "Uploading", done: false }),
      settle: () => {
        for (const complete of completions) complete();
      },
    });
    return {
      always: (callback: () => void) => {
        completions.push(callback);
      },
    };
  });
  const jquery = Object.assign(() => ({ css: vi.fn(), html }), {
    get,
  });
  const sandbox = {
    RS: {
      blockingProgressBar: { show: (_config: object) => {}, hide: () => {} },
      blockPage: vi.fn(),
      unblockPage: vi.fn(),
      msg: (key: string) => key,
    },
    $: jquery,
    setTimeout,
    clearTimeout,
    console,
  };
  const source = readFileSync(resolve(__dirname, "../../../scripts/global.js"), "utf8");
  const assignment = source.slice(
    source.indexOf("RS.blockingProgressBar = (function"),
    source.indexOf("RS.blockPage = function"),
  );
  runInNewContext(assignment, sandbox);
  const bar = sandbox.RS.blockingProgressBar;

  for (let i = 0; i < 4; i++) bar.show({ progressType: "any" });
  vi.advanceTimersByTime(3000);
  expect(get).toHaveBeenCalledTimes(1);
  vi.advanceTimersByTime(9000);
  expect(get).toHaveBeenCalledTimes(1);
  bar.show({ progressType: "import", checkingInterval: 1000, msg: "Importing", showCancel: true });
  expect(sandbox.RS.blockPage).toHaveBeenLastCalledWith("Importing", true);
  requests[0].succeed();
  requests[0].settle();
  vi.advanceTimersByTime(999);
  expect(get).toHaveBeenCalledTimes(1);
  vi.advanceTimersByTime(1);
  expect(get).toHaveBeenCalledTimes(2);
  expect(get).toHaveBeenLastCalledWith("/progress/import", expect.any(Function));

  bar.hide();
  html.mockClear();
  vi.advanceTimersByTime(9000);
  expect(get).toHaveBeenCalledTimes(2);
  bar.show({ progressType: "import", checkingInterval: 1000, msg: "Importing", showCancel: true });
  // A new lifecycle waits for the old request without starting a second timer chain.
  expect(vi.getTimerCount()).toBe(0);
  vi.advanceTimersByTime(3000);
  expect(get).toHaveBeenCalledTimes(2);
  requests[1].succeed();
  requests[1].settle();
  expect(html).not.toHaveBeenCalled();
  expect(vi.getTimerCount()).toBe(1);
  vi.advanceTimersByTime(999);
  expect(get).toHaveBeenCalledTimes(2);
  vi.advanceTimersByTime(1);
  expect(get).toHaveBeenCalledTimes(3);
  expect(get).toHaveBeenLastCalledWith("/progress/import", expect.any(Function));
  // A failed request still schedules the next poll after the configured delay.
  requests[2].settle();
  vi.advanceTimersByTime(999);
  expect(get).toHaveBeenCalledTimes(3);
  vi.advanceTimersByTime(1);
  expect(get).toHaveBeenCalledTimes(4);
  bar.hide();
  requests[3].settle();
  bar.hide();
  vi.advanceTimersByTime(9000);
  expect(get).toHaveBeenCalledTimes(4);
});
