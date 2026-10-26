import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, onTestFinished, test } from "vitest";
import { page } from "vitest/browser";
import { InfoCardWithLongDescription, LAST_LINE } from "./InfoCard.story";
import { InfoCardPage } from "./pageObjects/InfoCardPage";

/*
 * Browser mode is required: the card opens in a tooltip, which closes when the
 * page scrolls, so a description that makes the card taller than the room
 * beside the badge cannot be read unless the card scrolls itself.
 */
describe("InfoCard", () => {
  afterEach(cleanup);

  test("a name and description at their caps scroll inside a card that fits on screen", async () => {
    const { innerWidth, innerHeight } = window;
    onTestFinished(() => page.viewport(innerWidth, innerHeight));
    await page.viewport(414, 600);
    render(<InfoCardWithLongDescription />);
    const card = new InfoCardPage();

    await card.open();
    await expect.element(card.card).toBeInViewport({ ratio: 1 });
    await expect.element(card.openButton).toBeInViewport({ ratio: 1 });

    await card.scrollToEnd();

    await expect.element(card.descriptionText(LAST_LINE)).toBeInViewport({ ratio: 1 });
    await expect.element(card.openButton).toBeInViewport({ ratio: 1 });
    await expect.element(card.card).toBeInViewport({ ratio: 1 });
  });
});
