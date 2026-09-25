import { expect } from "@playwright/test";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";
import { readArchiveDocuments } from "@/__tests__/e2e/zipArchive";

test.describe("Document editor menus and history", () => {
  test("As a user, each of the seven TinyMCE menus exposes its expected actions", async ({ pageWorkspace }) => {
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const menus = {
      File: ["Save", "Print...", "Find and replace...", "Select all", "Undo", "Redo", "Create a snippet"],
      Insert: [
        "From Gallery",
        "External Link",
        "Internal Link",
        "Equation",
        "File from computer",
        "Special character...",
        "Nonbreaking space",
        "Date/time",
        "Horizontal line",
        "Comment",
        "Sketch",
      ],
      Format: [
        "Bold",
        "Italic",
        "Underline",
        "Clear formatting",
        "Strikethrough",
        "Superscript",
        "Subscript",
        "Formats",
        "Increase indent",
        "Decrease indent",
      ],
      Table: [
        "Insert Table",
        "Insert Advanced Table",
        "Insert Calculations Table",
        "Table properties",
        "Delete table",
        "Cell",
        "Row",
        "Column",
      ],
      View: ["Visual aids", "Show blocks", "Show invisible characters", "Source code", "Preview", "Fullscreen editing"],
      "Science Tools": ["Dilution calculator", "PCR Master Mix", "Other tools..."],
      "Online Tools": [
        "Buffer Calculator",
        "CalculatorSoup.com",
        "PubMed Advanced Search",
        "Sigma-Aldrich.com",
        "ScienceGateway.org",
      ],
    };
    for (const [name, expected] of Object.entries(menus)) {
      await field.openMenu(name);
      for (const item of expected) await expect.soft(field.menuItem(item), `${name} > ${item}`).toBeVisible();
      await field.closeMenu();
    }
  });

  test("As a user, the molarity calculator opens the intended external tool", async ({ pageWorkspace, context }) => {
    await context.route("https://www.sigmaaldrich.com/**", (route) =>
      route.fulfill({ status: 200, body: "Calculator boundary" }),
    );
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const popup = await field.openMolarityCalculator();
    await expect(popup).toHaveURL(
      "https://www.sigmaaldrich.com/chemistry/stockroom-reagents/learning-center/technical-library/molarity-calculator.html",
    );
    await popup.close();
  });

  test("As a user, each saved equation revision retains its original LaTeX, and an HTML payload never renders or executes", async ({
    page,
    pageWorkspace,
    pageDocument,
    pageDocumentRevisions,
  }) => {
    const name = uniqueName("e2e-equation-history");
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    await editor.header.rename(name);
    const id = editor.getId();
    // A <script> inserted via innerHTML never runs, so it couldn't detect unsanitised rendering; onerror does.
    const htmlPayload = "<img src=x onerror=alert(1)>";
    const equations = [String.raw`x^2 + \sqrt{y}`, htmlPayload, String.raw`\pi r^2`];
    const dialogs: string[] = [];
    page.on("dialog", async (dialog) => {
      dialogs.push(dialog.message());
      await dialog.dismiss();
    });
    const versions: number[] = [];
    const initialField = await editor.getField("", 0);
    await initialField.insertEquation(equations[0]);
    await editor.saveAndView();
    await pageDocumentRevisions.openForDocument(id);
    versions.push(await pageDocumentRevisions.latestVersion());
    for (const equation of equations.slice(1)) {
      await pageWorkspace.open();
      await pageWorkspace.searchBar.search(name);
      await pageWorkspace.table.openRecord(name);
      const field = await pageDocument.editField("", 0);
      await field.editEquation(equation);
      await field.saveAndFinishEditing();
      await pageDocumentRevisions.openForDocument(id);
      versions.push(await pageDocumentRevisions.latestVersion());
    }
    expect(new Set(versions).size, "Each explicit save creates a distinct revision").toBe(equations.length);
    for (const [index, version] of versions.entries()) {
      await pageDocumentRevisions.openForDocument(id);
      await pageDocumentRevisions.openVersion(version);
      await expect(await pageDocument.equationInField("", 0)).toHaveAttribute("data-equation", equations[index]);
      await expect(page.locator('img[src="x"]'), "The payload must not render as an element").toHaveCount(0);
    }
    expect(dialogs, "The payload is stored as equation text and never executed").toEqual([]);
  });
});

