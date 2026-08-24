import { afterEach, describe, expect, it, vi } from "vitest";
import { callLighthouse, loadLighthouseSdk, showLighthouse } from "@/modules/common/app/lighthouse";

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

  it("ignores missing Lighthouse methods without throwing", () => {
    setTestLighthouse({});

    expect(() => callLighthouse("showButton")).not.toThrow();
  });

  it("calls the requested Lighthouse methods when they exist", () => {
    const show = vi.fn();
    const showButton = vi.fn();
    setTestLighthouse({ show, showButton });

    showLighthouse();

    expect(showButton).toHaveBeenCalledTimes(1);
    expect(show).toHaveBeenCalledTimes(1);
  });

  it("configures the HelpDocs callbacks", () => {
    loadLighthouseSdk(
      {
        livechatEnabled: false,
        currentUser: "user1a",
      },
      vi.fn(),
    );

    expect(window.hdlh.widget_key).toBe("anqvq7xcs3n2jzflnzp7");
    expect(() => window.hdlh.onLoad()).not.toThrow();
    expect(() => window.hdlh.onHide()).not.toThrow();
  });

  it("routes contact navigation to support email when livechat is disabled", () => {
    const open = vi.fn();
    window.open = open;

    loadLighthouseSdk(
      {
        livechatEnabled: false,
        currentUser: "user1a",
      },
      vi.fn(),
    );

    window.hdlh.onNavigate?.({ page: "contact" });

    expect(open).toHaveBeenCalledWith("mailto:support@researchspace.com", "_blank");
  });
});
