import { mergeTests } from "@playwright/test";
import { test as documentSessionTest } from "@/__tests__/e2e/fixtures/flows/documentSessions";
import { test as repositoryIntegrationTest } from "@/__tests__/e2e/fixtures/flows/repositoryIntegrations";

export type { SelfServicePiActor, UserSession } from "@/__tests__/e2e/fixtures/flows/userSessions";
export const test = mergeTests(repositoryIntegrationTest, documentSessionTest);