dynamicUserTest.describe("Document recovery and downloadable exports", () => {
  for (const notebookEntry of [false, true]) {
    dynamicUserTest(
      `As a user, I recover autosaved custom fields after reopening a ${notebookEntry ? "notebook entry" : "document"}`,
      async ({
        clientForms,
        clientDocuments,
        clientFolders,
        pageWorkspace,
        pageDocument,
        pageNotebook,
        componentDocumentFields,
      }) => {
        dynamicUserTest.setTimeout(90_000);
        const form = await clientForms.create({
          name: uniqueName("AutosaveForm"),
          fields: [
            { name: "MyDate", type: "Date", defaultValue: Date.UTC(2020, 0, 1) },
            { name: "MyNumber", type: "Number", defaultValue: 5 },
            { name: "MyString", type: "String", defaultValue: "original" },
            { name: "MyTime", type: "Time", defaultValue: 9900000 },
            { name: "MyChoice", type: "Choice", options: ["a", "b", "c"], defaultOptions: ["a"], multipleChoice: true },
            { name: "MyRadio", type: "Radio", options: ["a", "b"], defaultOption: "a" },
            { name: "MyText", type: "Text", defaultValue: "<p>Original text</p>" },
          ],
        });
        const notebook = notebookEntry
          ? await clientFolders.create({ name: uniqueName("AutosaveNotebook"), notebook: true })
          : undefined;
        const document = await clientDocuments.create({
          name: uniqueName("AutosaveDocument"),
          form: { id: form.id },
          ...(notebook ? { parentFolderId: notebook.id } : {}),
        });
        const recoveredText = alphaNumericUnique("RecoveredText");
        await pageWorkspace.openDocument(document.id);
        await componentDocumentFields.setInput("MyDate", "2020-01-08");
        await componentDocumentFields.setInput("MyNumber", "10.0");
        await componentDocumentFields.setInput("MyString", "Recovered string");
        await componentDocumentFields.setInput("MyTime", "20:40");
        await componentDocumentFields.setOptions("MyChoice", ["b", "c"]);
        await componentDocumentFields.setOptions("MyRadio", ["b"]);
        const field = await pageDocument.editField("MyText");
        await field.fillAndWaitForAutosave(recoveredText);

        await pageDocument.leaveWithoutSaving();
        if (notebook) {
          await pageWorkspace.searchBar.search(notebook.name);
          await pageWorkspace.table.openNotebook(notebook.name);
          await pageNotebook.isLoaded();
          await expect.poll(() => pageNotebook.header.getName()).toBe(document.name);
        } else {
          await pageWorkspace.openDocument(document.id);
        }
        for (const [name, value] of Object.entries({
          MyDate: "2020-01-08",
          MyNumber: "10.0",
          MyString: "Recovered string",
          MyTime: "20:40",
          MyChoice: notebook ? "b, c" : "b,c",
          MyRadio: "b",
        })) {
          if (notebook) {
            await expect.poll(() => componentDocumentFields.notebookValue(name)).toBe(value);
          } else {
            await expect(await componentDocumentFields.value(name)).toHaveText(value);
          }
        }
        if (notebook) {
          await expect.poll(() => componentDocumentFields.notebookValue("MyText")).toContain(recoveredText);
        } else {
          await expect(await pageDocument.getFieldViewContent("MyText")).toContainText(recoveredText);
        }
      },
    );
  }

  for (const format of ["html", "xml"] as const) {
    dynamicUserTest(
      `As a user, I download a complete ${format.toUpperCase()} export from its notification`,
      async ({ pageWorkspace, componentExportWizard, componentNotifications }) => {
        const name = uniqueName(`e2e-${format}-notification`);
        const content = alphaNumericUnique("ExportedDocumentContent");
        await pageWorkspace.open();
        const editor = await pageWorkspace.createBasicDocument();
        await editor.header.rename(name);
        await (await editor.getField("", 0)).fill(content);
        const document = await editor.saveAndView();
        await document.toolbar.actions.exportButton.click();
        await componentExportWizard.waitForOpen();
        await componentExportWizard.selectFormat(format);
        await componentExportWizard.next();
        await componentExportWizard.fillExportDescription(name);
        await componentExportWizard.submit();
        const download = await componentNotifications.downloadExport(name);
        expect(await download.failure()).toBeNull();
        expect(download.suggestedFilename()).toContain(name);
        const path = await download.path();
        if (!path) throw new Error("The completed export download has no local file.");
        const exportedDocuments = await readArchiveDocuments(path, format);
        expect(
          exportedDocuments.some((entry) => entry.includes(name) && entry.includes(content)),
          "Export contains the selected document's name and saved field contents",
        ).toBe(true);
      },
    );
  }
});
