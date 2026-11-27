import type { FrameLocator, Locator, Page } from "@playwright/test";

export type SketchImageNode = { id: number; url: string; x: number; y: number; scaleX: number };

type Zwibbler = {
  save(format: string): string;
  undo(): void;
  beginTransaction(): void;
  createNode(type: string, properties: Record<string, unknown>): number;
  translateNode(id: number, x: number, y: number): void;
  commitTransaction(): void;
  getScreenCoordinates(x: number, y: number): { x: number; y: number };
};

/** Image nodes showing url in a drawing saved in Zwibbler's "zwibbler3" format, with their top-left position. */
export function imageNodesIn(saved: string, url: string): SketchImageNode[] {
  const nodes = JSON.parse(saved.slice(saved.indexOf("["))) as {
    id: number;
    type: string;
    url?: string;
    matrix?: number[];
  }[];
  return nodes
    .filter((node) => node.type === "ImageNode" && node.url === url && Array.isArray(node.matrix))
    .map((node) => ({
      id: node.id,
      url,
      x: node.matrix?.[4] ?? 0,
      y: node.matrix?.[5] ?? 0,
      scaleX: node.matrix?.[0] ?? 1,
    }));
}

/** The legacy Zwibbler Sketch tool, a TinyMCE URL dialog hosting `sketch/iframe.html`. */
export class SketchDialogComponent {
  readonly root: Locator;
  readonly frame: FrameLocator;
  readonly insertButton: Locator;
  private readonly iframe: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Sketch" });
    this.iframe = this.root.locator("iframe");
    this.frame = this.iframe.contentFrame();
    this.insertButton = this.root.getByRole("button", { name: "Insert" });
  }

  async waitUntilReady(): Promise<void> {
    // The dialog's init creates the drawing canvas, the only canvas Zwibbler makes keyboard focusable.
    await this.frame.locator("canvas[tabindex='0']").waitFor({ state: "visible" });
  }

  /** The tab radio inputs are hidden; their labelled icons are what users click. */
  async openGalleryTab(): Promise<void> {
    await this.frame.getByRole("img", { name: "Root Image Gallery" }).click();
    await this.galleryPicker.waitFor({ state: "visible" });
  }

  async openTemplatesTab(): Promise<void> {
    await this.frame.getByRole("img", { name: "Templates" }).click();
    await this.galleryPicker.waitFor({ state: "hidden" });
  }

  get galleryPicker(): Locator {
    // jQuery-built picker with no role or label; the legacy id is its only stable hook.
    return this.frame.locator("#gallery-editorSketch");
  }

  get galleryTiles(): Locator {
    return this.galleryPicker.locator("img");
  }

  // Zwibbler draws to a canvas with no DOM to query, so these use the iframe's Zwibbler context.

  async imageNodes(url: string): Promise<SketchImageNode[]> {
    const saved = await this.iframe.evaluate((iframe: HTMLIFrameElement) =>
      (iframe.contentWindow as unknown as { context: Zwibbler }).context.save("zwibbler3"),
    );
    return imageNodesIn(saved, url);
  }

  async undo(): Promise<void> {
    await this.iframe.evaluate((iframe: HTMLIFrameElement) => {
      (iframe.contentWindow as unknown as { context: Zwibbler }).context.undo();
    });
  }

  /** Adds an image node with its top-left at (x, y), as a drawing tool would; returns its id. */
  async addImageNode(url: string, x: number, y: number): Promise<number> {
    return this.iframe.evaluate(
      (iframe: HTMLIFrameElement, node) => {
        const { context } = iframe.contentWindow as unknown as { context: Zwibbler };
        context.beginTransaction();
        const id = context.createNode("ImageNode", { url: node.url });
        context.translateNode(id, node.x, node.y);
        context.commitTransaction();
        return id;
      },
      { url, x, y },
    );
  }

  /** Where the drawing's point (x, y) is on the page, for mouse actions. */
  async pagePoint(x: number, y: number): Promise<{ x: number; y: number }> {
    const box = await this.iframe.boundingBox();
    const inFrame = await this.iframe.evaluate(
      (iframe: HTMLIFrameElement, point) =>
        (iframe.contentWindow as unknown as { context: Zwibbler }).context.getScreenCoordinates(point.x, point.y),
      { x, y },
    );
    return { x: (box?.x ?? 0) + inFrame.x, y: (box?.y ?? 0) + inFrame.y };
  }

  /** Drags with the mouse from the drawing's point from to its point to. */
  async drag(from: { x: number; y: number }, to: { x: number; y: number }): Promise<void> {
    const page = this.root.page();
    const start = await this.pagePoint(from.x, from.y);
    const end = await this.pagePoint(to.x, to.y);
    await page.mouse.move(start.x, start.y);
    await page.mouse.down();
    await page.mouse.move(end.x, end.y, { steps: 5 });
    await page.mouse.up();
  }
}
