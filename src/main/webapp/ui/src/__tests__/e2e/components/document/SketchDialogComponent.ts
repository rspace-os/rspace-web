import { expect, type FrameLocator, type Locator, type Page } from "@playwright/test";

type ZwibblerWindow = Window & { context?: { findNode(id: string): unknown; save(format: string): string } };
type EditorWindow = { tinymce: { activeEditor: { execCommand(command: string, ...args: unknown[]): unknown } } };

/** The legacy TinyMCE Sketch dialog, opened on an image to annotate it. */
export class SketchDialogComponent {
  readonly dialog: Locator;
  private readonly frame: FrameLocator;
  private readonly drawingArea: Locator;

  constructor(private readonly page: Page) {
    this.dialog = page.getByRole("dialog", { name: "Sketch", exact: true });
    this.frame = this.dialog.frameLocator("iframe");
    // Zwibbler draws into a legacy div with no accessible role.
    this.drawingArea = this.frame.locator("#image-editorSketch");
  }

  async waitForReady(): Promise<this> {
    await this.drawingArea.locator("canvas").first().waitFor({ state: "visible" });
    await expect
      .poll(() => this.drawingArea.evaluate(() => Boolean((window as ZwibblerWindow).context?.findNode("bgImage"))))
      .toBe(true);
    return this;
  }

  /** The drawn strokes, serialised so two drawings can be compared. */
  async strokes(): Promise<string[]> {
    return this.drawingArea.evaluate(() => {
      const drawing = (window as ZwibblerWindow).context?.save("zwibbler3") ?? "zwibbler3.[]";
      const nodes = JSON.parse(drawing.slice("zwibbler3.".length)) as Array<{ type: string }>;
      return nodes.filter((node) => /Brush|Path/.test(node.type)).map((node) => JSON.stringify(node));
    });
  }

  async strokeCount(): Promise<number> {
    return (await this.strokes()).length;
  }

  /** Draws one brush stroke starting at the given fraction of the drawing area's width. */
  async drawStroke(xFraction: number): Promise<void> {
    await this.frame.getByTitle("Small brush").first().click();
    const box = await this.drawingArea.locator("canvas").first().boundingBox();
    if (!box) throw new Error("The sketch canvas has no bounding box");
    const startX = box.x + box.width * xFraction;
    const startY = box.y + box.height * 0.3;
    const strokesBefore = await this.strokeCount();
    await this.page.mouse.move(startX, startY);
    await this.page.mouse.down();
    await this.page.mouse.move(startX + 80, startY + 50, { steps: 10 });
    await this.page.mouse.move(startX + 110, startY, { steps: 10 });
    await this.page.mouse.up();
    await expect.poll(() => this.strokeCount()).toBe(strokesBefore + 1);
  }

  async cancel(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.page.getByRole("button", { name: "Yes, cancel", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }

  /** Makes the next export of the drawing in this format throw, as a Zwibbler failure would. */
  async failNextExport(format: "png" | "zwibbler3"): Promise<void> {
    await this.drawingArea.evaluate((_, failingFormat) => {
      const context = (window as ZwibblerWindow).context;
      if (!context) throw new Error("Zwibbler is not ready");
      const save = context.save.bind(context);
      let failed = false;
      context.save = (format: string) => {
        if (format === failingFormat && !failed) {
          failed = true;
          throw new Error(`Simulated ${format} export failure`);
        }
        return save(format);
      };
    }, format);
  }

  /** Makes the next insertion into the document editor throw, after the sketch is saved. */
  async failNextEditorInsertion(): Promise<void> {
    await this.drawingArea.evaluate(() => {
      const editor = (window.parent as unknown as EditorWindow).tinymce.activeEditor;
      const execCommand = editor.execCommand.bind(editor);
      let failed = false;
      editor.execCommand = (command: string, ...args: unknown[]) => {
        if (command === "mceInsertContent" && !failed) {
          failed = true;
          throw new Error("Simulated editor insertion failure");
        }
        return execCommand(command, ...args);
      };
    });
  }

  /** Clicks Insert when the save should fail, and returns the error alert's message. */
  async insertExpectingFailure(): Promise<string> {
    // Accept at once: an alert raised during the click blocks the click until it is handled.
    const alertMessage = this.page
      .waitForEvent("dialog", { timeout: 15_000 })
      .catch(() => {
        throw new Error("Insert didn't show the save error alert");
      })
      .then(async (alert) => {
        const message = alert.message();
        await alert.accept();
        return message;
      });
    const [message] = await Promise.all([
      alertMessage,
      this.dialog.getByRole("button", { name: "Insert", exact: true }).click(),
    ]);
    return message;
  }

  /** Clicks Insert for a sketch that was already saved, so no save request is expected. */
  async insertSaved(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Insert", exact: true }).click();
    await this.dialog.waitFor({ state: "hidden" });
  }

  async insert(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/image/ajax/saveImageAnnotation")),
      this.dialog.getByRole("button", { name: "Insert", exact: true }).click(),
    ]);
    expect(response.ok(), "The annotation was saved").toBe(true);
    await this.dialog.waitFor({ state: "hidden" });
  }
}
