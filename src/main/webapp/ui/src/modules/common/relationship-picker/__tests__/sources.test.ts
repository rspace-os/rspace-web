import { HttpResponse, http } from "msw";
import { expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { relationshipSources } from "../sources";

test("instrument search rejects an unsuccessful response", async () => {
  server.use(http.get("/api/v2/instruments", () => new HttpResponse(null, { status: 503 })));

  await expect(
    relationshipSources.instruments.search("microscope", "token", new AbortController().signal),
  ).rejects.toThrow("Relationship option request failed with status 503");
});

test("instrument resolution returns null for a missing record", async () => {
  server.use(http.get("/api/v2/instruments/1", () => new HttpResponse(null, { status: 404 })));

  await expect(
    relationshipSources.instruments.resolve?.("IN1", "token", new AbortController().signal),
  ).resolves.toBeNull();
});

test("instrument resolution rejects failures other than a missing record", async () => {
  server.use(http.get("/api/v2/instruments/1", () => new HttpResponse(null, { status: 403 })));

  await expect(relationshipSources.instruments.resolve?.("IN1", "token", new AbortController().signal)).rejects.toThrow(
    "Relationship option request failed with status 403",
  );
});
