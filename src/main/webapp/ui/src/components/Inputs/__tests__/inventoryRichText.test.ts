import DOMPurify from "dompurify";
import { describe, expect, test } from "vitest";
import { PHISHING_DESCRIPTION } from "@/__tests__/mocks/inventoryRichTextMocks";
import { sanitizeInventoryRichText } from "../inventoryRichText";

describe("sanitizeInventoryRichText", () => {
  test("drops the form, the boxes, the button and the image of the reported description and keeps its text", () => {
    expect(sanitizeInventoryRichText(PHISHING_DESCRIPTION)).toBe(
      "<p>Calibrated light microscope.</p>" +
        "<p><strong>Your session expired. Sign in again to see the full record.</strong></p>" +
        "Sign in",
    );
  });

  test.each([
    ["a form", "<form action='https://example.org/login'><p>a</p></form>", "<p>a</p>"],
    ["a text box", "<p>a<input type='text' name='user'>b</p>", "<p>ab</p>"],
    ["a password box", "<p>a<input type='password' name='pw'>b</p>", "<p>ab</p>"],
    ["a text area", "<p>a</p><textarea>b</textarea>", "<p>a</p>b"],
    ["a drop-down", "<select><option>x</option></select>", "x"],
    ["a button", "<button>Sign in</button>", "Sign in"],
    [
      "an image from another website",
      "<p>a</p><img src='https://example.org/pixel.gif' alt='tracking pixel'>",
      "<p>a</p>",
    ],
    ["an image from this website", "<p>a</p><img src='/images/icons/sample.png'>", "<p>a</p>"],
    ["an inline image", "<p>a</p><img src='data:image/gif;base64,R0lGODlhAQABAAAAACw='>", "<p>a</p>"],
    ["an upper-case image tag", "<P>a</P><IMG SRC='https://example.org/pixel.gif'>", "<p>a</p>"],
    ["a script", "<p>a</p><script>alert(1)</script>", "<p>a</p>"],
    ["a stylesheet", "<style>p{display:none}</style><p>a</p>", "<p>a</p>"],
    ["an iframe", "<iframe src='https://example.org'></iframe><p>a</p>", "<p>a</p>"],
    ["an object", "<object data='https://example.org/x.swf'></object><p>a</p>", "<p>a</p>"],
    ["inline SVG", "<svg><a href='javascript:alert(1)'>x</a></svg><p>a</p>", "<p>a</p>"],
    ["a table", "<table><tr><td>cell</td></tr></table>", "cell"],
    ["an event handler", "<p onclick='alert(1)'>a</p>", "<p>a</p>"],
    ["a javascript: link", "<a href='javascript:alert(1)'>a</a>", "<a>a</a>"],
    ["a data attribute", "<p data-x='1'>a</p>", "<p>a</p>"],
    ["an aria attribute", "<p aria-hidden='true'>a</p>", "<p>a</p>"],
    ["controls nested in a list item", "<ul><li><form><input type='text'></form>a</li></ul>", "<ul><li>a</li></ul>"],
    [
      "a background image",
      '<p><span style="background-image: url(https://example.org/pixel.gif);">a</span></p>',
      "<p><span>a</span></p>",
    ],
    [
      "an image set",
      "<p><span style='background: image-set(\"https://example.org/pixel.gif\" 1x);'>a</span></p>",
      "<p><span>a</span></p>",
    ],
    [
      "an escaped background image",
      '<p><span style="b\\61 ckground-image: url(https://example.org/pixel.gif);">a</span></p>',
      "<p><span>a</span></p>",
    ],
    [
      "a background image next to a colour",
      '<p><span style="color: red; background-image: url(https://example.org/pixel.gif);">a</span></p>',
      '<p><span style="color: red;">a</span></p>',
    ],
    ["a full-page overlay", '<p><span style="position: fixed; inset: 0;">a</span></p>', "<p><span>a</span></p>"],
  ])("drops %s and keeps its text", (_name, html, expected) => {
    expect(sanitizeInventoryRichText(html)).toBe(expected);
  });

  test.each([
    ["a paragraph", "<p>a</p>"],
    ["headings", "<h1>a</h1><h2>b</h2><h3>c</h3><h4>d</h4><h5>e</h5><h6>f</h6>"],
    ["preformatted text", "<pre>a\nb</pre>"],
    ["a line break", "<p>a<br>b</p>"],
    ["bold", "<p><strong>a</strong></p>"],
    ["italic", "<p><em>a</em></p>"],
    ["underline", '<p><span style="text-decoration: underline;">a</span></p>'],
    ["plain bold, italic and underline tags", "<p><b>a</b><i>b</i><u>c</u></p>"],
    ["highlight colour", '<p><span style="background-color: #e03e2d;">a</span></p>'],
    ["text colour", '<p><span style="color: #e03e2d;">a</span></p>'],
    ["alignment", '<p style="text-align: center;">a</p>'],
    ["indentation", '<p style="padding-left: 40px;">a</p>'],
    ["upper-case style names", '<p><span style="COLOR: red; TEXT-DECORATION: line-through;">a</span></p>'],
    ["superscript and subscript", "<p>10<sup>-3</sup> M, H<sub>2</sub>O</p>"],
    ["strikethrough", "<p><s>a</s> <del>b</del></p>"],
    ["a link", '<p><a href="https://example.org/a" title="Example">a</a></p>'],
    ["a link that opens in a new tab", '<p><a href="https://example.org/a" target="_blank" rel="noopener">a</a></p>'],
    ["a link to an RSpace page", '<p><a href="/inventory/search?query=a">a</a></p>'],
    ["a bulleted list", "<ul><li>a</li><li>b</li></ul>"],
    ["a numbered list", "<ol><li>a</li><li>b</li></ol>"],
    ["list numbering", '<ol start="4" reversed="" type="a"><li value="7">a</li></ol>'],
    ["a nested list", "<ul><li>a<ul><li>b</li></ul></li></ul>"],
    ["entities", "<p>a&nbsp;b &amp; c</p>"],
  ])("keeps %s unchanged", (_name, html) => {
    expect(sanitizeInventoryRichText(html)).toBe(html);
  });

  test("keeps a bare less-than sign as text", () => {
    expect(sanitizeInventoryRichText("<p>Resolution <5 µm</p>")).toBe("<p>Resolution &lt;5 µm</p>");
  });

  test("returns an empty string for an empty description", () => {
    expect(sanitizeInventoryRichText("")).toBe("");
  });

  test("leaves styles alone where other pages use DOMPurify", () => {
    expect(DOMPurify.sanitize('<span style="position: fixed;">a</span>')).toBe(
      '<span style="position: fixed;">a</span>',
    );
  });
});
