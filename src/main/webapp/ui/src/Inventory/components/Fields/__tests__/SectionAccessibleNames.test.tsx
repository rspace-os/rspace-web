import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/barcode-detection-api";
import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { deploymentPropertyQueryClient, deploymentPropertyQueryKeys } from "@/hooks/api/deploymentPropertyQueryClient";
import { mockAttachment } from "@/stores/definitions/__tests__/Attachment/mocking";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import type SampleModel from "@/stores/models/SampleModel";
import { makeMockRootStore } from "@/stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import AccessPermissions from "../AccessPermissions";
import Attachments from "../Attachments/Attachments";
import BarcodesFormField from "../Barcodes/FormField";
import Identifiers from "../Identifiers/Identifiers";

vi.mock("@/common/InvApiService", () => ({ default: {} }));

function renderSections(sample: SampleModel) {
  deploymentPropertyQueryClient.setQueryData(deploymentPropertyQueryKeys.property("chemistry.provider"), "");
  return render(
    <ThemeProvider theme={materialTheme}>
      <storesContext.Provider value={makeMockRootStore({ searchStore: { activeResult: sample }, uiStore: {} })}>
        <BarcodesFormField fieldOwner={sample} factory={sample.factory} />
        <Identifiers fieldOwner={sample} />
        <Attachments fieldOwner={sample} />
        <AccessPermissions fieldOwner={sample} />
      </storesContext.Provider>
    </ThemeProvider>,
  );
}

function expectSectionGroupsNamed() {
  for (const section of ["barcodes", "identifiers", "attachments", "accessPermissions"]) {
    expect(screen.getByRole("group", { name: `inventory:formSections.${section}` })).toBeInTheDocument();
  }
  expect(screen.queryAllByRole("heading").filter((heading) => heading.textContent?.trim() === "")).toEqual([]);
}

describe("Inventory form sections", () => {
  test("in edit mode, each section and its listing toggles have accessible names", async () => {
    const sample = makeMockSample();
    sample.attachments = [mockAttachment({})];
    sample.setEditable(new Set(["barcodes", "identifiers", "attachments", "sharingMode", "sharedWith"]), true);
    const { container } = renderSections(sample);

    expectSectionGroupsNamed();
    expect(
      screen.getByRole("group", { name: "inventory:formSections.accessPermissions" }),
    ).toHaveAccessibleDescription();
    expect(screen.getByRole("button", { name: "inventory:fields.barcodes.toggle.hide" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "inventory:fields.attachments.toggle.hide" })).toBeInTheDocument();
    await expectAccessible(container);
  });

  test("in view mode, the toggles keep their names, including the disabled one", () => {
    renderSections(makeMockSample());

    expectSectionGroupsNamed();
    expect(screen.queryByRole("heading", { name: "inventory:formSections.barcodes" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "inventory:fields.barcodes.toggle.hide" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "inventory:fields.attachments.toggle.none" })).toBeDisabled();
  });
});
