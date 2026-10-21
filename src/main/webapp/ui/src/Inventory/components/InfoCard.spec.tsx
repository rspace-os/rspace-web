import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, onTestFinished, test } from "vitest";
import { page } from "vitest/browser";
import { InfoCardWithLongDescription, LAST_LINE } from "./InfoCard.story";
import { InfoCardPage } from "./pageObjects/InfoCardPage";

const isFullyInViewport = (element: Element): boolean => {
  const rect = element.getBoundingClientRect();
  return rect.top >= 0 && rect.bottom <= window.innerHeight;
};

/*
 * Browser mode is required: the card opens in a tooltip, which closes when the
 * page scrolls, so a description that makes the card taller than the room
 * beside the badge cannot be read unless the card scrolls itself.
 */
describe("InfoCard", () => {
  afterEach(cleanup);

  test("a description at the cap scrolls inside a card that fits on screen", async () => {
    const { innerWidth, innerHeight } = window;
    onTestFinished(() => page.viewport(innerWidth, innerHeight));
    await page.viewport(414, 600);
    render(<InfoCardWithLongDescription />);
    const card = new InfoCardPage();

    await card.open();
    await expect.element(card.openButton).toBeVisible();
    await expect.poll(() => isFullyInViewport(card.card.element())).toBe(true);

    card.descriptionText(LAST_LINE).element().scrollIntoView();

    await expect.poll(() => isFullyInViewport(card.descriptionText(LAST_LINE).element())).toBe(true);
    expect(isFullyInViewport(card.card.element())).toBe(true);
  });
});
