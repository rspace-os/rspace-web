import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";

test("Document edits survive expiry and can be saved after signing in again", async ({
  appUser,
  context,
  page,
  pageDocument,
  pageWorkspace,
}) => {
  const otherTab = await context.newPage();
  await otherTab.goto("/workspace");
  await pageWorkspace.open();
  const document = await pageWorkspace.createBasicDocument();
  const field = await document.getField("", 0);
  const unsavedText = "Unsaved result: 4.25 mg/mL (repeat #2).";

  await otherTab.getByRole("button", { name: "Account Menu" }).click();
  await otherTab.getByRole("menuitem", { name: "Log Out" }).click();
  await otherTab.waitForURL("**/login*");

  const requests: string[] = [];
  page.on("request", (request) => {
    const match = new URL(request.url()).pathname.match(/\/structuredDocument\/ajax\/([^/]+)$/);
    if (match) requests.push(match[1]);
  });
  const failedAutosave = page.waitForResponse(
    (response) => response.url().endsWith("/ajax/autosaveField") && response.status() === 403,
  );
  await field.fill(unsavedText);
  await document.editToolbar.saveMenuButton.click();
  await page.getByRole("menuitem", { name: "Save", exact: true }).click();
  await failedAutosave;

  const signIn = page.getByRole("button", { name: "Sign in", exact: true });
  await expect(signIn).toBeFocused();
  await expect(field.body).toHaveText(unsavedText);
  expect(requests).not.toContain("saveStructuredDocument");
  expect(requests).not.toContain("getUpdatedFields");

  await otherTab.close();
  const [loginTab] = await Promise.all([context.waitForEvent("page"), signIn.click()]);
  await loginTab.getByRole("textbox", { name: "Password" }).fill(appUser.password);
  await loginTab.getByRole("textbox", { name: "User" }).fill(appUser.username);
  await loginTab.getByRole("button", { name: "Log in" }).click();
  await loginTab.waitForURL("**/workspace**");
  await loginTab.close();

  await page.route("**/ajax/requestEdit", (route) => route.fulfill({ json: "CANNOT_EDIT_OTHER_EDITING" }), {
    times: 1,
  });
  requests.length = 0;
  await document.editToolbar.saveMenuButton.click();
  await page.getByRole("menuitem", { name: "Save", exact: true }).click();
  await expect(page.getByText(/it is currently being edited by/)).toBeVisible();
  await expect(field.body).toBeEditable();
  await expect(field.body).toHaveText(unsavedText);
  expect(requests).toContain("requestEdit");
  expect(requests).not.toContain("autosaveField");
  expect(requests).not.toContain("saveStructuredDocument");
  await page.getByRole("button", { name: "Ok", exact: true }).click();

  requests.length = 0;
  await document.editToolbar.saveAndView();
  await pageDocument.isLoaded();
  await expect(await pageDocument.getFieldViewContent("", 0)).toHaveText(unsavedText);
  const lockRequest = requests.indexOf("requestEdit");
  const autosaveRequest = requests.indexOf("autosaveField");
  const saveRequest = requests.indexOf("saveStructuredDocument");
  expect(lockRequest).toBeGreaterThanOrEqual(0);
  expect(autosaveRequest).toBeGreaterThan(lockRequest);
  expect(saveRequest).toBeGreaterThan(autosaveRequest);

  const reopened = await pageWorkspace.openDocument(document.getId());
  await expect(await reopened.getFieldViewContent("", 0)).toHaveText(unsavedText);
});

test("An expired session prompts sign-in when requesting the edit lock", async ({
  appUser,
  clientDocuments,
  context,
  page,
  pageWorkspace,
}) => {
  const document = await clientDocuments.create({
    name: "Session expiry before editing",
    fields: [{ content: "Saved before the session expired" }],
  });
  const viewedDocument = await pageWorkspace.openDocument(document.id);
  const viewedField = await viewedDocument.getFieldViewContent("", 0);

  const otherTab = await context.newPage();
  await otherTab.goto("/workspace");
  await otherTab.getByRole("button", { name: "Account Menu" }).click();
  await otherTab.getByRole("menuitem", { name: "Log Out" }).click();
  await otherTab.waitForURL("**/login*");

  const failedLock = page.waitForResponse(
    (response) => response.url().endsWith("/ajax/requestEdit") && response.status() === 403,
  );
  await viewedField.dblclick();
  const response = await failedLock;
  const lockParams = new URLSearchParams(response.request().postData() ?? "");
  expect.soft(lockParams.get("expectedUsername")).toBe(appUser.username);
  const prompt = page.getByRole("alertdialog", { name: "Your session has expired" });
  await expect(prompt.getByRole("button", { name: "Sign in" })).toBeFocused();
});

test("An inline title stays pending when the signed-in account changes", async ({ page, pageWorkspace }) => {
  await pageWorkspace.open();
  const document = await pageWorkspace.createBasicDocument();
  await page.route(
    "**/ajax/rename",
    (route) => route.fulfill({ status: 403, headers: { "X-RSpace-Session": "ended" } }),
    { times: 1 },
  );

  const title = "Title from the original editor";
  await document.header.name.click();
  const titleInput = page.locator("#recordNameInHeaderEditor");
  await titleInput.fill(title);
  await titleInput.press("Enter");

  const prompt = page.getByRole("alertdialog", { name: "Your session has expired" });
  await expect(prompt.getByRole("button", { name: "Sign in" })).toBeFocused();
  await prompt.getByRole("button", { name: "Cancel" }).click();
  await expect(titleInput).toBeFocused();
  await expect(titleInput).toHaveValue(title);

  await document.editToolbar.saveAndView();
  await expect(document.header.name).toHaveText(title);
});

test("A rejected autosave is acknowledged and Save can proceed", async ({ page, pageWorkspace }) => {
  await pageWorkspace.open();
  const document = await pageWorkspace.createBasicDocument();
  const field = await document.getField("", 0);
  let autosaves = 0;
  await page.route("**/ajax/autosaveField", (route) => {
    autosaves++;
    return route.fulfill({
      json: { data: null, errorMsg: { errorMessages: ["The field is too large"] } },
    });
  });
  await field.fill("Invalid field content");
  const saved = page.waitForResponse(
    (response) => response.url().endsWith("/ajax/saveStructuredDocument") && response.ok(),
    { timeout: 10_000 },
  );
  await document.editToolbar.saveMenuButton.click();
  await page.getByRole("menuitem", { name: "Save", exact: true }).click();
  await expect(page.getByText("Errors: The field is too large")).toBeVisible();
  await saved;
  await page.getByRole("button", { name: "Ok", exact: true }).click();
  await document.editToolbar.saveAndView();
  expect(autosaves).toBe(1);
});
