import { describe, expect, test } from "vitest";
import { searchLinkFailureReason } from "../searchLinkFailure";

describe("searchLinkFailureReason", () => {
  test("reads the message of the server's error page as plain text", () => {
    const page = '<html><body><span id="ajaxErrorMsg">Something went wrong:\n  bad <b>term</b>\n</span></body></html>';
    expect(searchLinkFailureReason(page)).toBe("Something went wrong: bad term");
  });

  test("is empty when the response has no error message", () => {
    expect(searchLinkFailureReason("<html><body>Oops</body></html>")).toBe("");
    expect(searchLinkFailureReason(undefined)).toBe("");
  });

  test("is cut to 200 characters", () => {
    const page = `<span id="ajaxErrorMsg">${"x".repeat(300)}</span>`;
    expect(searchLinkFailureReason(page)).toHaveLength(200);
  });
});
