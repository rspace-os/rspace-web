import { HttpResponse, http } from "msw";
import addFilesFixture from "./fixtures/addFiles.json" with { type: "json" };
import createDatasetFixture from "./fixtures/createDataset.json" with { type: "json" };
import datasetFixture from "./fixtures/dataset.json" with { type: "json" };
import dataverseFixture from "./fixtures/dataverse.json" with { type: "json" };

export const dataverseHandlers = [
  http.get("/api/v1/dataverses/:alias", () => HttpResponse.json(dataverseFixture)),
  http.post("/api/v1/dataverses/:alias/datasets", () => HttpResponse.json(createDatasetFixture)),
  http.get("/api/v1/datasets/:id", () => HttpResponse.json(datasetFixture)),
  http.post("/api/v1/datasets/:persistentId/add", () => HttpResponse.json(addFilesFixture)),
];
