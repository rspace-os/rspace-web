import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import type { SystemUsersPage } from "@/__tests__/e2e/pageObjects/system/users/SystemUsersPage";

// Shared across specs. Pairs with createDynamicUser: deletes the user via the sysadmin UI and
// releases it from clientSysadmin's cleanup tracking in one step, so teardown never retries a
// disable against an account the scenario already removed.
export async function deleteDynamicUser(
  users: SystemUsersPage,
  clientSysadmin: SysadminClient,
  username: string,
): Promise<void> {
  await users.deleteUser(username);
  clientSysadmin.releaseUser(username);
}
