import { waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  bootstrapLegacyEditorHarness,
  createJQueryRequestChain,
  type LegacyEditorHarness,
  loadLegacyEditorScript,
} from "./legacyEditorTestHarness";

describe("tinymceRS_pasteHandler", () => {
  let harness: LegacyEditorHarness;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  let pasteHandler: Record<string, (...args: Array<any>) => any>;
  let currentServerUrl: string;

  beforeEach(() => {
    harness = bootstrapLegacyEditorHarness();
    loadLegacyEditorScript("tinymceRS_pasteHandler.js");
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    pasteHandler = harness.RS.tinymcePasteHandler as Record<string, (...args: Array<any>) => any>;
    currentServerUrl = (harness.RS.createAbsoluteUrl as () => string)();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("initialises on the page", () => {
    expect(pasteHandler).toBeDefined();
  });

  it("does not process arbitrary pasted html", () => {
    const text = "paragraph a, with an img script in itparagraph b";
    const html =
      "<div><p>paragraph a, with an img script in it</p>" +
      '<img onerror="alert(\'xss attempt\')" src="dummy.png"></div>' +
      "<div><p>paragraph b</p></div>";

    expect(pasteHandler.processPastedContent(text, html)).toBe(false);
    expect(harness.execCommand).not.toHaveBeenCalled();
  });

  it.each([
    ["asdf", "<span>asdf</span>"],
    ["John, take a look at SD27.", "<span>John, take a look at SD27.</span>"],
  ])("does not recognise %s as an RSpace link", (text, html) => {
    expect(pasteHandler._isRSpaceLink(text)).toBe(false);
    expect(pasteHandler.processPastedContent(text, html)).toBe(false);
  });

  it("recognises plain text global ids and requests record details", () => {
    const requestedUrls: Array<string> = [];
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (harness.$.get as any).mockImplementation((url: string) => {
      requestedUrls.push(url);
      return {
        fail() {
          return this;
        },
      };
    });

    expect(pasteHandler._isRSpaceLink("GL12345")).toBe(true);
    expect(pasteHandler._isGlobalId("GL12345")).toBe("GL12345");
    expect(pasteHandler.processPastedContent("GL12345", "<span>GL12345</span>")).toBe(true);

    expect(pasteHandler._isRSpaceLink("SD54321")).toBe(true);
    expect(pasteHandler._isGlobalId("SD54321")).toBe("SD54321");
    expect(pasteHandler.processPastedContent("SD54321", "<span>SD54321</span>")).toBe(true);

    expect(requestedUrls).toEqual([
      "/workspace/getRecordInformation?recordId=12345",
      "/workspace/getRecordInformation?recordId=54321",
    ]);
  });

  it("handles same-instance internal document and gallery links", () => {
    const requestedUrls: Array<string> = [];
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (harness.$.get as any).mockImplementation((url: string) => {
      requestedUrls.push(url);
      return {
        fail() {
          return this;
        },
      };
    });

    const documentText = `${currentServerUrl}/globalId/SD27`;
    expect(pasteHandler._isRSpaceLink(documentText)).toBe(true);
    expect(pasteHandler._isGlobalIdUrl(documentText)).toBe(documentText);
    expect(pasteHandler.processPastedContent(documentText, documentText)).toBe(true);

    const galleryHtml = `<a class="infoPanel-objectIdLink" href="${currentServerUrl}/globalId/GL21">GL21</a>`;
    expect(pasteHandler._isRSpaceLink("GL21")).toBe(true);
    expect(pasteHandler._containsGlobalIdHref(harness.$(galleryHtml))).toBe(`${currentServerUrl}/globalId/GL21`);
    expect(pasteHandler.processPastedContent("GL21", galleryHtml)).toBe(true);

    expect(requestedUrls).toEqual([
      "/workspace/getRecordInformation?recordId=27",
      "/workspace/getRecordInformation?recordId=21",
    ]);
  });

  it("inserts a simple anchor when pasting a link to another RSpace instance", () => {
    const otherInstanceUrl = "http://other.rspace:8080/globalId/GL27";

    expect(pasteHandler._isRSpaceLink(otherInstanceUrl)).toBe(true);
    expect(pasteHandler._isGlobalIdUrl(otherInstanceUrl)).toBe(otherInstanceUrl);
    expect(pasteHandler.processPastedContent(otherInstanceUrl, otherInstanceUrl)).toBe(true);

    expect(harness.$.get).not.toHaveBeenCalled();
    expect(harness.execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      '<a href="http://other.rspace:8080/globalId/GL27">GL27</a>',
    );
  });

  it("requests the right record version for versioned links", () => {
    const requestedUrls: Array<string> = [];
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (harness.$.get as any).mockImplementation((url: string) => {
      requestedUrls.push(url);
      return {
        fail() {
          return this;
        },
      };
    });

    const versionedDocumentUrl = `${currentServerUrl}/globalId/SD27v2`;
    const versionedExternalUrl = "http://other.rspace:8080/globalId/SD28v3";

    expect(pasteHandler._isGlobalIdUrl(versionedDocumentUrl)).toBe(versionedDocumentUrl);
    expect(pasteHandler.processPastedContent(versionedDocumentUrl, versionedDocumentUrl)).toBe(true);

    expect(pasteHandler._isRSpaceLink("SD28v3")).toBe(true);
    expect(pasteHandler.processPastedContent("SD28v3", `<a href="${versionedExternalUrl}">SD28v3</a>`)).toBe(true);

    expect(requestedUrls).toEqual(["/workspace/getRecordInformation?recordId=27&version=2"]);
    expect(harness.execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      `<a href="${versionedExternalUrl}">SD28v3</a>`,
    );
  });

  it("inserts internal links and gallery items after resolving record details", () => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (harness.$.get as any).mockImplementation(
      (url: string, success: (result: { data: { id: number; name: string } }) => void) => {
        if (url.includes("recordId=27")) {
          success({ data: { id: 27, name: "Notebook entry" } });
        } else if (url.includes("recordId=21")) {
          success({ data: { id: 21, name: "Gallery image" } });
        }
        return {
          fail() {
            return this;
          },
        };
      },
    );

    expect(pasteHandler.processPastedContent("SD27", "<span>SD27</span>")).toBe(true);
    expect(pasteHandler.processPastedContent("GL21", "<span>GL21</span>")).toBe(true);

    expect(harness.RS.tinymceInsertInternalLink).toHaveBeenCalledWith(27, "SD27", "Notebook entry", expect.any(Object));
    expect(harness.addFromGallery).toHaveBeenCalledWith({
      id: 21,
      name: "Gallery image",
    });
  });

  it("filters notebook wrappers and table download chrome from pasted html", () => {
    const html =
      '<button id="prevEntryButton_mobile" class="bootstrap-custom-flat"></button>' +
      '<div id="journalPagePaddingId" class="journalPagePadding">' +
      '<div class="journalPageContent"><p>text</p>' +
      '<div class="tableDownloadWrap"><table border="1"><tbody><tr><td>a</td></tr></tbody></table>' +
      '<div class="tableContextButtons tableDownloadButton"></div></div></div></div>';

    const $html = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(html);
    expect(pasteHandler._containsRSpaceViewModeClasses($html)).toBe(true);

    expect(pasteHandler.processPastedContent("text", html)).toBe(true);
    expect(harness.execCommand).toHaveBeenCalledWith(
      "mceInsertContent",
      false,
      '<p>text</p><table border="1"><tbody><tr><td>a</td></tr></tbody></table>',
    );
  });

  it("unwraps pasted image view mode panels down to the image element", () => {
    const html =
      '<span>before&nbsp;</span><div class="imageViewModePanel"><div class="imagePanel">' +
      '<img id="65569-1995" class="imageDropped inlineImageThumbnail" src="http://localhost:8080/thumbnail/data?sourceType=IMAGE&amp;sourceId=1995" alt="image test2.png"></div>' +
      '<div class="imageData"><span class="imageFileName">test2.png</span></div></div><span>after</span>';

    const $html = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(html);
    const strippedHtml = (harness.RS.convert$HtmlToHtmlString as ($html: JQuery) => string)(
      pasteHandler._getHtmlWithoutRSpaceViewModeClasses($html),
    );

    expect(strippedHtml).toBe(
      '<span>before&nbsp;</span><img id="65569-1995" class="imageDropped inlineImageThumbnail" src="http://localhost:8080/thumbnail/data?sourceType=IMAGE&amp;sourceId=1995" alt="image test2.png"><span>after</span>',
    );
  });

  it("recognises copied RSpace field elements", () => {
    const chem = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      '<p><img id="425984" class="chem" src="/thumbnail/data?sourceType=CHEM"></p>',
    );
    const sketch = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      'test<img id="327680" class="sketch" src="/image/getImageSketch/327680/1575812783054">',
    );
    const annotation = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      '<img id="557056-15541" class="imageDropped" src="/image/getAnnotation/393216/1575882780885">',
    );
    const equation = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      '<div class="rsEquation mceNonEditable" data-mathid="32768"></div>',
    );
    const comment = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      '<img id="10" class="commentIcon" src="/images/commentIcon.gif">',
    );
    const plain = (harness.RS.safelyParseHtmlInto$Html as (html: string) => JQuery)(
      "<span>John, take a look at SD27.</span>",
    );

    expect(pasteHandler._containsRSpaceFieldElements(chem)).toBe(true);
    expect(pasteHandler._containsRSpaceFieldElements(sketch)).toBe(true);
    expect(pasteHandler._containsRSpaceFieldElements(annotation)).toBe(true);
    expect(pasteHandler._containsRSpaceFieldElements(equation)).toBe(true);
    expect(pasteHandler._containsRSpaceFieldElements(comment)).toBe(true);
    expect(pasteHandler._containsRSpaceFieldElements(plain)).toBe(false);
  });

  it("copies pasted RSpace elements through the server endpoint", () => {
    const request = createJQueryRequestChain();
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (harness.$.post as any).mockImplementation((url: string, payload: unknown) => {
      expect(url).toBe("/workspace/editor/structuredDocument/copyContentIntoField");
      expect(payload).toEqual({
        content: '<p><img id="360450" class="chem" src="/thumbnail/data?sourceType=CHEM&amp;sourceId=360450"></p>',
        fieldId: "11",
      });
      return request.chain;
    });

    expect(
      pasteHandler.processPastedContent(
        "We have perfor",
        '<p><img id="360450" class="chem" src="/thumbnail/data?sourceType=CHEM&amp;sourceId=360450"></p>',
      ),
    ).toBe(true);

    expect(harness.blockPage).toHaveBeenCalledWith("Copying elements in pasted content...");

    request.resolve("mocked_copy_response");

    expect(harness.unblockPage).toHaveBeenCalled();
    expect(harness.execCommand).toHaveBeenCalledWith("mceInsertContent", false, "mocked_copy_response");
  });

  it("processes pasted image data by forwarding a generated file to the upload widget", async () => {
    const blob = new Blob(["png"], { type: "image/png" });
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue({ blob: vi.fn().mockResolvedValue(blob) } as any);

    expect(pasteHandler.processPastedContent("", '<img src="data:image/png;base64," alt="">')).toBe(true);

    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledWith("data:image/png;base64,");
      expect(harness.fileUpload).toHaveBeenCalledWith(
        "add",
        expect.objectContaining({
          files: [expect.any(File)],
        }),
      );
    });
  });

  describe("links to this RSpace server (RSDEV-614)", () => {
    const origin = window.location.origin;

    function stubEditor() {
      return {
        settings: { convert_urls: true, relative_urls: false, remove_script_host: true },
        documentBaseURI: {
          toAbsolute: vi.fn((url: string) => `default:${url}`),
          toRelative: vi.fn((url: string) => `relative:${url}`),
        },
      };
    }

    function convert(editor: ReturnType<typeof stubEditor>, url: string, node: unknown, name: string): string {
      return pasteHandler.convertEditorUrl.call(editor, url, node, true, name) as string;
    }

    it.each([
      ["a tag name from the parser", "a"],
      ["an element from DOMUtils", document.createElement("a")],
    ])("keeps an absolute same-origin href unchanged when node is %s", (_, node) => {
      const editor = stubEditor();
      const url = `${origin}/inventory/sample/1?x=1#y`;

      expect(convert(editor, url, node, "href")).toBe(url);
      expect(editor.documentBaseURI.toAbsolute).not.toHaveBeenCalled();
    });

    it.each([
      `${origin}/globalId/SD1`,
      `${origin}/globalId/SD1?version=2#top`,
      `${origin}/Streamfile/5`,
      `${origin}/Streamfile/5/file.txt?x=1`,
      `${origin}/public/publicView/Streamfile/5`,
      "/inventory/search",
      "https://other.example/inventory/sample/1",
    ])("uses the default conversion for href %s", (url) => {
      const editor = stubEditor();

      expect(convert(editor, url, "a", "href")).toBe(`default:${url}`);
      expect(editor.documentBaseURI.toAbsolute).toHaveBeenCalledWith(url, true);
    });

    it("uses the default conversion for a same-origin src", () => {
      const editor = stubEditor();
      const url = `${origin}/thumbnail/data?sourceId=1`;

      expect(convert(editor, url, document.createElement("img"), "src")).toBe(`default:${url}`);
      expect(editor.documentBaseURI.toAbsolute).toHaveBeenCalledWith(url, true);
    });

    it.each([
      ["an empty url", "", "a"],
      ["a file: url", "file:///tmp/x.html", "a"],
      ["a LINK element", "/styles/x.css", document.createElement("link")],
    ])("returns %s unchanged without conversion", (_, url, node) => {
      const editor = stubEditor();

      expect(convert(editor, url, node, "href")).toBe(url);
      expect(editor.documentBaseURI.toAbsolute).not.toHaveBeenCalled();
    });

    it("returns urls unchanged when convert_urls is off", () => {
      const editor = stubEditor();
      editor.settings.convert_urls = false;

      expect(convert(editor, "/inventory/search", "a", "href")).toBe("/inventory/search");
      expect(editor.documentBaseURI.toAbsolute).not.toHaveBeenCalled();
    });

    function pastedNode(html: string): HTMLDivElement {
      const node = document.createElement("div");
      node.innerHTML = html;
      return node;
    }

    it("makes a pasted root-relative href absolute, keeping query and fragment", () => {
      const node = pastedNode('<p><a href="/inventory/search?query=RSDEV614#r">rel</a></p>');

      pasteHandler.absolutizePastedLinks(node);

      expect(node.querySelector("a")).toHaveAttribute("href", `${origin}/inventory/search?query=RSDEV614#r`);
    });

    it("leaves a pasted absolute same-origin href unchanged", () => {
      const href = `${origin}/inventory/sample/1?x=1`;
      const node = pastedNode(`<a href="${href}">abs</a>`);

      pasteHandler.absolutizePastedLinks(node);

      expect(node.querySelector("a")).toHaveAttribute("href", href);
    });

    it.each([
      ["linkedRecord", "/workspace/editor/structuredDocument/1?v=2#top"],
      ["attachmentLinked", "/gallery/item/5"],
    ])("makes a pasted absolute same-origin %s href root-relative", (cssClass, path) => {
      const node = pastedNode(`<a class="${cssClass}" href="${origin}${path}">x</a>`);

      pasteHandler.absolutizePastedLinks(node);

      expect(node.querySelector("a")).toHaveAttribute("href", path);
    });

    it("keeps a pasted absolute same-origin RSpace element href whose path starts with //", () => {
      const href = `${origin}//other.example/x`;
      const node = pastedNode(`<a class="linkedRecord" href="${href}">x</a>`);

      pasteHandler.absolutizePastedLinks(node);

      const anchor = node.querySelector("a");
      expect(anchor).toHaveAttribute("href", href);
      expect(new URL(anchor?.getAttribute("href") ?? "", origin).host).toBe(new URL(origin).host);
    });

    it.each([
      '<a href="/globalId/SD1">x</a>',
      '<a href="/globalId/SD1?v=1#x">x</a>',
      '<a href="/Streamfile/5">x</a>',
      '<a href="/public/publicView/Streamfile/5">x</a>',
      '<a class="linkedRecord" href="/workspace/editor/structuredDocument/1">x</a>',
      '<a class="attachmentLinked" href="/gallery/item/5">x</a>',
      '<a href="//cdn.example/x">x</a>',
      '<a href="#frag">x</a>',
      '<a href="mailto:someone@example.com">x</a>',
      '<a href="https://other.example/inventory">x</a>',
      '<a href="foo.html">x</a>',
    ])("leaves the pasted link %s unchanged", (html) => {
      const node = pastedNode(html);
      const before = node.querySelector("a")?.getAttribute("href");

      pasteHandler.absolutizePastedLinks(node);

      expect(node.querySelector("a")).toHaveAttribute("href", before);
    });
  });
});
