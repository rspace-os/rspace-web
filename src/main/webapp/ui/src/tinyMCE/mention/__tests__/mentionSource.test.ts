/**
 * RSDEV-992: @mentions returned an empty list for large labgroups.
 *
 * Root cause: in this TinyMCE version the mention plugin's caret lands in the
 * '#autocomplete-delimiter' span, so typed text never reaches '#autocomplete-searchtext'
 * and the plugin always passes an empty `query` to `source`. Every lookup therefore sent
 * term="" and the server returned the ENTIRE shared group; for large labgroups the client
 * then rendered everything and stalled, surfacing as an empty list.
 *
 * Fixes under test:
 *  - `source` reads the actually-typed text from the '#autocomplete' span (in the editor
 *    iframe), strips the delimiter and the plugin's \ufeff caret placeholder, and uses it
 *    as the search term, so the server filters the recipient list.
 *  - lookups are sequence-tagged so a slow, broad response (term="") arriving late cannot
 *    clobber the dropdown rendered by a newer, narrower lookup.
 *  - `highlighter` is a no-op for the (always-empty) plugin query, instead of building the
 *    pathological /()/ig regex that wraps every character of every entry in <strong>.
 *
 * This test loads the real (legacy) tinymce config via a vm sandbox and asserts on the
 * requests made and the data passed to the plugin's `process` callback.
 */
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import { describe, expect, it, vi } from "vitest";

// Resolve from this file's location rather than process.cwd(): the jsdom test
// environment polyfills `process` (vite-plugin-node-polyfills), so
// `process.cwd()` returns "/" instead of the ui directory. Do not use the
// `new URL("...", import.meta.url)` form - Vite rewrites that pattern into a
// non-file asset URL.
const __dirname = dirname(fileURLToPath(import.meta.url));

type ServerUser = { firstName: string; lastName: string; username: string };
type SuccessCb = (data: { data: Array<ServerUser> | null }) => void;
type GetCall = { url: string; params: Record<string, unknown>; respond: SuccessCb; fail: () => void };
type MentionSource = (query: string, process: (items: unknown) => void, delimiter: string) => void;
type Mentions = {
  source: MentionSource;
  highlighter: (this: { query: string }, text: string) => string;
};

/** Loads the config into a sandbox; `autocompleteText` is what the editor's #autocomplete span contains. */
function load(autocompleteText: string | null): { mentions: Mentions; getCalls: GetCall[] } {
  const getCalls: GetCall[] = [];
  const jq = vi.fn((_sel: string) => ({ text: () => "SD2838" })) as unknown as {
    (sel: string): { text: () => string };
    get: (url: string, params: Record<string, unknown>, cb: SuccessCb) => { fail: (cb: () => void) => void };
  };
  jq.get = (url, params, cb) => {
    const call: GetCall = { url, params, respond: cb, fail: () => {} };
    getCalls.push(call);
    return {
      fail: (failCb) => {
        call.fail = failCb;
      },
    };
  };

  const editorBody = {
    querySelector: (sel: string) =>
      sel === "#autocomplete" && autocompleteText !== null ? { textContent: autocompleteText } : null,
  };

  const sandbox: Record<string, unknown> = {
    tinymce: { PluginManager: { add: () => {} }, activeEditor: { getBody: () => editorBody } },
    $: jq,
    window: {},
    document: { querySelector: () => null },
    console,
  };

  const src = readFileSync(
    resolve(
      __dirname,
      "../../../../../scripts/pages/workspace/editor/tinymce5_configuration.js",
    ),
    "utf8",
  );
  runInNewContext(src, sandbox);
  const setup = sandbox.tinymcesetup as { mentions: Mentions };
  return { mentions: setup.mentions, getCalls };
}

const aUser = (username: string): ServerUser => ({
  firstName: username,
  lastName: "User",
  username,
});

describe("RSDEV-992 mention source uses the typed text as the search term", () => {
  it("sends the typed text (minus the delimiter) as the term, not the empty plugin query", () => {
    // plugin passes query="" because the caret landed in the delimiter span; #autocomplete holds "@ro"
    const { mentions, getCalls } = load("@ro");
    mentions.source("", vi.fn(), "@");
    expect(getCalls).toHaveLength(1);
    expect(getCalls[0].url).toBe("/messaging/ajax/recipients");
    expect(getCalls[0].params.term).toBe("ro");
  });

  it("strips only the leading delimiter", () => {
    const { mentions, getCalls } = load("@john.doe");
    mentions.source("", vi.fn(), "@");
    expect(getCalls[0].params.term).toBe("john.doe");
  });

  it("strips the plugin's \\ufeff caret placeholder and surrounding whitespace", () => {
    // the dummy caret span inside #autocomplete-searchtext holds a zero-width no-break space
    const { mentions, getCalls } = load("@ro\ufeff");
    mentions.source("", vi.fn(), "@");
    expect(getCalls[0].params.term).toBe("ro");
  });

  it("falls back to the plugin query when only the delimiter and placeholder are present", () => {
    const { mentions, getCalls } = load("@\ufeff");
    mentions.source("", vi.fn(), "@");
    expect(getCalls[0].params.term).toBe("");
  });

  it("falls back to the plugin query when the autocomplete span is absent", () => {
    const { mentions, getCalls } = load(null);
    mentions.source("smith", vi.fn(), "@");
    expect(getCalls[0].params.term).toBe("smith");
  });
});

describe("RSDEV-992 stale lookup responses are discarded", () => {
  it("ignores an earlier broad response that resolves after a newer lookup", () => {
    const { mentions, getCalls } = load("@ro");
    const process = vi.fn();
    mentions.source("", process, "@"); // broad lookup (e.g. the initial '@')
    mentions.source("", process, "@"); // newer lookup
    expect(getCalls).toHaveLength(2);

    // newer response arrives first, then the stale broad one
    getCalls[1].respond({ data: [aUser("rob")] });
    getCalls[0].respond({ data: [aUser("rob"), aUser("roberta"), aUser("rocco")] });

    expect(process).toHaveBeenCalledTimes(1);
    expect((process.mock.calls[0][0] as Array<ServerUser>).map((u) => u.username)).toEqual(["rob"]);
  });

  it("ignores a stale failure arriving after a newer lookup", () => {
    const { mentions, getCalls } = load("@ro");
    const process = vi.fn();
    mentions.source("", process, "@");
    mentions.source("", process, "@");

    getCalls[1].respond({ data: [aUser("rob")] });
    getCalls[0].fail();

    expect(process).toHaveBeenCalledTimes(1);
    expect(process).not.toHaveBeenCalledWith({});
  });

  it("still reports a failure of the latest lookup", () => {
    const { mentions, getCalls } = load("@ro");
    const process = vi.fn();
    mentions.source("", process, "@");
    getCalls[0].fail();
    expect(process).toHaveBeenCalledWith({});
  });
});

describe("RSDEV-992 highlighter", () => {
  it("is a no-op for an empty query instead of wrapping every character", () => {
    const { mentions } = load(null);
    expect(mentions.highlighter.call({ query: "" }, "Rob Principal <rob>")).toBe("Rob Principal <rob>");
  });

  it("still wraps case-insensitive matches of a non-empty query", () => {
    const { mentions } = load(null);
    expect(mentions.highlighter.call({ query: "ro" }, "Rob")).toBe("<strong>Ro</strong>b");
  });
});
