import { env } from "@/__tests__/e2e/env";
import type {
  ApiIgsnSettingsUpdate,
  ApiPidinstSettingsUpdate,
  ApiSysadminGroup,
  ApiSysadminGroupCreateRequest,
  ApiSysadminUser,
  ApiSysadminUserCreateRequest,
} from "../models/sysadmin";
import { BaseApiClient } from "./BaseApiClient";

type ManagedUserState = "active" | "released";

export class SysadminClient extends BaseApiClient {
  private readonly managedUsers = new Map<string, { id: number; state: ManagedUserState }>();

  async createUser(user: ApiSysadminUserCreateRequest): Promise<ApiSysadminUser> {
    env.assertGlobalMutationsAllowed("createUser");
    const created = await this.requestJson<ApiSysadminUser>("post", "/api/v1/sysadmin/users", {
      data: user,
      action: "createSysadminUser",
    });
    this.managedUsers.set(user.username, { id: created.id, state: "active" });
    return created;
  }

  /** Call once a scenario has itself removed a tracked user, so teardown skips it. */
  releaseUser(username: string): void {
    const entry = this.managedUsers.get(username);
    if (!entry) {
      throw new Error(`releaseUser("${username}") called for a user this client never created.`);
    }
    entry.state = "released";
  }

  /** Ids still owned by this client and needing teardown cleanup, most-recently-created first. */
  get pendingCleanup(): Array<{ username: string; id: number }> {
    return [...this.managedUsers.entries()]
      .filter(([, entry]) => entry.state === "active")
      .map(([username, entry]) => ({ username, id: entry.id }))
      .reverse();
  }

  async disableUser(id: number): Promise<void> {
    await this.requestVoid("put", `/api/v1/sysadmin/users/${id}/disable`, { action: "disableSysadminUser" });
  }

  async createGroup(group: ApiSysadminGroupCreateRequest): Promise<ApiSysadminGroup> {
    env.assertGlobalMutationsAllowed("createGroup");
    return this.requestJson("post", "/api/v1/sysadmin/groups", { data: group, action: "createSysadminGroup" });
  }

  async configureIgsn(settings: ApiIgsnSettingsUpdate): Promise<void> {
    env.assertGlobalMutationsAllowed("configureIgsn");
    await this.requestVoid("put", "/api/inventory/v1/system/settings", {
      data: settings,
      action: "configureIgsn",
    });
  }

  async testIgsnConnection(): Promise<boolean> {
    return this.requestJson("get", "/api/inventory/v1/identifiers/testIgsnConnection", {
      action: "testIgsnConnection",
    });
  }

  async configurePidinst(settings: ApiPidinstSettingsUpdate): Promise<void> {
    env.assertGlobalMutationsAllowed("configurePidinst");
    await this.requestVoid("put", "/api/inventory/v1/system/settings", {
      data: settings,
      action: "configurePidinst",
    });
  }

  async testPidinstConnection(): Promise<boolean> {
    return this.requestJson("get", "/api/inventory/v1/identifiers/testPidinstConnection", {
      action: "testPidinstConnection",
    });
  }
}
