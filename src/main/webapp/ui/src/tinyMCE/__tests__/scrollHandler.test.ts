import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  bootstrapLegacyEditorHarness,
  loadLegacyEditorScript,
  type LegacyEditorHarness,
} from "./legacyEditorTestHarness";

describe("tinymceRS_scrollHandler", () => {
  let harness: LegacyEditorHarness;
  let scrollHandler: Record<string, (...args: Array<any>) => any>;

  const paragraphFragment =
    '<p>test paragraph with <strong>inline tags</strong></p>';
  const tableFragment =
    '<div class="tableDownloadWrap" style="display:flex;">' +
    '<table style="border-collapse: collapse; width: 100%;" border="1" width="300" cellpadding="5">' +
    '<tbody><tr><td style="width: 50%;"><strong>rspace table</strong></td>' +
    '<td style="width: 50%;">&nbsp;</td></tr></tbody>' +
    '</table><div class="tableContextButtons tableDownloadButton" title="Download as CSV"></div></div>';
  const attachmentFragment =
    '<div class="attachmentDiv mceNonEditable">' +
    '<div class="attachmentPanel previewableAttachmentPanel">' +
    '<div class="inlineActionsPanel"><a href="#" class="inlineActionLink viewActionLink">View</a></div>' +
    '</div></div>';

  beforeEach(() => {
    harness = bootstrapLegacyEditorHarness();
    vi.spyOn(console, "log").mockImplementation(() => undefined);
    loadLegacyEditorScript("tinymceRS_scrollHandler.js");
    scrollHandler = harness.RS.tinymceScrollHandler as Record<
      string,
      (...args: Array<any>) => any
    >;
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function buildViewModeHtml() {
    return harness.$('<div id="div_rtf_131150" class="isResizable textFieldViewModeDiv">')
      .append(
        paragraphFragment +
          tableFragment +
          paragraphFragment +
          attachmentFragment +
          paragraphFragment +
          tableFragment +
          paragraphFragment +
          attachmentFragment +
          paragraphFragment,
      );
  }

  it("initialises on the page", () => {
    expect(scrollHandler).toBeDefined();
  });

  it("finds the top-level view mode element for paragraph, table and attachment selections", () => {
    const $viewModeHtml = buildViewModeHtml();

    const $secondParagraph = $viewModeHtml.children("p:eq(1)");
    expect(scrollHandler._findTopLevelViewModeElem($secondParagraph)).toEqual(
      $secondParagraph,
    );
    expect(
      scrollHandler
        ._findTopLevelViewModeElem($secondParagraph.children("strong:eq(0)"))
        .is($secondParagraph),
    ).toBe(true);

    const $secondAttachment = $viewModeHtml.children(".attachmentDiv:eq(1)");
    expect(
      scrollHandler
        ._findTopLevelViewModeElem($secondAttachment.find(".viewActionLink:eq(0)"))
        .is($secondAttachment),
    ).toBe(true);

    const $secondTable = $viewModeHtml.children(".tableDownloadWrap:eq(1)");
    expect(
      scrollHandler
        ._findTopLevelViewModeElem($secondTable.find("strong:eq(0)"))
        .is($secondTable),
    ).toBe(true);

    expect(scrollHandler._findTopLevelViewModeElem($viewModeHtml)).toBeNull();
  });

  it("maps top-level view mode elements back to edit mode selectors", () => {
    const $viewModeHtml = buildViewModeHtml();

    expect(
      scrollHandler._getEditModeSelectorForTopLevelElem(
        $viewModeHtml.children("p:eq(1)"),
      ),
    ).toBe("p:eq(1)");
    expect(
      scrollHandler._getEditModeSelectorForTopLevelElem(
        $viewModeHtml.children(".attachmentDiv:eq(1)"),
      ),
    ).toBe("div.mceNonEditable:eq(1)");
    expect(
      scrollHandler._getEditModeSelectorForTopLevelElem(
        $viewModeHtml.children(".tableDownloadWrap:eq(1)"),
      ),
    ).toBe("table:eq(1)");
  });

  it("returns a callback that scrolls the TinyMCE iframe to the matching edit-mode element", () => {
    const $viewModeHtml = buildViewModeHtml();
    const $target = $viewModeHtml.children("p:eq(1)").find("strong:eq(0)");

    harness.editorBody.innerHTML = "<p>first paragraph</p><p>second paragraph</p>";
    document.body.innerHTML =
      '<div id="field_131150"><div class="tox-edit-area"><iframe></iframe></div></div>';

    const iframe = document.querySelector("iframe") as HTMLIFrameElement;
    const scrollTo = vi.fn();
    Object.defineProperty(iframe, "contentWindow", {
      configurable: true,
      value: { scrollTo },
    });

    const originalOffset = harness.$.fn.offset;
    harness.$.fn.offset = function () {
      return {
        left: 0,
        top: 260,
      };
    } as JQuery["offset"];

    const callback = scrollHandler.getScrollToCallback($target, "131150");
    callback?.();

    expect(scrollTo).toHaveBeenCalledWith(0, 160);

    harness.$.fn.offset = originalOffset;
  });

  it("does not create a callback for clicks outside the rendered text field", () => {
    const callback = scrollHandler.getScrollToCallback(harness.$("<div>outside</div>"), "131150");

    expect(callback).toBeNull();
  });
});


