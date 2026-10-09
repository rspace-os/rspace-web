import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { alphaNumericUnique, DYNAMIC_USER_PASSWORD, E2E_AFFILIATION } from "@/__tests__/e2e/testData";

export type DynamicUser = { username: string; password: string; fullName: string; apiKey: string; email: string };

// Shared across specs and fixtures
export async function createDynamicUser(
  clientSysadmin: SysadminClient,
  role: "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN",
  namePrefix: string,
  lastName: string = namePrefix,
): Promise<DynamicUser> {
  const username = alphaNumericUnique(namePrefix);
  const apiKey = alphaNumericUnique("e2eApiKey");
  const email = `${username}@example.com`;
  await clientSysadmin.createUser({
    username,
    password: DYNAMIC_USER_PASSWORD,
    email,
    firstName: "E2E",
    lastName,
    role,
    apiKey,
    // Mandatory on community (deployment.cloud=true) servers.
    affiliation: E2E_AFFILIATION,
  });
  return { username, password: DYNAMIC_USER_PASSWORD, fullName: `E2E ${lastName}`, apiKey, email };
}
