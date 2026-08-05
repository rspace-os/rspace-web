import { env } from "@/__tests__/e2e/env";
import type {
  ApiIgsnSettingsUpdate,
  ApiSysadminGroup,
  ApiSysadminGroupCreateRequest,
  ApiSysadminUser,
  ApiSysadminUserCreateRequest,
} from "../models/sysadmin";
import { BaseApiClient } from "./BaseApiClient";

export class SysadminClient extends BaseApiClient {
  readonly createdUserIds: number[] = [];

  async createUser(user: ApiSysadminUserCreateRequest): Promise<ApiSysadminUser> {
    env.assertGlobalMutationsAllowed("createUser");
    const created = await this.requestJson<ApiSysadminUser>("post", "/api/v1/sysadmin/users", {
      data: user,
      action: "createSysadminUser",
    });
    this.createdUserIds.push(created.id);
    return created;
  }

  async disableUser(id: number): Promise<void> {
    await this.requestVoid("put", `/api/v1/sysadmin/users/${id}/disable`, { action: "disableSysadminUser" });
  }

  async createGroup(group: ApiSysadminGroupCreateRequest): Promise<ApiSysadminGroup> {
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
}
