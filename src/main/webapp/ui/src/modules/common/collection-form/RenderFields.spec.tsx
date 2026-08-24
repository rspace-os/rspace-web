import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { RenderFieldsPage } from "./pageObjects/RenderFieldsPage";
import { CardSelectStory, RenderFieldsStory } from "./RenderFields.story";

const form = new RenderFieldsPage();

afterEach(() => {
  cleanup();
});

describe("RenderFields", () => {
  test("renders the configured fields accessibly", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.recordDetailsSection).toBeVisible();
    await expect.element(form.relationshipsSection).toBeVisible();
    await expect.element(form.hiddenId).not.toBeInTheDocument();
    await expectNoAxeViolations();
  });

  test("writes user changes to the form", async () => {
    render(<RenderFieldsStory />);

    await form.setTitle("Updated title");
    await form.setScore("17");
    await form.toggleEnabled();
    await form.chooseStatus("Published");

    await expect.element(form.values).toHaveTextContent('"title":"Updated title"');
    await expect.element(form.values).toHaveTextContent('"score":17');
    await expect.element(form.values).toHaveTextContent('"enabled":false');
    await expect.element(form.values).toHaveTextContent('"status":"published"');
  });

  test("renders string, object, and React select options", async () => {
    render(<RenderFieldsStory />);

    await form.openStatus();

    await expect.element(form.constantStatusOption).toBeVisible();
    await expect.element(form.objectStatusOption).toBeVisible();
    await expect.element(form.richStatusOption).toBeVisible();
  });

  test("filters select options as the user types", async () => {
    render(<RenderFieldsStory />);

    await form.typeStatus("Pub");

    await expect.element(form.richStatusOption).toBeVisible();
    await expect.element(form.constantStatusOption).not.toBeInTheDocument();
  });

  test("reactively applies field conditions", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.notes).toBeVisible();
    await form.toggleEnabled();
    await expect.element(form.notes).not.toBeInTheDocument();
    await form.toggleEnabled();
    await expect.element(form.notes).toBeVisible();
    await form.setNotes("hide");
    await expect.element(form.notes).not.toBeInTheDocument();
  });

  test("associates descriptions and validation errors with their controls", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.notes).toHaveAccessibleDescription("The human-readable name of the record.");
    await form.setTitle("");
    await expect.element(form.title).toBeInvalid();
    await expect.element(form.title).toHaveAccessibleDescription("Title is required.");
  });

  test("disables every rendered control", async () => {
    render(<RenderFieldsStory disabled />);

    await expect.element(form.title).toBeDisabled();
    await expect.element(form.notes).toBeDisabled();
    await expect.element(form.score).toBeDisabled();
    await expect.element(form.enabled).toBeDisabled();
    await expect.element(form.modifiedAt).toBeDisabled();
    await expect.element(form.status).toBeDisabled();
    await expect.element(form.owner).toBeDisabled();
    await expect.element(form.collaborators).toBeDisabled();
  });
});

describe("RenderFields card select", () => {
  test("renders rich option labels in an accessible responsive group", async () => {
    render(<CardSelectStory />);

    await expect.element(form.cardStatusGroup).toHaveClass("grid-cols-1", "sm:grid-cols-2", "lg:grid-cols-3");
    await expect.element(form.cardStatus("Draft")).toBeChecked();
    await expect.element(form.cardStatus("Published")).toBeDisabled();
    await expect.element(form.cardStatus("Archived")).toBeDisabled();
    expect(getComputedStyle(form.cardStatus("Draft").element()).backgroundColor).not.toBe(
      getComputedStyle(form.cardStatus("In review").element()).backgroundColor,
    );
    await expectNoAxeViolations();
  });

  test("selects cards with the keyboard and reacts to the complete form input", async () => {
    render(<CardSelectStory />);

    await form.chooseNextCardStatus("Draft");
    await expect.element(form.cardStatus("In review")).toBeChecked();
    await expect.element(form.values).toHaveTextContent('"status":"review"');

    await form.toggleEnabled();
    await expect.element(form.cardStatus("Published")).not.toBeDisabled();
    await expect.element(form.cardStatus("Archived")).toBeDisabled();
    await form.chooseCardStatus("Published");
    await expect.element(form.cardStatus("Published")).toBeChecked();
    await expect.element(form.values).toHaveTextContent('"status":"published"');
  });
});
