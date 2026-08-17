import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
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

  test("reaches the instrument's custom fields through the field dropdown only", async () => {
    render(<BookableItemsStory />);

    await pageObj.openFilters();

    await expect.element(pageObj.fieldSelect).toBeVisible();
    await userEvent.click(pageObj.fieldSelect);
    await expect.element(page.getByRole("option", { name: "Custom field on Bookable item…" })).toBeVisible();
    await expect.element(page.getByRole("group", { name: "Related record fields" })).toBeVisible();
    await expect.element(page.getByRole("option", { name: "Bookable item → Instrument name" })).toBeVisible();
    await expect.element(page.getByRole("option", { name: /Hazard class/ })).not.toBeInTheDocument();

    await pageObj.chooseCustomFieldSource();

    await expect.element(pageObj.customFieldSearch).toBeVisible();
    await expect.element(pageObj.valueInput).not.toBeInTheDocument();
    await expect.element(pageObj.valueTextbox).not.toBeInTheDocument();
  });

  test("filters on a text custom field with a value the user types", async () => {
    render(<BookableItemsStory />);

    await pageObj.openFilters();
    await pageObj.chooseCustomFieldSource();
    await userEvent.fill(pageObj.customFieldSearch, "haz");

    const definition = page.getByRole("option", { name: /Hazard class/ });
    await expect.element(definition).toBeVisible();
    await userEvent.click(definition);

    await expect.element(pageObj.operatorSelect).toBeVisible();

    await expect.element(pageObj.valueTextbox).toBeVisible();
    await expect.element(pageObj.valueInput).not.toBeInTheDocument();
    await userEvent.fill(pageObj.valueTextbox, "BSL-2");
    await userEvent.click(page.getByRole("button", { name: "Apply filters" }));

    await expect.poll(() => collectionQuery).toContain("target.customFields.SF152==BSL-2");
  });

  test("keeps a value picker for a custom field that publishes its own options", async () => {
    render(<BookableItemsStory />);

    await pageObj.openFilters();
    await pageObj.chooseCustomFieldSource();
    await userEvent.fill(pageObj.customFieldSearch, "training");
    await userEvent.click(page.getByRole("option", { name: /Requires training/ }));

    await userEvent.click(pageObj.valueInput);
    await expect.element(page.getByRole("option", { name: "supervised only" })).toBeVisible();
    await expect.element(page.getByRole("option", { name: "yes" })).toBeVisible();
    await userEvent.click(page.getByRole("option", { name: "yes" }));
    await userEvent.click(page.getByRole("button", { name: "Apply filters" }));

    await expect.poll(() => collectionQuery).toContain("target.customFields.SF160==yes");
  });

  test("offers a custom field as a column from the rule that filters on it", async () => {
    render(<BookableItemsStory />);

    await pageObj.openFilters();
    await pageObj.chooseCustomFieldSource();
    await userEvent.fill(pageObj.customFieldSearch, "haz");
    await userEvent.click(page.getByRole("option", { name: /Hazard class/ }));

    const showAsColumn = page.getByRole("checkbox", { name: "Show as column" });
    await expect.element(showAsColumn).toBeVisible();
    await userEvent.click(showAsColumn);

    const text = (selector: string) => () =>
      [...document.querySelectorAll(selector)].map((cell) => cell.textContent ?? "").join("|");
    await expect.poll(text("th")).toContain("Bookable item \u2192 Hazard class");
    await expect.poll(text("td")).toContain("BSL-2");
    await expect.poll(() => collectionQuery).toContain("target.customFields.SF152");
  });

  test("lists target fields as optional hidden columns", async () => {
    render(<BookableItemsStory />);

    await pageObj.openColumns();

    await expect.element(pageObj.hiddenInstrumentName).toBeVisible();
    await expect.element(pageObj.hiddenDeleted).toBeVisible();
    await expect.element(pageObj.instrumentNameHeader).not.toBeInTheDocument();
    await expect.element(pageObj.deletedHeader).not.toBeInTheDocument();
  });

  test("offers a target field that cannot be filtered on as a column only", async () => {
    render(<BookableItemsStory />);

    await pageObj.openColumns();
    await expect.element(pageObj.hiddenGlobalId).toBeVisible();

    await pageObj.openFilters();
    await userEvent.click(pageObj.fieldSelect);

    await expect.element(pageObj.fieldOption("Bookable item \u2192 Instrument name")).toBeVisible();
    await expect.element(pageObj.fieldOption("Bookable item \u2192 Global ID")).not.toBeInTheDocument();
  });
});
