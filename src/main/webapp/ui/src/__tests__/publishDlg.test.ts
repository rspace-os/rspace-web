import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it } from "vitest";
import { legacyMsg } from "@/__tests__/helpers/legacyI18n";

type Section = "internet" | "link";
type Handler = () => void;
type DialogOptions = { open: (this: object) => void; buttons: Array<{ click: (this: object) => void }> };
type AccordionOptions = { activate: (event: object, ui: { oldHeader: object; newPanel: object }) => void };
type AjaxRequest = { url: string; data: string };

const SECTIONS = {
  internet: {
    header: "publishPublicHeader",
    confirmation: "#make_public_confirmation",
    label: ".publishSelected",
    description: "#publicationDescription",
    contactDetails: "#displayContactDetails",
  },
  link: {
    header: "publishPublicLinkHeader",
    confirmation: "#make_public_link_confirmation",
    label: ".publishLinkSelected",
    description: "#publicationLinkDescription",
    contactDetails: "#displayLinkContactDetails",
  },
} as const;

/** One selected element: only the state the publish dialog script reads and writes. */
class FakeElement {
  value = "";
  disabled = false;
  checked = false;
  content = "";

  val(value?: string) {
    if (value === undefined) return this.value;
    this.value = value;
    return this;
  }

  prop(name: string, value: boolean) {
    if (name === "disabled") this.disabled = value;
    if (name === "checked") this.checked = value;
    return this;
  }

  is() {
    return this.checked;
  }

  css() {
    return this;
  }

  text(content?: string) {
    if (content === undefined) return this.content;
    this.content = content;
    return this;
  }
}

function loadPublishDialog() {
  const elements = new Map<string, FakeElement>();
  const handlers = new Map<string, Handler>();
  const document = {};
  const dialog = {};
  const ajaxRequests: Array<AjaxRequest> = [];
  let dialogOptions: DialogOptions | undefined;
  let accordionOptions: AccordionOptions | undefined;
  let dialogClosed = false;

  // `input[id="x"]` and `#x` name the same element.
  const element = (selector: string) => {
    const id = selector.replace(/^input\[id="(.+)"\]$/, "#$1");
    const existing = elements.get(id);
    if (existing) return existing;
    const created = new FakeElement();
    elements.set(id, created);
    return created;
  };

  const jquery = (target: unknown) => {
    if (target === document) {
      return {
        on: (event: string, selector: string, handler: Handler) => {
          handlers.set(`${event} ${selector}`, handler);
        },
      };
    }
    if (target === "#publish-dialog") {
      return {
        dialog: (options: DialogOptions) => {
          dialogOptions = options;
        },
      };
    }
    if (target === dialog) {
      return {
        find: () => ({
          accordion: (options: AccordionOptions) => {
            accordionOptions = options;
          },
        }),
        data: () => undefined,
        dialog: () => {
          dialogClosed = true;
        },
      };
    }
    if (typeof target === "string") return element(target);
    if (typeof target === "object" && target !== null && "id" in target) return { length: 1, 0: target };
    if (typeof target === "object" && target !== null && "confirmation" in target) {
      // A panel finds only the confirmation input it contains.
      return {
        find: (selector: string) =>
          selector.split(", ").includes(String(target.confirmation))
            ? element(String(target.confirmation))
            : { val: () => undefined },
      };
    }
    throw new Error(`Unexpected jQuery target: ${String(target)}`);
  };

  // The submit handler only attaches callbacks to the request, so none of them run.
  jquery.ajax = (request: AjaxRequest) => {
    ajaxRequests.push(request);
    return { always: () => {}, done: () => {}, fail: () => {} };
  };

  const sandbox = {
    $: jquery,
    RS: { msg: legacyMsg, blockPage: () => {}, unblockPage: () => {} },
    createURL: (url: string) => url,
    document,
  };
  runInNewContext(readFileSync(resolve(__dirname, "../../../scripts/tags/publishDlg.js"), "utf8"), sandbox);
  (sandbox as unknown as { createPublishDialog: (idsGetter: () => number[]) => void }).createPublishDialog(() => [1]);
  dialogOptions?.open.call(dialog);

  return {
    publishButton: element("#publish-dialog-submit-btn"),
    ajaxRequests,
    isDialogClosed: () => dialogClosed,
    typeConfirmation(section: Section, text: string) {
      const { confirmation } = SECTIONS[section];
      element(confirmation).val(text);
      handlers.get(`input ${confirmation}`)?.();
    },
    confirmedLabel(section: Section) {
      return element(SECTIONS[section].label).content;
    },
    fillSection(section: Section, description: string, displayContactDetails: boolean) {
      element(SECTIONS[section].description).val(description);
      element(SECTIONS[section].contactDetails).prop("checked", displayContactDetails);
    },
    /** Clicks the dialog's Publish button, the second of its two buttons. */
    clickPublish() {
      dialogOptions?.buttons[1]?.click.call(dialog);
    },
    /** The accordion reports a section switch only once its animation has finished. */
    finishSwitchingSection(from: Section, to: Section) {
      accordionOptions?.activate(
        {},
        { oldHeader: { id: SECTIONS[from].header }, newPanel: { confirmation: SECTIONS[to].confirmation } },
      );
    },
  };
}

