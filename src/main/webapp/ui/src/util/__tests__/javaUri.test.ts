import { describe, expect, test } from "vitest";
import { isJavaUri } from "../javaUri";

/*
 * Each case was checked against `new java.net.URI(value)` on Java 17, which is
 * how the server validates a URI field.
 */
describe("isJavaUri", () => {
  test.each([
    "https://lab.example.org/lsm980",
    "manuals/lsm980.pdf",
    "/manuals/lsm980.pdf",
    "../lsm980",
    "?q=1",
    "#section",
    "//cdn.example.org/a",
    "urn:isbn:0-486-27557-4",
    "mailto:lab@example.org",
    "file:///srv/manual.pdf",
    "http://[::1]:8080/",
    "http://[fe80::1%eth0]/",
    "http://ex\u00e4mple.org/\u00fc",
    "http://host:abc/",
    "https://lab.example.org/%41",
    "http://#top",
    "http://u@[::1]:2147483647/",
  ])("accepts %j", (value) => {
    expect(isJavaUri(value)).toBe(true);
  });

  test.each([
    "https://lab.example.org/lsm 980",
    "https://lab.example.org/a|b",
    "https://lab.example.org/%zz",
    "a#b#c",
    "1abc:def",
    ":foo",
    "mailto:",
    "http://",
    "http://[::1",
    "http://[1::2::3]/",
    "http://[::1]:99999999999/",
    "C:\\manuals",
    "x\u00a0y",
    "x\u3000y",
  ])("rejects %j", (value) => {
    expect(isJavaUri(value)).toBe(false);
  });
});
