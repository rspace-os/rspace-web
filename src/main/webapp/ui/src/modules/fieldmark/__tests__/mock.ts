import { readFileSync } from "node:fs";
import { HttpResponse, http } from "msw";
import notebookFixture from "./fixtures/notebookID.json" with { type: "json" };
import notebooksFixture from "./fixtures/notebooks.json" with { type: "json" };
import recordsFixture from "./fixtures/records.json" with { type: "json" };

const csvFixture = readFileSync(new URL("./fixtures/notebook.csv", import.meta.url));
const zipFixture = readFileSync(new URL("./fixtures/FieldmarkFile.zip", import.meta.url));

export const fieldmarkHandlers = [
  http.post("/auth/exchange-long-lived-token", () => HttpResponse.json({ token: "short-lived-mock-token" })),

  http.get("/notebooks/", () => HttpResponse.json(notebooksFixture)),

  http.get(
    "/notebooks/:notebookId/records/:recordId.csv",
    () => new HttpResponse(csvFixture, { headers: { "Content-Type": "text/csv" } }),
  ),

  http.get(
    "/notebooks/:notebookId/records/:recordId.zip",
    () => new HttpResponse(zipFixture, { headers: { "Content-Type": "application/zip" } }),
  ),

  http.get("/notebooks/:notebookId/records", () => HttpResponse.json(recordsFixture)),

  http.get("/notebooks/:notebookId", () => HttpResponse.json(notebookFixture)),
];
