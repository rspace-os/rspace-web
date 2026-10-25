import { expect } from "@playwright/test";
import type {
  InventoryDetailsPanel,
  InventoryMoreAction,
} from "@/__tests__/e2e/components/inventory/InventoryDetailsPanel";

const READ_ACTIONS: InventoryMoreAction[] = ["Export", "Print Barcode"];
export const EDIT_ACTIONS: InventoryMoreAction[] = ["Edit", "Move", "Trash"];

export async function expectActionsEnabled(
  detailsPanel: InventoryDetailsPanel,
  actions: InventoryMoreAction[],
): Promise<void> {
  for (const action of actions) {
    await detailsPanel.withActionControl(action, (control, timeout) => expect(control).toBeEnabled({ timeout }));
  }
}

export async function expectActionsDisabled(
  detailsPanel: InventoryDetailsPanel,
  actions: InventoryMoreAction[],
): Promise<void> {
  for (const action of actions) {
    await detailsPanel.withActionControl(action, (control, timeout) => expect(control).toBeDisabled({ timeout }));
  }
}

export async function expectFullAccess(detailsPanel: InventoryDetailsPanel): Promise<void> {
  await expect(detailsPanel.permissionAlert).toHaveCount(0);
  await expect(detailsPanel.section("Barcodes")).toBeVisible();
  await expect(detailsPanel.section("Attachments")).toBeVisible();
  await expect(detailsPanel.section("Access Permissions")).toBeVisible();
  await expectActionsEnabled(detailsPanel, ["Duplicate", "Add to Basket", ...READ_ACTIONS]);
}

async function expectNoFullAccessActions(detailsPanel: InventoryDetailsPanel): Promise<void> {
  await expect(detailsPanel.section("Attachments")).toHaveCount(0);
  await expect(detailsPanel.section("Access Permissions")).toHaveCount(0);
  await expect(detailsPanel.section("Locations and Content")).toHaveCount(0);
  await expectActionsEnabled(detailsPanel, ["Add to Basket"]);
  await expectActionsDisabled(detailsPanel, ["Duplicate", ...READ_ACTIONS, ...EDIT_ACTIONS]);
}

export async function expectPublicAccess(detailsPanel: InventoryDetailsPanel): Promise<void> {
  await expect(detailsPanel.permissionAlert).toContainText("You do not have permission to see any of the details");
  await expect(detailsPanel.section("Barcodes")).toHaveCount(0);
  await expectNoFullAccessActions(detailsPanel);
}

export async function expectRestrictedAccess(detailsPanel: InventoryDetailsPanel): Promise<void> {
  await expect(detailsPanel.permissionAlert).toContainText("You do not have permission to see all of the details");
  await expect(detailsPanel.section("Barcodes")).toBeVisible();
  await expectNoFullAccessActions(detailsPanel);
}
