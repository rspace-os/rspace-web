import { mergeTests } from "@playwright/test";
import { test as fileSystemsTest } from "@/__tests__/e2e/fixtures/flows/environment/fileSystems";
import { test as filestoreBackendsTest } from "@/__tests__/e2e/fixtures/flows/environment/filestoreBackends";
import { test as ipWhitelistTest } from "@/__tests__/e2e/fixtures/flows/environment/ipWhitelist";
import { test as publicSharingTest } from "@/__tests__/e2e/fixtures/flows/environment/publicSharing";
import { test as repositoryIntegrationTest } from "@/__tests__/e2e/fixtures/flows/environment/repositoryIntegrations";
import { test as rorRegistryTest } from "@/__tests__/e2e/fixtures/flows/environment/rorRegistry";
import { test as userSessionTest } from "@/__tests__/e2e/fixtures/flows/sessions/userSessions";

export type { SelfServicePiActor, UserSession } from "@/__tests__/e2e/fixtures/flows/sessions/userSessions";
export const test = mergeTests(
  repositoryIntegrationTest,
  userSessionTest,
  publicSharingTest,
  ipWhitelistTest,
  fileSystemsTest,
  filestoreBackendsTest,
  rorRegistryTest,
);
