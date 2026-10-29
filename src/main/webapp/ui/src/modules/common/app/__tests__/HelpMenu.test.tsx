import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import HelpMenu from "@/modules/common/app/HelpMenu";
import type { AppConfig } from "@/modules/common/app/queries/config";

const helpMenuCases: Array<[string, AppConfig["helpLinks"], boolean]> = [
  ["without configured links", [], false],
  ["with configured links", [{ label: "Local help", url: "https://help.example.com" }], true],
];

const originalHdlh = window.hdlh;
const originalLighthouse = window.Lighthouse;
const originalOpen = window.open;

function setTestLighthouse(lighthouse: Window["Lighthouse"] | undefined) {
  Object.defineProperty(window, "Lighthouse", {
    configurable: true,
    value: lighthouse,
  });
}

describe("HelpMenu fallback", () => {
  afterEach(() => {
    window.hdlh = originalHdlh;
    window.open = originalOpen;
    setTestLighthouse(originalLighthouse);
    vi.restoreAllMocks();
  });

  it.each(helpMenuCases)(
    "opens the documentation site %s when Lighthouse is missing",
    async (_scenario, helpLinks, clickDocumentationItem) => {
      const open = vi.fn();
      window.open = open;
      setTestLighthouse(undefined);

      const user = userEvent.setup();
      render(<HelpMenu helpLinks={helpLinks} livechatProperties={{ livechatEnabled: false }} />);

      const helpButton = screen.getByRole("button", { name: "common:helpDocs.openHelp" });
      if (clickDocumentationItem) {
        await user.click(helpButton);
        await user.click(await screen.findByRole("menuitem", { name: "common:helpDocs.documentation" }));
      } else {
        await user.click(helpButton);
      }

      expect(open).toHaveBeenCalledWith("https://researchspace.helpdocs.io", "_blank", "noopener,noreferrer");
      expect(open).toHaveBeenCalledTimes(1);
    },
  );
});
