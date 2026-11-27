import { expect, type Locator } from "@playwright/test";
import { dynamicUserTest } from "@/__tests__/e2e/fixtures/dynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { alphaNumericUnique, TINY_PNG, uniqueName } from "@/__tests__/e2e/testData";
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
      for (const item of expected) {
        await expect.soft(field.menuItem(item), `${name} > ${item}`).toBeVisible();
      }
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

test.describe("Document image annotations", () => {
  test("As a user, each copy of a Gallery image in a field keeps its own sketch annotation", async ({
    pageWorkspace,
    pageDocument,
    clientFiles,
  }) => {
    const fileName = `${alphaNumericUnique("e2eSketchImage")}.png`;
    await clientFiles.uploadFile({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const images = field.galleryImages;
    for (const count of [1, 2]) {
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Images");
      await picker.openFolder("Api Inbox");
      await picker.selectItem(fileName);
      await picker.add();
      await expect(images).toHaveCount(count);
      await field.moveCaretToNewLineAtEnd();
    }
    const annotationIds = (copies: Locator) =>
      copies.evaluateAll((nodes) => nodes.map((node) => node.getAttribute("data-id")));

    let sketch = await field.openSketch(images.nth(0));
    await sketch.drawStroke(0.15);
    await sketch.insert();
    sketch = await field.openSketch(images.nth(1));
    expect(await sketch.strokeCount(), "The raw copy opens without the other copy's stroke").toBe(0);
    await sketch.drawStroke(0.35);
    await sketch.insert();

    const [firstId, secondId] = await annotationIds(images);
    expect(firstId).toMatch(/^\d+$/);
    expect(secondId).toMatch(/^\d+$/);
    expect(secondId).not.toBe(firstId);
    const imageIds = await images.evaluateAll((nodes) => nodes.map((node) => node.id));
    expect(imageIds[1], "Both copies still show the same Gallery image").toBe(imageIds[0]);

    await editor.saveAndView();
    const savedField = await pageDocument.editField("", 0);
    const savedImages = savedField.galleryImages;
    expect(await annotationIds(savedImages)).toEqual([firstId, secondId]);
    for (const index of [0, 1]) {
      sketch = await savedField.openSketch(savedImages.nth(index));
      expect(await sketch.strokeCount(), `Copy ${index + 1} reopens with only its own stroke`).toBe(1);
      await sketch.cancel();
    }

    // Copies that already share one annotation, as this bug left them, separate when one is edited.
    await savedField.insertHtmlAtEnd(await savedImages.nth(0).evaluate((node) => node.outerHTML));
    await expect(savedImages).toHaveCount(3);
    sketch = await savedField.openSketch(savedImages.nth(2));
    expect(await sketch.strokeCount(), "The shared drawing is the starting point").toBe(1);
    await sketch.drawStroke(0.55);
    await sketch.insert();
    const [untouchedId, , editedId] = await annotationIds(savedImages);
    expect(untouchedId).toBe(firstId);
    expect(editedId).toMatch(/^\d+$/);
    expect([firstId, secondId]).not.toContain(editedId);
    sketch = await savedField.openSketch(savedImages.nth(0));
    expect(await sketch.strokeCount(), "The untouched copy keeps its original stroke").toBe(1);
    await sketch.cancel();

    // A link from before 1.29 shows the oldest annotation, so editing its explicit copy separates them.
    const legacySrc = `/image/getImage/${imageIds[0]}/1`;
    await savedField.insertHtmlAtEnd(`<img id="${imageIds[0]}" class="imageDropped" src="${legacySrc}" alt="image" />`);
    await expect(savedImages).toHaveCount(4);
    sketch = await savedField.openSketch(savedImages.nth(0));
    await sketch.drawStroke(0.75);
    await sketch.insert();
    const [separatedId] = await annotationIds(savedImages);
    expect(separatedId).toMatch(/^\d+$/);
    expect(separatedId).not.toBe(firstId);
    sketch = await savedField.openSketch(savedImages.nth(3));
    expect(await sketch.strokeCount(), "The legacy copy keeps the original stroke").toBe(1);
    await sketch.cancel();
  });

  test("As a user, I see an error when a sketch save fails and can insert the sketch again", async ({
    page,
    pageWorkspace,
    clientFiles,
  }) => {
    const fileName = `${alphaNumericUnique("e2eSketchSaveFailure")}.png`;
    await clientFiles.uploadFile({ name: fileName, mimeType: "image/png", buffer: TINY_PNG });
    await pageWorkspace.open();
    const editor = await pageWorkspace.createBasicDocument();
    const field = await editor.getField("", 0);
    const images = field.galleryImages;
    for (const count of [1, 2, 3, 4, 5, 6]) {
      const picker = await editor.openGalleryPicker();
      await picker.goToSection("Images");
      await picker.openFolder("Api Inbox");
      await picker.selectItem(fileName);
      await picker.add();
      await expect(images).toHaveCount(count);
      await field.moveCaretToNewLineAtEnd();
    }
    const saveRoute = "**/image/ajax/saveImageAnnotation";
    let saveRequests = 0;
    const savedIds: string[] = [];
    page.on("request", (request) => {
      if (request.url().includes("/image/ajax/saveImageAnnotation")) saveRequests++;
    });
    page.on("response", (response) => {
      if (!response.url().includes("/image/ajax/saveImageAnnotation")) return;
      void response
        .json()
        .then((json: { data?: { id?: number } }) => {
          if (json.data?.id) savedIds.push(String(json.data.id));
        })
        .catch(() => {});
    });

    // An HTML page instead of JSON, as when the container redirects an oversized request.
    let sketch = await field.openSketch(images.nth(0));
    await sketch.drawStroke(0.15);
    await page.route(saveRoute, (route) =>
      route.fulfill({ status: 200, contentType: "text/html", body: "<html><body>Workspace</body></html>" }),
    );
    expect(await sketch.insertExpectingFailure()).toMatch(/failed/i);
    await expect(sketch.dialog).toBeVisible();
    await page.unroute(saveRoute);
    let requestsBefore = saveRequests;
    await sketch.insert();
    expect(saveRequests, "The retry saves once").toBe(requestsBefore + 1);
    await expect(images.nth(0)).toHaveAttribute("data-id", /^\d+$/);

    // The drawing can't be exported, so nothing is sent.
    for (const [index, format] of [
      [1, "png"],
      [2, "zwibbler3"],
    ] as const) {
      sketch = await field.openSketch(images.nth(index));
      await sketch.drawStroke(0.35);
      const strokes = await sketch.strokes();
      await sketch.failNextExport(format);
      requestsBefore = saveRequests;
      expect(await sketch.insertExpectingFailure()).toMatch(/failed/i);
      await expect(sketch.dialog).toBeVisible();
      expect(saveRequests, `No save is sent when the ${format} export fails`).toBe(requestsBefore);
      await sketch.waitForReady();
      expect(await sketch.strokes(), "The drawing is kept for the retry").toEqual(strokes);
      await sketch.insert();
      expect(saveRequests, "The retry saves once").toBe(requestsBefore + 1);
      await expect(images.nth(index)).toHaveAttribute("data-id", /^\d+$/);
    }

    // The sketch is saved but can't be inserted, so the retry inserts it without saving again.
    sketch = await field.openSketch(images.nth(3));
    await sketch.drawStroke(0.55);
    await sketch.failNextEditorInsertion();
    requestsBefore = saveRequests;
    expect(await sketch.insertExpectingFailure()).toMatch(/failed/i);
    await expect(sketch.dialog).toBeVisible();
    await sketch.insertSaved();
    expect(saveRequests, "The sketch is saved once").toBe(requestsBefore + 1);
    await expect(images, "The sketch is inserted once, replacing the raw copy").toHaveCount(6);
    await expect(images.nth(3)).toHaveAttribute("data-id", /^\d+$/);

    // A stroke added after the failed insertion updates the saved annotation instead of adding one.
    sketch = await field.openSketch(images.nth(4));
    await sketch.drawStroke(0.15);
    await sketch.failNextEditorInsertion();
    requestsBefore = saveRequests;
    const idsBefore = savedIds.length;
    expect(await sketch.insertExpectingFailure()).toMatch(/failed/i);
    await expect.poll(() => savedIds.length).toBe(idsBefore + 1);
    const savedId = savedIds[idsBefore];
    await sketch.drawStroke(0.55);
    await sketch.insert();
    expect(saveRequests, "The changed drawing is saved again").toBe(requestsBefore + 2);
    await expect(images).toHaveCount(6);
    await expect(images.nth(4), "The first save's annotation is updated and inserted").toHaveAttribute(
      "data-id",
      savedId,
    );
    const ids = await images.evaluateAll((nodes) => nodes.map((node) => node.getAttribute("data-id")));
    expect(ids.filter((id) => id === savedId)).toHaveLength(1);
    const stored = await page.request.get(`/image/ajax/loadSketchImageAnnotations?sketchId=${savedId}`);
    const drawing = ((await stored.json()) as { data: string }).data;
    const nodes = JSON.parse(drawing.slice("zwibbler3.".length)) as Array<{ type: string }>;
    expect(
      nodes.filter((node) => /Brush|Path/.test(node.type)),
      "The stored drawing has both strokes",
    ).toHaveLength(2);

    // An empty link template is rejected before anything is saved.
    const templateRoute = "**/fieldTemplates/ajax/annotatedImageLink";
    sketch = await field.openSketch(images.nth(5));
    await sketch.drawStroke(0.35);
    await page.route(templateRoute, (route) => route.fulfill({ status: 200, contentType: "text/html", body: "" }));
    requestsBefore = saveRequests;
    expect(await sketch.insertExpectingFailure()).toMatch(/failed/i);
    expect(saveRequests, "No save is sent with an empty template").toBe(requestsBefore);
    await expect(sketch.dialog).toBeVisible();
    await page.unroute(templateRoute);
    await sketch.insert();
    expect(saveRequests, "Only the retry saves").toBe(requestsBefore + 1);
    await expect(images.nth(5)).toHaveAttribute("data-id", /^\d+$/);
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
        if (!path) {
          throw new Error("The completed export download has no local file.");
        }
        const exportedDocuments = await readArchiveDocuments(path, format);
        expect(
          exportedDocuments.some((entry) => entry.includes(name) && entry.includes(content)),
          "Export contains the selected document's name and saved field contents",
        ).toBe(true);
      },
    );
  }
});
