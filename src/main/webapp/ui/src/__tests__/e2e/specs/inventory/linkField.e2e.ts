import { expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";
import { USERS } from "@/__tests__/e2e/users";

test.describe(`Inventory Link fields`, { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test(`As a user, I can create a Link field on a sample with a manually-typed target`, async ({
    pageInventory,
    clientInventory,
    componentToasts,
  }) => {
    const fieldName = "Related Item";
    const sampleName = uniqueName("e2e-link-manual-sample");

    const targetGlobalId = await test.step("Given a target sample exists", async () => {
      const target = await clientInventory.createSample({ name: uniqueName("e2e-link-manual-target") });
      return target.globalId;
    });

    await pageInventory.open();
    await pageInventory.isLoaded();
    const createMenu = await pageInventory.openCreateMenu();
    const newSample = await createMenu.newSample();
    await newSample.fillName(sampleName);
    await newSample.expandSection("Custom Fields");

    const linkEditor = await newSample.customFields().addNewLinkField(fieldName);
    await linkEditor.setRelationType("IsSupplementTo");
    await linkEditor.setTargetGlobalId(targetGlobalId);

    await expect.poll(() => linkEditor.isPinVersionEnabled()).toBe(true);
    await expect.poll(() => linkEditor.getVersionLabel()).toBe("Latest");

    await linkEditor.apply();
    await newSample.save();

    await expect(componentToasts.byText("successfully created.")).toBeVisible();

    const detailsPanel = pageInventory.detailsPanel;
    await detailsPanel.expandSection("Custom Fields");
    const card = detailsPanel.customFields().linkFieldCard(fieldName);
    await expect(card).toContainText("IsSupplementTo");
    await expect(card).toContainText(targetGlobalId);
    await expect(card).toContainText("Latest");
    await expect(card.getByRole("link", { name: `Open ${targetGlobalId}` })).toBeVisible();
  });

  test(`As a user, I can see the same message for an unreadable target and a nonexistent Global ID`, async ({
    pageInventory,
    apiContext,
  }) => {
    const fieldName = "Related Item";

    const unreadableGlobalId = await test.step("Given a sample exists that this viewer cannot read", async () => {
      const otherOwnerClient = new InventoryClient(apiContext, USERS.user9i.apiKey);
      const unreadableTarget = await otherOwnerClient.createSample({
        name: uniqueName("e2e-link-unreadable-target"),
      });
      return unreadableTarget.globalId;
    });

    const nonexistentGlobalId = `${unreadableGlobalId.slice(0, 2)}999999999`;

    const editor = await test.step("And I start adding a Link field on a new sample", async () => {
      await pageInventory.open();
      await pageInventory.isLoaded();
      const createMenu = await pageInventory.openCreateMenu();
      const newSample = await createMenu.newSample();
      await newSample.fillName(uniqueName("e2e-link-nondisclosure-sample"));
      await newSample.expandSection("Custom Fields");
      const linkEditor = await newSample.customFields().addNewLinkField(fieldName);
      await linkEditor.setRelationType("References");
      return linkEditor;
    });

    const nonexistentMessage = await test.step("When I target a genuinely nonexistent Global ID", async () => {
      await editor.setTargetGlobalId(nonexistentGlobalId);
      const message = await editor.applyAndGetTargetHelperText();
      expect(message).toContain("does not exist");
      return message;
    });

    const unreadableMessage = await test.step("When I target an existing-but-unreadable Global ID", async () => {
      await editor.clearTarget();
      await editor.setTargetGlobalId(unreadableGlobalId);
      return editor.applyAndGetTargetHelperText();
    });

    expect(nonexistentMessage.replace(nonexistentGlobalId, "<GID>")).toBe(
      unreadableMessage.replace(unreadableGlobalId, "<GID>"),
    );
  });

  test(`As a user, I can see duplicate Link-field names rejected on the same record`, async ({
    pageInventory,
    clientInventory,
  }) => {
    const fieldName = "Related Item";
    const sampleName = uniqueName("e2e-link-dup-sample");

    const targetGlobalId = await test.step("Given a target sample exists", async () => {
      const target = await clientInventory.createSample({ name: uniqueName("e2e-link-dup-target") });
      return target.globalId;
    });

    await pageInventory.open();
    await pageInventory.isLoaded();
    const createMenu = await pageInventory.openCreateMenu();
    const newSample = await createMenu.newSample();
    await newSample.fillName(sampleName);
    await newSample.expandSection("Custom Fields");
    const editor = await newSample.customFields().addNewLinkField(fieldName);
    await editor.setRelationType("References");
    await editor.setTargetGlobalId(targetGlobalId);
    await editor.apply();
    await newSample.save();

    const detailsPanel = pageInventory.detailsPanel;
    await detailsPanel.enterEditMode();
    await detailsPanel.expandSection("Custom Fields");
    const customFields = detailsPanel.customFields();
    await customFields.startNewField(fieldName);

    await expect
      .poll(() => customFields.getNewFieldNameHelperText())
      .toBe("You either already have a field with that name or that name is not permitted.");
  });
});
