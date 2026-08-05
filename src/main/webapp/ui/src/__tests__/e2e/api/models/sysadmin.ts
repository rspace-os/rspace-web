export interface ApiSysadminUserCreateRequest {
  username: string;
  password: string;
  email: string;
  firstName: string;
  lastName: string;
  role: "ROLE_USER" | "ROLE_PI" | "ROLE_ADMIN" | "ROLE_SYSADMIN";
  apiKey?: string;
  affiliation?: string;
  createGroupForPi?: boolean;
}

export interface ApiSysadminUser {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  hasPiRole: boolean;
  hasSysAdminRole: boolean;
}

export type GroupMemberRole = "DEFAULT" | "RS_LAB_ADMIN" | "PI" | "GROUP_OWNER";

export interface ApiSysadminGroupCreateRequest {
  displayName: string;
  type: "LAB_GROUP";
  users: Array<{ username: string; roleInGroup: GroupMemberRole }>;
}

export interface ApiSysadminGroup {
  id: number;
  globalId: string;
  name: string;
  type: string;
  members: Array<{ id: number; username: string; role: string }>;
}

export interface ApiIgsnSettingsUpdate {
  provider: "IGSN_DATACITE";
  enabled: "true";
  serverUrl: string;
  username: string;
  password: string;
  repositoryPrefix: string;
}
