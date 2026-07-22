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

function loadInternalLinksRenderer() {
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
    document: {},
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
  };
}

describe("record info panel internal links", () => {
  it("escapes record types in the no-links message", () => {
    const { render } = loadInternalLinksRenderer();
    const div = new FakeDiv();

    render(div, { id: 1, linkedByCount: 0 }, '<img src=x onerror="alert(1)">');

    expect(div.html).not.toContain("<img");
    expect(div.html).toContain("&lt;img");
  });

  it("escapes linked-record data and safely counts prototype-like owner names", () => {
    const { render, respond } = loadInternalLinksRenderer();
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
});
