import { screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import inventory from "../locales/en-US/inventory.json";
import workspace from "../locales/en-US/workspace.json";
import TransRichText from "../TransRichText";

test("renders the complete location instructions from the English catalog", async () => {
  await renderWithRealI18n(<TransRichText i18nKey="inventory:container.fields.locationsImage.instructions" />, {
    resources: { inventory },
    defaultNS: "inventory",
  });
  expect(screen.getByText("Tap on the image to add a location marker.", { exact: false })).toMatchTextContent(
    "Tap and hold on a marker, and then drag to adjust the marked location.",
  );
});

test("interpolates the Galaxy format as text inside the complete message", async () => {
  const format = "Document <name>";
  await renderWithRealI18n(
    <TransRichText i18nKey="workspace:tinymce.galaxy.allSelectedFilesCombined" values={{ format }} />,
    { resources: { workspace }, defaultNS: "workspace" },
  );
  expect(screen.getByText(format)).toHaveProperty("tagName", "STRONG");
  expect(screen.getByText("All selected files will be combined", { exact: false })).toMatchTextContent(
    "The list dataset will be named after this RSpace document, using the format:",
  );
});
