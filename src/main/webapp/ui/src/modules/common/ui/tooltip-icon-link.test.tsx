import { render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { TooltipIconLink } from "./tooltip-icon-link";

test("names the icon link from its tooltip label", () => {
  render(
    <TooltipIconLink label="Open booking" icon={<svg aria-hidden="true" />}>
      {/* biome-ignore lint/a11y/useAnchorContent: The wrapper supplies the icon and accessible name. */}
      <a href="/booking" />
    </TooltipIconLink>,
  );
  expect(screen.getByRole("link", { name: "Open booking" })).toHaveAttribute("href", "/booking");
});
