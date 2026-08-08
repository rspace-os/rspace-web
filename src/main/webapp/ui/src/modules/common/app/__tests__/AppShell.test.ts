import { describe, expect, it } from "vitest";
import { getAppBarConfig } from "@/modules/common/app/AppShell";

describe("getAppBarConfig", () => {
  it("uses the deepest matched route app bar config", () => {
    expect(
      getAppBarConfig([
        { context: { appBar: { currentPage: "Root" } } },
        { context: { appBar: { currentPage: "booking" } } },
      ]),
    ).toEqual({ currentPage: "booking" });
  });

  it("allows a route to opt out of the app bar", () => {
    expect(getAppBarConfig([{ context: { appBar: { currentPage: "Root" } } }, { context: { appBar: false } }])).toBe(
      false,
    );
  });

  it("defaults to a generic RSpace app bar", () => {
    expect(getAppBarConfig([{ context: {} }])).toEqual({ currentPage: "rspace" });
  });
});
