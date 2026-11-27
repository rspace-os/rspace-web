import { waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { getGenericDocTemplate } from "@/tinyMCE/gallery/utils";
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

  describe("attachment panels copied from view mode", () => {
    const records: Record<string, { id: number; name: string; type: string; extension: string }> = {
      "192": { id: 192, name: "sample.pdf", type: "Document", extension: "pdf" },
      "193": { id: 193, name: "notes.txt", type: "Miscellaneous", extension: "txt" },
    };
    let lookups: Record<string, JQuery.Deferred<unknown>>;

    // view-mode panel as Chromium copies it, with an absolute Download URL
    const copiedPanel = (name: string, downloadHref: string) =>
      '<div class="attachmentDiv mceNonEditable"><div class="attachmentPanel previewableAttachmentPanel">' +
      `<div class="attachmentThumbnailPanel"><img class="attachmentIcon"><label class="attachmentName">${name}</label></div>` +
      '<div class="inlineActionsPanel"><a href="#" class="inlineActionLink viewActionLink">View</a>' +
      `<a href="${downloadHref}" class="inlineActionLink downloadActionLink">Download</a>` +
      '<a href="#" class="inlineActionLink infoActionLink">Info</a></div></div></div>';
    const download = (path: string) => `${window.location.origin}${path}`;

    const lookupUrl = (id: string, revision?: string) =>
      `/workspace/getRecordInformation?recordId=${id}${revision ? `&revision=${revision}` : ""}`;
    const respond = (id: string, revision?: string) => lookups[lookupUrl(id, revision)].resolve({ data: records[id] });

    async function insertedContent() {
      await waitFor(() => expect(harness.execCommand).toHaveBeenCalledTimes(1));
      expect(harness.execCommand).toHaveBeenCalledWith("mceInsertContent", false, expect.any(String));
      const container = document.createElement("div");
      container.innerHTML = harness.execCommand.mock.calls[0][2] as string;
      document.body.append(container);
      return container;
    }

    beforeEach(() => {
      lookups = {};
      harness.$.get.mockImplementation((url: string) => {
        lookups[url] = harness.$.Deferred();
        return lookups[url].promise();
      });
      harness.RS.renderFieldTemplate = vi.fn((_templateId: string, model: { id: number; name: string }) =>
        harness.$.Deferred()
          .resolve(
            `<div class="attachmentDiv mceNonEditable"><a href="/Streamfile/${model.id}"><img class="attachmentIcon"></a>` +
              `<p class="attachmentP"><a class="attachmentLinked" id="attachOnText_${model.id}" href="/Streamfile/${model.id}">${model.name}</a></p>` +
              `<div class="attachmentInfoDiv" id="attachmentInfoDiv_${model.id}"></div></div>`,
          )
          .promise(),
      );
      window.getGenericDocTemplate = getGenericDocTemplate;
      Object.defineProperty(globalThis, "generateIconSrc", {
        configurable: true,
        writable: true,
        value: vi.fn(() => "/images/icons/pdf.png"),
      });
    });

    it("claims the paste and inserts the attachment as editor markup in its original position", async () => {
      const html = `before ${copiedPanel("sample.pdf", download("/Streamfile/192"))} after`;

      expect(pasteHandler.processPastedContent("before sample.pdf View Download Info after", html)).toBe(true);
      expect(harness.$.get).toHaveBeenCalledWith(lookupUrl("192"));
      expect(harness.blockPage).toHaveBeenCalledWith("Copying elements in pasted content...");

      respond("192");
      const inserted = await insertedContent();

      expect(inserted.querySelector(".attachmentPanel")).toBeNull();
      expect(inserted.querySelectorAll(".attachmentDiv")).toHaveLength(1);
      const link = inserted.querySelector("a.attachmentLinked");
      expect(link).toHaveAttribute("id", "attachOnText_192");
      expect(link).toHaveAttribute("href", "/Streamfile/192");
      expect(link).toHaveTextContent("sample.pdf");
      expect(inserted.querySelector("#attachmentInfoDiv_192")).toBeInTheDocument();
      expect(inserted.firstChild?.textContent).toBe("before ");
      expect(inserted.lastChild?.textContent).toBe(" after");
      expect(harness.unblockPage).toHaveBeenCalled();
    });

    it("keeps panels in their pasted order when lookups finish in reverse order", async () => {
      const html =
        copiedPanel("sample.pdf", download("/Streamfile/192")) + copiedPanel("notes.txt", download("/Streamfile/193"));

      expect(pasteHandler.processPastedContent("", html)).toBe(true);
      respond("193");
      respond("192");
      const inserted = await insertedContent();

      expect(Array.from(inserted.querySelectorAll("a.attachmentLinked"), (a) => a.id)).toEqual([
        "attachOnText_192",
        "attachOnText_193",
      ]);
    });

    it("keeps a pinned revision on the restored attachment", async () => {
      const html = copiedPanel("sample.pdf", download("/Streamfile/192?revision=7"));

      expect(pasteHandler.processPastedContent("", html)).toBe(true);
      expect(harness.$.get).toHaveBeenCalledWith(lookupUrl("192", "7"));
      respond("192", "7");
      const inserted = await insertedContent();

      expect(inserted.querySelector("a.attachmentLinked")).toHaveAttribute("data-rsrevision", "7");
      expect(Array.from(inserted.querySelectorAll("[href]"), (a) => a.getAttribute("href"))).toEqual([
        "/Streamfile/192?revision=7",
        "/Streamfile/192?revision=7",
      ]);
    });

    it("keeps the label as plain text when the lookup fails", async () => {
      const html = `before ${copiedPanel("sample.pdf", download("/Streamfile/192"))} after`;

      const restoreConsole = silenceConsole(["warn"], ["Could not resolve pasted attachment"]);

      expect(pasteHandler.processPastedContent("", html)).toBe(true);
      lookups[lookupUrl("192")].reject({ status: 403 });
      const inserted = await insertedContent();

      expect(inserted.innerHTML).toBe("before sample.pdf after");
      restoreConsole();
    });

    it("keeps panels from another origin or without an origin as plain text, without a lookup", async () => {
      const html =
        copiedPanel("foreign.pdf", "http://other.rspace:8080/Streamfile/192") +
        copiedPanel("unknown.pdf", "/Streamfile/192");

      expect(pasteHandler.processPastedContent("", html)).toBe(true);
      const inserted = await insertedContent();

      expect(harness.$.get).not.toHaveBeenCalled();
      expect(inserted.innerHTML).toBe("foreign.pdfunknown.pdf");
    });

    it("inserts nothing if the receiving editor is no longer active", async () => {
      const toastmessage = vi.fn();
      Object.defineProperty(harness.$.fn, "toastmessage", { configurable: true, writable: true, value: toastmessage });
      const html = copiedPanel("sample.pdf", download("/Streamfile/192"));

      expect(pasteHandler.processPastedContent("", html)).toBe(true);
      (window as unknown as { tinymce: { activeEditor: unknown } }).tinymce.activeEditor = {
        ...harness.activeEditor,
        id: "rtf_12",
      };
      respond("192");

      await waitFor(() =>
        expect(toastmessage).toHaveBeenCalledWith("showToast", expect.objectContaining({ type: "error" })),
      );
      expect(harness.execCommand).not.toHaveBeenCalled();
      expect(harness.unblockPage).toHaveBeenCalled();
    });
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
});
