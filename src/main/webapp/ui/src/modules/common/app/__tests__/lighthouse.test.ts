import Intercom from "@intercom/messenger-js-sdk";
import { afterEach, describe, expect, it, vi } from "vitest";
import { callLighthouse, configureLighthouse, showLighthouse } from "@/modules/common/app/lighthouse";

vi.mock("@intercom/messenger-js-sdk", () => ({ default: vi.fn() }));

type PartialLighthouse = Partial<Record<"hide" | "show" | "showButton" | "hideButton", () => void>>;

const originalHdlh = window.hdlh;
const originalLighthouse = window.Lighthouse;
const originalOpen = window.open;

function setTestLighthouse(lighthouse: PartialLighthouse | Window["Lighthouse"] | undefined) {
  Object.defineProperty(window, "Lighthouse", {
    configurable: true,
    value: lighthouse,
  });
}

describe("Lighthouse SDK manager", () => {
  afterEach(() => {
    window.hdlh = originalHdlh;
    window.open = originalOpen;
    setTestLighthouse(originalLighthouse);
    vi.restoreAllMocks();
  });

  it("starts anonymous livechat without claiming a user identity", () => {
    configureLighthouse({
      livechatProperties: { livechatEnabled: true, livechatServerKey: "app-key" },
      onReady: vi.fn(),
    });
    expect(Intercom).toHaveBeenCalledWith({ app_id: "app-key", hide_default_launcher: true });
  });

  it("ignores missing Lighthouse methods without throwing", () => {
    setTestLighthouse({});

    expect(() => callLighthouse("showButton")).not.toThrow();
  });

  it.each([
    ["missing", undefined],
    ["incomplete", { show: vi.fn() }],
  ] as const)("opens the documentation fallback when Lighthouse is %s", (_state, lighthouse) => {
    const open = vi.fn();
    window.open = open;
    setTestLighthouse(lighthouse);

    showLighthouse();

    expect(open).toHaveBeenCalledWith("https://researchspace.helpdocs.io", "_blank", "noopener,noreferrer");
    expect(open).toHaveBeenCalledTimes(1);
  });

  it("calls the requested Lighthouse methods when they exist", () => {
    const show = vi.fn();
    const showButton = vi.fn();
    const open = vi.fn();
    window.open = open;
    setTestLighthouse({ show, showButton });

    showLighthouse();

    expect(showButton).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledTimes(1);
    expect(open).not.toHaveBeenCalled();
  });

  it("routes contact navigation to support email when livechat is disabled", () => {
    const open = vi.fn();
    window.open = open;

    configureLighthouse({
      livechatProperties: {
        livechatEnabled: false,
      },
      onReady: vi.fn(),
    });

    window.hdlh.onNavigate?.({ page: "contact" });

    expect(open).toHaveBeenCalledWith("mailto:support@researchspace.com", "_blank");
  });
});
