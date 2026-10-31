import { expect, test } from "vitest";
import { apiV2Headers, apiV2JsonHeaders } from "../apiV2Headers";

test("sets authentication and request identification while retaining other headers", () => {
  const headers = apiV2Headers("token", {
    Authorization: "old",
    "X-Requested-With": "old",
    Accept: "application/json",
  });
  expect(headers.get("Authorization")).toBe("Bearer token");
  expect(headers.get("X-Requested-With")).toBe("XMLHttpRequest");
  expect(headers.get("Accept")).toBe("application/json");
});

test("JSON headers replace content type and preserve the remaining headers", () => {
  const headers = apiV2JsonHeaders("token", { "Content-Type": "text/plain", "If-Match": "version" });
  expect(headers.get("Content-Type")).toBe("application/json");
  expect(headers.get("If-Match")).toBe("version");
  expect(headers.get("Authorization")).toBe("Bearer token");
  expect(headers.get("X-Requested-With")).toBe("XMLHttpRequest");
});
