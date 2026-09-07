import { composeStories } from "@storybook/tanstack-react";
import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, test } from "vitest";
import { page } from "vitest/browser";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import * as stories from "./button.stories";

const { Default } = composeStories(stories);
const buttonPage = {
  get button() {
    return page.getByRole("button", { name: "Button", exact: true });
  },
};

afterEach(() => {
  cleanup();
  document.documentElement.classList.remove("dark");
});

test.each(["light", "dark"])("keeps primary button text readable on hover in %s mode", async (theme) => {
  document.documentElement.classList.toggle("dark", theme === "dark");
  render(<Default />);
  await expect.element(buttonPage.button).toBeVisible();
  await buttonPage.button.hover();
  await Promise.all(
    buttonPage.button
      .element()
      .getAnimations()
      .map((animation) => animation.finished),
  );
  await expectNoAxeViolations();
});
