import axios from "@/common/axios";
import { action, observable, makeObservable } from "mobx";
import InvApiService from "../../common/InvApiService";
import ElnApiService from "../../common/ElnApiService";
import JwtService from "../../common/JwtService";
import type { RootStore } from "./RootStore";
import { mkAlert } from "../contexts/Alert";
import { getErrorMessage } from "../../util/error";

/* The public view is for a document made accessible to non RSpace users, List Of Materials access initialises AuthStore */
const publicView = document.getElementById("public_document_view") !== null;

export type IntegrationState = "true" | "false";
export type DataCiteServerUrl =
  | "https://api.datacite.org"
  | "https://api.test.datacite.org";

export type SystemSettings = {
  datacite: {
    enabled: IntegrationState;
    serverUrl: DataCiteServerUrl;
    username: string;
    password: string;
    repositoryPrefix: string;
  };
};

export default class AuthStore {
  rootStore: RootStore;
  isAuthenticated: boolean = false;
  isSynchronizing: boolean = true;
  isSigningOut: boolean = false;
  timeoutId: null | NodeJS.Timeout = null;
  systemSettings: SystemSettings | undefined;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      isAuthenticated: observable,
      isSynchronizing: observable,
      isSigningOut: observable,
      systemSettings: observable,
      authenticate: action,
      signOut: action,
      synchronizeWithSessionStorage: action,
      getSystemSettings: action,
      setSystemSettings: action,
    });
    this.rootStore = rootStore;
  }

  authenticate(): Promise<void> {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    this.isAuthenticated = false;
    const oAuthUrl =
      (publicView ? "/public/publicView" : "") +
      "/userform/ajax/inventoryOauthToken";
    return axios
      .get<{ data: string }>(oAuthUrl)
      .then(
        action((response) => {
          const token = response.data.data;

          if (typeof token === "undefined") {
            // Usually this means that backend returned login.html in response.data
            window.location.href = "/login";
            return Promise.resolve();
          }
          JwtService.saveToken(token);
          InvApiService.setAuthorizationHeader();
          ElnApiService.setAuthorizationHeader();
          this.isAuthenticated = true;
          this.isSynchronizing = false;

          // Reauthenticate few minutes before token expiry
          this.timeoutId = setTimeout(
            () => void this.authenticate(),
            JwtService.secondsToExpiry(token) * 1000
          );
        })
      )
      .then(() => {})
      .catch(() => {
        // @ts-expect-error I can update the location by assigning a string to it
        window.location = "/login";
      }) as Promise<void>;
  }

  signOut() {
    this.isSigningOut = true;
    this.isAuthenticated = false;
    JwtService.destroyToken();
  }

  static async getStatus() {
    await ElnApiService.get<void>("status");
  }

  synchronizeWithSessionStorage(): Promise<void> {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    this.isSynchronizing = true;
    const token = JwtService.getToken();

    if (token && !JwtService.isExpiringSoon(token)) {
      this.isAuthenticated = true;
      InvApiService.setAuthorizationHeader();
      ElnApiService.setAuthorizationHeader();
      this.isSynchronizing = false;

      // Reauthenticate few minutes before token expiry
      this.timeoutId = setTimeout(
        () => void this.authenticate(),
        JwtService.secondsToExpiry(token) * 1000
      );
      return Promise.resolve();
    }

    return this.authenticate();
  }

  setSystemSettings(settings: SystemSettings): void {
    this.systemSettings = settings;
  }

  async getSystemSettings(): Promise<void> {
    try {
      const { data } = await InvApiService.get<SystemSettings>(
        "system/settings",
        ""
      );
      this.setSystemSettings(data);
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not get System Settings.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      console.error(`Error fetching System Settings`, error);
      throw error;
    }
  }

  async updateSystemSettings<SettingFor extends keyof SystemSettings>(
    settingFor: SettingFor,
    newSettings: SystemSettings[SettingFor]
  ): Promise<void> {
    try {
      await InvApiService.put<void>("system/settings", {
        [settingFor]: newSettings,
      });
      this.rootStore.uiStore.addAlert(
        mkAlert({
          message: `System Settings have been updated.`,
          variant: "success",
        })
      );
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not update System Settings.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        })
      );
      console.error(`Error updating system settings`, error);
      throw error;
    }
  }
}