describe("publish dialog", () => {
  it("disables Publish when the dialog opens", () => {
    const { publishButton } = loadPublishDialog();

    expect(publishButton.disabled).toBe(true);
  });

  it("keeps Publish enabled when the confirmation is typed before the section switch finishes", () => {
    const { publishButton, typeConfirmation, finishSwitchingSection } = loadPublishDialog();

    typeConfirmation("link", "confirm");
    finishSwitchingSection("internet", "link");

    expect(publishButton.disabled).toBe(false);
  });

  it("leaves Publish disabled after switching to a section that is not confirmed", () => {
    const { publishButton, typeConfirmation, finishSwitchingSection } = loadPublishDialog();

    typeConfirmation("link", "confirm");
    finishSwitchingSection("link", "internet");

    expect(publishButton.disabled).toBe(true);
  });

  it("disables Publish and clears the confirmed label when the confirmation is edited to something else", () => {
    const { publishButton, typeConfirmation, confirmedLabel } = loadPublishDialog();

    typeConfirmation("link", "confirm");
    typeConfirmation("link", "confirm x");

    expect(publishButton.disabled).toBe(true);
    expect(confirmedLabel("link")).toBe("");
  });

  it("publishes by link only when only the link section is confirmed", () => {
    const { typeConfirmation, fillSection, clickPublish, ajaxRequests, isDialogClosed } = loadPublishDialog();
    fillSection("internet", "internet summary", false);
    fillSection("link", "link summary", true);
    typeConfirmation("link", "CONFIRM");

    clickPublish();

    expect(ajaxRequests).toHaveLength(1);
    expect(JSON.parse(ajaxRequests[0].data)).toEqual({
      idsToShare: [1],
      values: [{ publicationSummary: "link summary", displayContactDetails: true, publishOnInternet: false }],
      publish: true,
    });
    expect(isDialogClosed()).toBe(true);
  });

  it("publishes on the internet when the internet section is confirmed", () => {
    const { typeConfirmation, fillSection, clickPublish, ajaxRequests } = loadPublishDialog();
    fillSection("internet", "internet summary", false);
    fillSection("link", "link summary", true);
    typeConfirmation("internet", "confirm");

    clickPublish();

    expect(ajaxRequests).toHaveLength(1);
    expect(JSON.parse(ajaxRequests[0].data).values).toEqual([
      { publicationSummary: "internet summary", displayContactDetails: false, publishOnInternet: true },
    ]);
  });

  it("sends nothing when no section is confirmed", () => {
    const { typeConfirmation, clickPublish, ajaxRequests, isDialogClosed } = loadPublishDialog();
    typeConfirmation("link", "conf");

    clickPublish();

    expect(ajaxRequests).toHaveLength(0);
    expect(isDialogClosed()).toBe(false);
  });
});
