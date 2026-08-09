#!/usr/bin/env node

import { createServer } from "node:http";
import { getRequestListener } from "@hono/node-server";
import { createMiddleware } from "@mswjs/http-middleware";
import { Hono } from "hono";
import { HttpResponse, http } from "msw";
import { argosHandlers } from "../../modules/argos/__tests__/mock.ts";
import { caliraHandlers } from "../../modules/calira/__tests__/mock.ts";
import { dataverseHandlers } from "../../modules/dataverse/__tests__/mock.ts";
import { dcdHandlers } from "../../modules/dcd/__tests__/mock.ts";
import { dmpassistantHandlers } from "../../modules/dmpassistant/__tests__/mock.ts";
import { dmponlineHandlers } from "../../modules/dmponline/__tests__/mock.ts";
import { dmptoolHandlers } from "../../modules/dmptool/__tests__/mock.ts";
import { dryadHandlers } from "../../modules/dryad/__tests__/mock.ts";
import { dswHandlers } from "../../modules/dsw/__tests__/mock.ts";
import { fieldmarkHandlers } from "../../modules/fieldmark/__tests__/mock.ts";
import { galaxyHandlers } from "../../modules/galaxy/__tests__/mock.ts";
import { githubHandlers } from "../../modules/github/__tests__/mock.ts";
import { msteamsHandlers } from "../../modules/msteams/__tests__/mock.ts";
import { nextcloudHandlers } from "../../modules/nextcloud/__tests__/mock.ts";
import { omeroHandlers } from "../../modules/omero/__tests__/mock.ts";
import { orcidHandlers } from "../../modules/orcid/__tests__/mock.ts";
import { owncloudHandlers } from "../../modules/owncloud/__tests__/mock.ts";
import { protocolsioHandlers } from "../../modules/protocolsio/__tests__/mock.ts";
import { pubchemHandlers } from "../../modules/pubchem/__tests__/mock.ts";
import { pyratHandlers } from "../../modules/pyrat/__tests__/mock.ts";
import { raidHandlers } from "../../modules/raid/__tests__/mock.ts";
import { slackHandlers } from "../../modules/slack/__tests__/mock.ts";
import { snapgeneHandlers } from "../../modules/snapgene/__tests__/mock.ts";
import { zenodoHandlers } from "../../modules/zenodo/__tests__/mock.ts";
import { dataciteHandlers } from "./mocks/datacite.ts";

const PORT = Number(process.argv[2] ?? process.env.E2E_MOCK_PORT ?? "9099");
const HOST = process.env.E2E_MOCK_HOST ?? "127.0.0.1";

const healthHandler = http.get(
  "/e2e-health",
  () => new HttpResponse("ok", { headers: { "Content-Type": "text/plain" } }),
);

const handlers = [
  healthHandler,
  ...argosHandlers,
  ...pubchemHandlers,
  ...fieldmarkHandlers,
  ...zenodoHandlers,
  ...galaxyHandlers,
  ...githubHandlers,
  ...msteamsHandlers,
  ...dataverseHandlers,
  ...dcdHandlers,
  ...dswHandlers,
  ...dmptoolHandlers,
  ...dmpassistantHandlers,
  ...dmponlineHandlers,
  ...raidHandlers,
  ...slackHandlers,
  ...snapgeneHandlers,
  ...dryadHandlers,
  ...caliraHandlers,
  ...pyratHandlers,
  ...omeroHandlers,
  ...orcidHandlers,
  ...owncloudHandlers,
  ...nextcloudHandlers,
  ...protocolsioHandlers,
  ...dataciteHandlers,
];
const mswMiddleware = createMiddleware(...handlers);

const app = new Hono();
app.notFound((c) => c.text("not found", 404));
const honoListener = getRequestListener(app.fetch);

createServer((req, res) => {
  const header = (name: string) => req.headers[name.toLowerCase()];
  const mockRequest = Object.assign(req, { protocol: "http", get: header, header }) as unknown as Parameters<
    typeof mswMiddleware
  >[0];
  mswMiddleware(mockRequest, res, (err: unknown) => {
    if (err) {
      res.statusCode = 500;
      res.end(String(err));
      return;
    }
    honoListener(req, res);
  });
}).listen(PORT, HOST, () => {
  console.log(`[e2e-mocks] listening on http://${HOST}:${PORT}`);
});
