import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { runInNewContext } from "node:vm";
import { JSDOM } from "jsdom";
import { describe, expect, it, vi } from "vitest";

const JQUERY_SOURCE = readFileSync(
  resolve(__dirname, "../../../scripts/bower_components/jquery/dist/jquery.js"),
  "utf8",
);
const NOTIFICATIONS_SOURCE = readFileSync(
  resolve(__dirname, "../../../scripts/pages/messaging/notifications.js"),
  "utf8",
);

function loadNotificationClient(markup: string) {
  const dom = new JSDOM(markup);
  runInNewContext(JQUERY_SOURCE, { window: dom.window, document: dom.window.document });

  const jquery = (dom.window as unknown as { jQuery: JQueryStatic }).jQuery;
  const unescapeHtml = vi.fn((html: string) => jquery("<div />").html(html).text());
  const clientContext: Record<string, unknown> = {
    document: dom.window.document,
    $: (selector: string | Document | Element) =>
      selector === dom.window.document ? { ready: () => undefined } : jquery(selector as never),
    RS: { unescape: unescapeHtml },
  };

  runInNewContext(NOTIFICATIONS_SOURCE, clientContext);

  return {
    dom,
    unescapeHtml,
    unescapeMessageContent: clientContext.unescapeMessageContent as () => void,
  };
}

describe("notification message rendering", () => {
  it("keeps booking links and escaped instrument markup as text across repeated rendering", () => {
    const instrumentName = '<img src=x onerror="alert(1)">';
    const { dom, unescapeHtml, unescapeMessageContent } = loadNotificationClient(
      `<div id="notificationListContents">
        <div class="msgContent bookingNotificationMessage">
          <a href="/booking/booking/42">Booking 42</a> for &lt;img src=x onerror=&quot;alert(1)&quot;&gt;
        </div>
        <div class="msgContent" id="legacyNotification">&lt;strong&gt;Legacy notification&lt;/strong&gt;</div>
      </div>`,
    );

    try {
      unescapeMessageContent();
      unescapeMessageContent();

      const bookingMessage = dom.window.document.querySelector(".bookingNotificationMessage");
      expect(bookingMessage?.querySelector("a")).toHaveAttribute("href", "/booking/booking/42");
      expect(bookingMessage?.querySelector("img")).toBeNull();
      expect(bookingMessage?.textContent?.trim()).toBe(`Booking 42 for ${instrumentName}`);
      expect(unescapeHtml.mock.calls).toEqual([
        ["&lt;strong&gt;Legacy notification&lt;/strong&gt;"],
        ["<strong>Legacy notification</strong>"],
      ]);
      expect(dom.window.document.querySelector("#legacyNotification")).toHaveTextContent("Legacy notification");
    } finally {
      dom.window.close();
    }
  });
});
