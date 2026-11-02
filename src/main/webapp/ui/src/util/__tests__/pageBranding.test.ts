import { afterEach, describe, expect, test } from "vitest";

import { currentPageKey } from "@/util/pageBranding";

describe("currentPageKey", () => {
  afterEach(() => {
    window.history.replaceState(null, "", "/");
  });

  test.each([
    ["/workspace/trash/list", "myRSpace"],
    ["/workspace/editor/form/list", "myRSpace"],
    ["/workspace/editor/form/", "myRSpace"],
    ["/workspace/editor/structuredDocument/audit/view", "myRSpace"],
    ["/workspace/editor/structuredDocument/123", "workspace"],
    ["/workspace", "workspace"],
    ["/gallery", "gallery"],
  ])("%s -> %s", (path, expected) => {
    window.history.replaceState(null, "", path);
    expect(currentPageKey()).toBe(expected);
  });
});
