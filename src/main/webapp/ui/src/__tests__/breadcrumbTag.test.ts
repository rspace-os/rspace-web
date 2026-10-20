import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { describe, expect, it } from "vitest";

type BreadcrumbElement = {
  attributes: Record<string, string>;
  text: string;
};

function isBreadcrumbElement(value: unknown): value is BreadcrumbElement {
  return typeof value === "object" && value !== null && "attributes" in value && "text" in value;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    const escaped: Record<string, string> = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    };
    return escaped[character] ?? character;
  });
}

describe("breadcrumb JSP tag", () => {
  it("passes string IDs and safely displays translated home and ordinary names", () => {
    const elements: Array<BreadcrumbElement> = [
      { attributes: { "data-id": "42", "data-home": "true" }, text: "Stored Home Folder Name" },
      { attributes: { "data-id": "43", "data-home": "false" }, text: "Research <Lab> & Team" },
    ];
    const addedElements: Array<{ tagId: unknown; elementId: unknown; displayName: unknown }> = [];
    const refreshedTags: Array<unknown> = [];
    let breadcrumbDataRemoved = false;
    const document = {};

    const jquery = (value: unknown) => {
      if (value === document) {
        return { ready: (callback: () => void) => callback() };
      }
      if (value === "#breadcrumbTag_test") {
        return {
          children: (selector: string) => {
            expect(selector).toBe(".breadcrumbData");
            return {
              children: (childSelector: string) => {
                expect(childSelector).toBe("span");
                return {
                  each: (callback: (this: BreadcrumbElement) => void) => {
                    elements.forEach((element) => {
                      callback.call(element);
                    });
                  },
                };
              },
              remove: () => {
                breadcrumbDataRemoved = true;
              },
            };
          },
        };
      }
      if (value === "<span>") {
        const span = {
          content: "",
          text(content: string) {
            span.content = content;
            return span;
          },
          html() {
            return escapeHtml(span.content);
          },
        };
        return span;
      }
      if (isBreadcrumbElement(value)) {
        return {
          data: (key: string) =>
            key === "home" ? value.attributes["data-home"] === "true" : Number(value.attributes[`data-${key}`]),
          attr: (name: string) => value.attributes[name],
          text: () => value.text,
        };
      }
      throw new Error(`Unexpected jQuery selector: ${String(value)}`);
    };

    const markup = readFileSync(resolve(__dirname, "../../../WEB-INF/tags/breadcrumb.tag"), "utf8");
    const scriptStart = markup.indexOf("<script>");
    const scriptEnd = markup.indexOf("</script>", scriptStart);
    if (scriptStart === -1 || scriptEnd === -1) throw new Error("breadcrumb tag script was not found");
    const script = markup.slice(scriptStart + "<script>".length, scriptEnd);

    runInNewContext(script.replaceAll(`\${breadcrumbTagId}`, "test"), {
      $: jquery,
      document,
      RS: {
        msg: (key: string) => {
          expect(key).toBe("legacyjs.core.breadcrumb.home");
          return "Home <Lab> & Team";
        },
        addBreadcrumbElement: (tagId: unknown, elementId: unknown, displayName: unknown) => {
          addedElements.push({ tagId, elementId, displayName });
        },
        refreshBreadcrumbElems: (tagId: unknown) => refreshedTags.push(tagId),
      },
    });

    expect(addedElements).toEqual([
      { tagId: "test", elementId: "42", displayName: "Home &lt;Lab&gt; &amp; Team" },
      { tagId: "test", elementId: "43", displayName: "Research &lt;Lab&gt; &amp; Team" },
    ]);
    expect(refreshedTags).toEqual(["test"]);
    expect(breadcrumbDataRemoved).toBe(true);
  });
});
