import { screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import workspace from "@/modules/common/i18n/locales/en-US/workspace.json";
import TransRichText from "@/modules/common/i18n/TransRichText";

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
