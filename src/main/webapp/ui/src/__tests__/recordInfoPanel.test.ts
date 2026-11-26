import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it } from "vitest";
import { legacyMsg } from "@/__tests__/helpers/legacyI18n";

class FakeDiv {
  html = "";
  clickHandler: (() => boolean) | undefined;

  empty() {
    this.html = "";
    return this;
  }

  append(value: string) {
    this.html += value;
    return this;
  }

  find() {
    return {
      click: (handler: () => boolean) => {
        this.clickHandler = handler;
      },
    };
  }
}

type LinkedRecord = {
  id?: number;
  name?: string;
  oid?: { idString: string };
  ownerFullName: string;
};

function loadRecordInfoPanelFunctions() {
  let respond: ((response: { data: LinkedRecord[] }) => void) | undefined;
  const jquery = Object.assign(() => ({ ready: () => {} }), {
    get: () => ({
      done: (handler: (response: { data: LinkedRecord[] }) => void) => {
        respond = handler;
      },
    }),
    each: (values: LinkedRecord[] | Record<string, number>, callback: (key: string | number, value: never) => void) => {
      Object.keys(values).forEach((key) => {
        callback(key, values[key as keyof typeof values] as never);
      });
    },
  });
  const escapeHtml = (value: unknown) =>
    String(value).replace(
      /[&<>"'/]/g,
      (character) =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;", "/": "&#x2F;" })[character] ??
        character,
    );
  const sandbox = {
    $: jquery,
    RS: {
      escapeHtml,
      msg: legacyMsg,
    },
    document,
    window: { location: { href: "" } },
    console,
  };

  const source = readFileSync(resolve(__dirname, "../../../scripts/pages/recordInfoPanel.js"), "utf8");
  runInNewContext(source, sandbox);

  return {
    render: (sandbox as unknown as Record<string, unknown>)._setInfoPanelInternalLinksHtml as (
      div: FakeDiv,
      info: { id: number; linkedByCount: number },
      recordType: string,
    ) => void,
    respond: (records: LinkedRecord[]) => respond?.({ data: records }),
    sharingHtml: (sandbox as unknown as Record<string, unknown>)._getInfoPanelSharingHtml as (
      info: {
        shared?: boolean;
        implicitlyShared?: boolean;
        sharedGroupsAndAccess?: Record<string, string>;
        sharedUsersAndAccess?: Record<string, string>;
      },
      isNotebook: boolean,
    ) => string,
    displayType: (sandbox as unknown as Record<string, unknown>)._getInfoPanelTypeDisplayName as (
      type: string,
    ) => string,
    setTags: (sandbox as unknown as Record<string, unknown>)._setInfoPanelTags as (
      cell: HTMLElement,
      info: { tags: string | null; tagMetaData?: string | null },
    ) => void,
  };
}

describe("record info panel internal links", () => {
  it("escapes record types in the no-links message", () => {
    const { render } = loadRecordInfoPanelFunctions();
    const div = new FakeDiv();

    render(div, { id: 1, linkedByCount: 0 }, '<img src=x onerror="alert(1)">');

    expect(div.html).not.toContain("<img");
    expect(div.html).toContain("&lt;img");
  });

  it("escapes linked-record data and safely counts prototype-like owner names", () => {
    const { render, respond } = loadRecordInfoPanelFunctions();
    const div = new FakeDiv();
    render(div, { id: 1, linkedByCount: 3 }, "document");

    div.clickHandler?.();
    respond([
      {
        id: 2,
        name: '<img src=x onerror="alert(1)">',
        oid: { idString: "SD1'><img src=x>" },
        ownerFullName: "Visible Owner",
      },
      { ownerFullName: "__proto__" },
      { ownerFullName: '<img src=x onerror="alert(2)">' },
    ]);

    expect(div.html).not.toContain("<img");
    expect(div.html).toContain("&lt;img");
    expect(div.html).toContain("SD1%27%3E%3Cimg%20src%3Dx%3E");
    expect(div.html).toContain("__proto__");
  });

  it("renders record types and access levels as translated phrases", () => {
    const { sharingHtml } = loadRecordInfoPanelFunctions();

    expect(
      sharingHtml(
        {
          shared: true,
          sharedGroupsAndAccess: { "Example group": "READ" },
          sharedUsersAndAccess: { "Example user": "WRITE" },
        },
        true,
      ),
    ).toBe(
      "This notebook is shared: <ul><li>with Example group (group) for read</li>" +
        "<li>with Example user (user) for write</li></ul>",
    );
  });
});

describe("record info panel type display names", () => {
  it("translates every known record type while preserving unknown values", () => {
    const { displayType } = loadRecordInfoPanelFunctions();

    const recordTypes = [
      ["Structured Document", "Structured Document"],
      ["Folder", "Folder"],
      ["Notebook", "Notebook"],
      ["System Folder", "System Folder"],
      ["Template", "Template"],
      ["Image", "Image"],
      ["Video", "Video"],
      ["Audio", "Audio"],
      ["Document", "Document"],
      ["Chemistry", "Chemistry"],
      ["PdfDocuments", "Exports"],
      ["Miscellaneous", "Miscellaneous"],
      ["DMPs", "DMPs"],
      ["Snippet", "Snippet"],
      ["Form", "Form"],
    ];

    for (const [type, displayName] of recordTypes) {
      expect(displayType(type)).toBe(displayName);
    }
    expect(displayType("Future Record Type")).toBe("Future Record Type");
  });
});

describe("record info panel tags", () => {
  const term = (label: string, uri: string) =>
    `${label}__RSP_EXTONT_URL_DELIM__${uri}__RSP_EXTONT_NAME_DELIM__Vocabulary__RSP_EXTONT_VERSION_DELIM__1.0`;

  it("lists ontology terms with their source and safe links", () => {
    const { setTags } = loadRecordInfoPanelFunctions();
    const cell = document.createElement("td");

    setTags(cell, {
      tags: "plain,<img src=x>,unsafe",
      tagMetaData: [
        "plain",
        term("<img src=x>", "https:__rspactags_forsl____rspactags_forsl__example.org__rspactags_forsl__x"),
        term("unsafe", "javascript:alert(1)"),
      ].join(","),
    });

    expect(cell.querySelector("img")).toBeNull();
    const [plain, encoded, unsafe] = Array.from(cell.querySelectorAll("li"));
    expect(plain.textContent).toBe("plain");
    expect(encoded.textContent).toBe("<img src=x> (from version 1.0 of Vocabulary) https://example.org/x");
    const [uriLink, ...otherLinks] = Array.from(encoded.querySelectorAll("a"));
    expect(uriLink.getAttribute("href")).toBe("https://example.org/x");
    expect(uriLink.getAttribute("rel")).toBe("noopener noreferrer");
    // An Inventory search would not find the ELN record that carries the tag.
    expect(otherLinks).toEqual([]);
    expect(unsafe.textContent).toBe("unsafe (from version 1.0 of Vocabulary) javascript:alert(1)");
    expect(unsafe.querySelector("a")).toBeNull();
  });

  it("replaces earlier content and keeps plain text when no tag has metadata", () => {
    const { setTags } = loadRecordInfoPanelFunctions();
    const cell = document.createElement("td");
    setTags(cell, { tags: "x", tagMetaData: term("x", "https://example.org/x") });

    setTags(cell, { tags: "a__rspactags_forsl__b,c", tagMetaData: "a__rspactags_forsl__b,c" });

    expect(cell.innerHTML).toBe("a/b, c");
  });
});
