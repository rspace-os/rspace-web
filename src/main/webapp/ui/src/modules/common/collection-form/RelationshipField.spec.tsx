import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { RenderFieldsPage } from "./pageObjects/RenderFieldsPage";
import { RenderFieldsStory } from "./RenderFields.story";

const form = new RenderFieldsPage();

afterEach(() => {
  cleanup();
});

describe("RelationshipField", () => {
  test("reads and writes a to-one relationship value", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.owner).toHaveValue("Ada Lovelace");
    await form.chooseOwner("Grace Hopper");
    await form.addCollaborator("Katherine Johnson");
    await form.removeCollaborator("Grace Hopper");

    await expect.element(form.values).toHaveTextContent('"ownerId":{"relationTo":"users","value":"user-2"}');
    await expect.element(form.values).toHaveTextContent('"collaboratorIds":["user-3"]');
  });

  test("renders React content supplied for a relationship option", async () => {
    render(<RenderFieldsStory />);

    await form.openOwner();

    await expect.element(form.richRelationshipContent).toBeVisible();
  });
});
