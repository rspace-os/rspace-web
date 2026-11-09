import { action, makeObservable, observable } from "mobx";
import axios from "@/common/axios";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import i18n from "@/modules/common/i18n";
import { isPublicDocumentView, mintUiToken, tokenRefreshDelayMs } from "@/modules/common/utils/auth";
import ElnApiService from "../../common/ElnApiService";
import InvApiService from "../../common/InvApiService";
import JwtService from "../../common/JwtService";
import { getErrorMessage } from "../../util/error";
import { mkAlert } from "../contexts/Alert";
import type { RootStore } from "./RootStore";
import {
  type ApiInventorySystemSettings,
  type B2InstSettings,
  type DataciteSettings,
  dataciteSettingsToIgsnPayload,
  type IdentifierSettings,
  pidinstB2InstSettingsToPayload,
  pidinstDataciteSettingsToPayload,
  type SystemSettings,
  systemSettingsFromApiResponse,
} from "./systemSettingsMapping";

export type {
  B2InstSettings,
  DataCiteServerUrl,
  DataciteSettings,
  IntegrationState,
  SystemSettings,
} from "./systemSettingsMapping";

export default class AuthStore {
  rootStore: RootStore;
  isAuthenticated: boolean = false;
  isSynchronizing: boolean = true;
  isSigningOut: boolean = false;
  timeoutId: null | NodeJS.Timeout = null;
  private refreshFailures = 0;
  systemSettings: SystemSettings | undefined;
  pidinstEnabled: boolean = false;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      isAuthenticated: observable,
      isSynchronizing: observable,
      isSigningOut: observable,
      systemSettings: observable,
      pidinstEnabled: observable,
      authenticate: action,
      signOut: action,
      refreshAuthentication: action,
      getSystemSettings: action,
      setSystemSettings: action,
      fetchPidinstEnabled: action,
    });
    this.rootStore = rootStore;
  }

  authenticate(): Promise<void> {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    this.timeoutId = null;
    // Keep Inventory mounted while refreshing; invalidate only after a rejected or expired refresh.
    const tokenRequest = isPublicDocumentView()
      ? axios
          .get<{ data: string }>("/public/publicView/userform/ajax/inventoryOauthToken")
          .then((response) => response.data.data)
      : mintUiToken();
    return tokenRequest
      .then(
        action((token) => {
          if (typeof token === "undefined") {
            // Usually this means that backend returned login.html in response.data
            window.location.href = "/login";
            return Promise.resolve();
          }
          JwtService.saveToken(token);
          InvApiService.setAuthorizationHeader();
          ElnApiService.setAuthorizationHeader();
          this.refreshFailures = 0;
          this.isAuthenticated = true;
          this.isSynchronizing = false;
          this.scheduleRefresh(tokenRefreshDelayMs(token));
        }),
      )
      .then(() => {})
      .catch((error: unknown) => {
        this.isSynchronizing = false;
        if (
          (error instanceof ApiV2ProblemError && error.status === 401) ||
          (axios.isAxiosError(error) && error.response?.status === 401)
        ) {
          this.isAuthenticated = false;
          JwtService.destroyToken();
          window.location.href = "/login";
          return;
        }
        const storedToken = JwtService.getToken();
        const millisecondsToExpiry = storedToken ? JwtService.secondsToExpiry(storedToken) * 1000 : 0;
        if (millisecondsToExpiry > 0) {
          const backoffMs = 1000 * 2 ** Math.min(this.refreshFailures++, 5);
          this.scheduleRefresh(Math.min(backoffMs, millisecondsToExpiry));
        } else {
          this.isAuthenticated = false;
        }
        throw error;
      });
  }

  private scheduleRefresh(delay: number) {
    if (!Number.isFinite(delay)) return;
    // authenticate schedules recovery before rejecting; timer callers must consume that rejection.
    this.timeoutId = setTimeout(() => void this.authenticate().catch(() => undefined), delay);
  }

  signOut() {
    if (this.timeoutId) clearTimeout(this.timeoutId);
    this.isSigningOut = true;
    this.isAuthenticated = false;
    JwtService.destroyToken();
  }

  static async getStatus() {
    await ElnApiService.get<void>("status");
  }

  refreshAuthentication(): Promise<void> {
    this.isSynchronizing = true;

    return this.authenticate();
  }

  setSystemSettings(settings: SystemSettings): void {
    this.systemSettings = settings;
  }

  async fetchPidinstEnabled(): Promise<void> {
    try {
      const { data } = await InvApiService.get<boolean>("identifiers/pidinstEnabled", "");
      this.pidinstEnabled = data;
    } catch {
      // Non-critical — leave pidinstEnabled as false if the call fails
    }
  }

  async getSystemSettings(): Promise<void> {
    try {
      const { data } = await InvApiService.get<ApiInventorySystemSettings>("system/settings", "");
      // the endpoint now returns identifiersSettings keyed by type; the dialog uses the IGSN entry
      this.setSystemSettings(systemSettingsFromApiResponse(data));
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:settings.alerts.fetchFailed"),
          message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
          variant: "error",
        }),
      );
      console.error(`Error fetching System Settings`, error);
      throw error;
    }
  }

  async updateSystemSettings<SettingFor extends keyof SystemSettings>(
    settingFor: SettingFor,
    newSettings: SystemSettings[SettingFor],
  ): Promise<void> {
    try {
      let payload: IdentifierSettings;
      if (settingFor === "igsnDatacite") {
        payload = dataciteSettingsToIgsnPayload(newSettings as DataciteSettings);
      } else if (settingFor === "pidinstDatacite") {
        payload = pidinstDataciteSettingsToPayload(newSettings as DataciteSettings);
      } else {
        payload = pidinstB2InstSettingsToPayload(newSettings as B2InstSettings);
      }
      await InvApiService.put<void>("system/settings", payload);
      this.rootStore.uiStore.addAlert(
        mkAlert({
          message: i18n.t("inventory:settings.alerts.updateSuccess"),
          variant: "success",
        }),
      );
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: i18n.t("inventory:settings.alerts.updateFailed"),
          message: getErrorMessage(error, i18n.t("inventory:errors.unknownReason")),
          variant: "error",
        }),
      );
      console.error("Error updating system settings", getErrorMessage(error, ""));
      throw error;
    }
  }
}
