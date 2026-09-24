import { expect } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

const BIOPORTAL_DISABLED = "Use My LabGroups page to enable inclusion of BioPortal Ontologies suggestions.";
const BIOPORTAL_ENABLED = "BioPortal Ontologies being added to suggestions.Disable on My LabGroups Page.";
const BIOPORTAL_SUGGESTION =
  /^.+ - ontology: \S+, version: https:\/\/bioportal\.bioontology\.org\/ontologies\/\S+ +on: .+, uri:https?:\/\/\S+$/;

const bioPortalSuggestions = (suggestions: string[]) =>
  suggestions.filter((suggestion) => suggestion.includes("version: https://bioportal.bioontology.org/ontologies/"));

test.describe("BioPortal ontology suggestions", () => {
  test.skip(
    env.integrationMode !== "real",
    "BioPortal's API host is fixed to data.bioontology.org, so real mode (with bioportal.api.key set) is required",
  );

  test("As a PI of two lab groups, BioPortal suggestions only appear once every group allows them", async ({
    appUser,
    clientSysadmin,
    flowRefreshDocumentSession,
    pageGroupView,
    pageWorkspace,
  }) => {
    test.setTimeout(120_000);

    const [firstGroup, secondGroup] = await test.step("Given I'm the PI of two lab groups", async () => {
      const groups = [];
      for (const prefix of ["e2e-bioportal-lab1", "e2e-bioportal-lab6"]) {
        groups.push(
          await clientSysadmin.createGroup({
            displayName: uniqueName(prefix),
            type: "LAB_GROUP",
            users: [{ username: appUser.username, roleInGroup: "PI" }],
          }),
        );
      }
      await flowRefreshDocumentSession();
      return groups;
    });

    await test.step("When BioPortal is allowed in only one of them", async () => {
      await pageGroupView.open(firstGroup.id);
      await pageGroupView.setBioPortalOntologiesAllowed(true);
      await pageGroupView.open(secondGroup.id);
      expect(await pageGroupView.isBioPortalOntologiesAllowed()).toBe(false);
    });

    await test.step("Then the tag editor says BioPortal isn't enabled, and 'Tol' returns no BioPortal suggestions", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const suggestions = await editor.header.searchTagSuggestions("Tol");
      await expect(editor.header.tagEditorNotice).toHaveText(BIOPORTAL_DISABLED);
      expect(bioPortalSuggestions(suggestions)).toEqual([]);
      await editor.editToolbar.saveAndClose();
    });

    await test.step("When BioPortal is also allowed in the other group", async () => {
      await pageGroupView.open(secondGroup.id);
      await pageGroupView.setBioPortalOntologiesAllowed(true);
    });

    await test.step("Then the tag editor says BioPortal is enabled, and 'Tol' returns well-formed BioPortal suggestions", async () => {
      await pageWorkspace.open();
      const editor = await pageWorkspace.createBasicDocument();
      const suggestions = await editor.header.searchTagSuggestions("Tol");
      await expect(editor.header.tagEditorNotice).toHaveText(BIOPORTAL_ENABLED);
      const fromBioPortal = bioPortalSuggestions(suggestions);
      expect(fromBioPortal.length, "BioPortal returns suggestions for 'Tol'").toBeGreaterThan(0);
      for (const suggestion of fromBioPortal) {
        expect(suggestion).toMatch(BIOPORTAL_SUGGESTION);
      }
      await editor.editToolbar.saveAndClose();
    });
  });
});
