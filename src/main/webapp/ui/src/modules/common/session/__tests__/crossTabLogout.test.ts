import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { startCrossTabLogout } from "../crossTabLogout";

const key = "rspace.logout";
const eventName = "rspace:session-ended";
const at = 2_000;
const message = { type: "logout", at, mode: "standalone" };

class LogoutChannel extends EventTarget {
  static receiver: LogoutChannel;
  static nameReceived: string;

  constructor(name: string) {
    super();
    LogoutChannel.receiver = this;
    LogoutChannel.nameReceived = name;
  }
}

function broadcast(data: unknown) {
  LogoutChannel.receiver.dispatchEvent(new MessageEvent("message", { data }));
}

function storageEvent(newValue: string, storageKey = key) {
  window.dispatchEvent(new StorageEvent("storage", { key: storageKey, newValue, storageArea: localStorage }));
}

describe("cross-tab logout", () => {
  const actualWindow = window;
  const assign = vi.fn();
  const onSessionEnded = vi.fn();
  const cancel = (event: Event) => event.preventDefault();

  beforeEach(() => {
    vi.spyOn(Date, "now").mockReturnValue(at + 9);
    vi.spyOn(performance, "now").mockReturnValue(10);
    vi.spyOn(performance, "timeOrigin", "get").mockReturnValue(at - 2);
    vi.stubGlobal("BroadcastChannel", LogoutChannel);
    vi.stubGlobal("location", { pathname: "/workspace", assign });
    sessionStorage.setItem("id_token", "tab-token");
    actualWindow.addEventListener(eventName, onSessionEnded);
  });

  afterEach(() => {
    const registered = vi.mocked(actualWindow.addEventListener).mock?.calls ?? [];
    for (const [type, listener] of registered) actualWindow.removeEventListener(type, listener);
    actualWindow.removeEventListener(eventName, onSessionEnded);
    actualWindow.removeEventListener(eventName, cancel);
    delete actualWindow.rspaceCrossTabLogoutStarted;
    localStorage.clear();
    sessionStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  function start() {
    vi.spyOn(actualWindow, "addEventListener");
    startCrossTabLogout();
  }

  it.each([
    ["standalone", "/login"],
    ["sso", "/public/ssologout"],
  ])("clears the token and navigates to the fixed %s destination", (mode, destination) => {
    onSessionEnded.mockImplementationOnce(() => expect(sessionStorage.getItem("id_token")).toBeNull());
    start();
    expect(LogoutChannel.nameReceived).toBe("rspace-session");

    broadcast({ ...message, mode, url: "/workspace" });

    expect(onSessionEnded).toHaveBeenCalledTimes(1);
    expect(assign).toHaveBeenCalledExactlyOnceWith(destination);
  });

  it("does not navigate when the editor cancels the event", () => {
    actualWindow.addEventListener(eventName, cancel);
    start();

    broadcast(message);

    expect(onSessionEnded).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem("id_token")).toBeNull();
    expect(assign).not.toHaveBeenCalled();
  });

  it("uses storage events when BroadcastChannel is unavailable", () => {
    vi.stubGlobal("BroadcastChannel", undefined);
    start();

    storageEvent(JSON.stringify({ at, mode: "sso" }));

    expect(assign).toHaveBeenCalledExactlyOnceWith("/public/ssologout");
  });

  it("checks stored markers on startup and pageshow when BroadcastChannel throws", () => {
    vi.stubGlobal(
      "BroadcastChannel",
      class {
        constructor() {
          throw new DOMException("BroadcastChannel is unavailable", "SecurityError");
        }
      },
    );
    localStorage.setItem(key, JSON.stringify({ at, mode: "standalone" }));
    start();
    expect(assign).toHaveBeenCalledExactlyOnceWith("/login");

    localStorage.setItem(key, JSON.stringify({ at: at + 1, mode: "sso" }));
    window.dispatchEvent(new Event("pageshow"));
    expect(assign).toHaveBeenLastCalledWith("/public/ssologout");
    expect(assign).toHaveBeenCalledTimes(2);
  });

  it.each(["storage", "broadcast"])("deduplicates the same logout when %s arrives first", (first) => {
    start();
    startCrossTabLogout();
    const marker = JSON.stringify({ at, mode: "standalone" });
    if (first === "storage") {
      storageEvent(marker);
      broadcast(message);
    } else {
      broadcast(message);
      storageEvent(marker);
    }
    localStorage.setItem(key, marker);
    window.dispatchEvent(new Event("pageshow"));

    expect(onSessionEnded).toHaveBeenCalledTimes(1);
    broadcast({ ...message, at: at + 1 });
    broadcast(message);
    expect(onSessionEnded).toHaveBeenCalledTimes(2);
  });

  it("ignores markers at or before the page start in wall-clock time", () => {
    localStorage.setItem(key, JSON.stringify({ at: at - 1, mode: "standalone" }));
    start();
    expect(onSessionEnded).not.toHaveBeenCalled();

    localStorage.setItem(key, JSON.stringify({ at, mode: "sso" }));
    window.dispatchEvent(new Event("pageshow"));
    expect(assign).toHaveBeenCalledExactlyOnceWith("/public/ssologout");
  });

  it("handles a logout recorded before an older page finished loading", () => {
    localStorage.setItem(key, JSON.stringify({ at, mode: "standalone" }));
    start();

    expect(assign).toHaveBeenCalledExactlyOnceWith("/login");
  });

  it("ignores invalid and unrelated messages and markers", () => {
    localStorage.setItem(key, "invalid JSON");
    start();

    for (const invalid of [
      null,
      { type: "login", at, mode: "standalone" },
      { type: "logout", mode: "standalone" },
      { ...message, at: "2000" },
      { ...message, at: Number.NaN },
      { ...message, mode: "unknown" },
    ]) {
      broadcast(invalid);
    }
    storageEvent(JSON.stringify({ at, mode: "standalone" }), "another-key");
    storageEvent("invalid JSON");
    storageEvent(JSON.stringify({ mode: "standalone" }));
    storageEvent(JSON.stringify({ at, mode: "unknown" }));

    expect(onSessionEnded).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("id_token")).toBe("tab-token");
  });

  it("ignores public pages", () => {
    vi.stubGlobal("location", { pathname: "/public/ssologout", assign });
    localStorage.setItem(key, JSON.stringify({ at, mode: "sso" }));
    start();

    expect(actualWindow.addEventListener).not.toHaveBeenCalled();
    expect(assign).not.toHaveBeenCalled();
  });

  it("ignores frames", () => {
    const frame = document.createElement("iframe");
    document.body.append(frame);
    try {
      vi.stubGlobal("window", frame.contentWindow);
      startCrossTabLogout();
      expect(frame.contentWindow?.rspaceCrossTabLogoutStarted).toBeUndefined();
      expect(assign).not.toHaveBeenCalled();
    } finally {
      frame.remove();
    }
  });
});
