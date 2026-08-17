import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { BookableItemsStory } from "./BookableItemsPage.story";
import { bookableItemsHandlers } from "./mocks/bookableItemsMocks";
import { BookableItemsPage } from "./pageObjects/BookableItemsPage";

const pageObj = new BookableItemsPage();
let collectionQuery = "";

beforeEach(() => {
  collectionQuery = "";
  window.history.replaceState({}, "", "/");
  worker.use(
    oauthTokenHandler(),
    ...bookableItemsHandlers((request) => {
      collectionQuery = decodeURIComponent(new URL(request.url).search);
    }),
  );
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the bookable items table", () => {
  test("searches a field from the target instrument", async () => {
    render(<BookableItemsStory />);

    await pageObj.search("confocal");

    await expect.poll(() => collectionQuery).toContain("target.name=contains=confocal");
  });

  test("lists target fields as optional hidden columns", async () => {
    render(<BookableItemsStory />);

    await pageObj.openColumns();

    await expect.element(pageObj.hiddenInstrumentName).toBeVisible();
    await expect.element(pageObj.hiddenDeleted).toBeVisible();
    await expect.element(pageObj.instrumentNameHeader).not.toBeInTheDocument();
    await expect.element(pageObj.deletedHeader).not.toBeInTheDocument();
  });
});
