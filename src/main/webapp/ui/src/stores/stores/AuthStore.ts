import { action, makeObservable, observable } from "mobx";
import axios from "@/common/axios";
import { ApiV2ProblemError } from "@/modules/common/api/v2/ApiV2ProblemError";
import { ApiV2IdentityChangedError } from "@/modules/common/api/v2/tokenProvider";
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

const MAX_TIMER_DELAY_MS = 2 ** 31 - 1;

function storedTokenValidityMs(): number {
  const token = JwtService.getToken();
  try {
    return token ? JwtService.secondsToExpiry(token) * 1000 : 0;
  } catch {
    return 0;
  }
}

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
    if (this.isSigningOut) return Promise.resolve();
    /*
     * Never clear isAuthenticated here, even on 401 or expiry: Router renders the
     * Inventory routes only while it is true, so clearing it would unmount the
     * page for every refresh, and on failure would empty the page behind the
     * "leave page?" prompt that the redirect to /login can raise.
     */
    // Abort a request still pending when the stored token expires, so the catch below redirects.
    // A retry that starts at or after expiry is aborted at once; a first request may still mint from the session.
    const request = new AbortController();
    const validityMs = storedTokenValidityMs();
    const expiryTimer =
      (validityMs > 0 || this.refreshFailures > 0) && validityMs <= MAX_TIMER_DELAY_MS
        ? setTimeout(() => request.abort(), Math.max(validityMs, 0))
        : undefined;
    // The public document view keeps its legacy token endpoint; every other page mints a REST API v2 UI token.
    const tokenRequest = isPublicDocumentView()
      ? axios
          .get<{ data: string }>("/public/publicView/userform/ajax/inventoryOauthToken", { signal: request.signal })
          .then((response) => response.data.data)
      : mintUiToken(request.signal);
    return tokenRequest
      .finally(() => clearTimeout(expiryTimer))
      .then(
        action((token: string | undefined) => {
          if (typeof token === "undefined") {
            // Usually this means that backend returned login.html in response.data
            window.location.href = "/login";
            return Promise.resolve();
          }
          if (this.isSigningOut) return;
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
      .catch(
        action((error: unknown) => {
          // The token provider reloads the page itself after an identity change; never send it to /login.
          if (error instanceof ApiV2IdentityChangedError) throw error;
          this.isSynchronizing = false;
          if (this.isSigningOut) return;
          const validityMs = storedTokenValidityMs();
          const status = axios.isAxiosError(error)
            ? error.response?.status
            : error instanceof ApiV2ProblemError
              ? error.status
              : undefined;
          if (status === 401 || !(validityMs > 0)) {
            JwtService.destroyToken();
            window.location.href = "/login";
            throw error;
          }
          // Network failures and other errors without an HTTP status retry too, while the token is usable.
          if (status === undefined || status === 429 || status >= 500) {
            if (!axios.isAxiosError(error) && !(error instanceof ApiV2ProblemError)) {
              console.error("Inventory token refresh failed", error);
            }
            const backoffMs = 1000 * 2 ** Math.min(this.refreshFailures++, 5);
            this.scheduleRefresh(Math.min(backoffMs, validityMs));
            this.isAuthenticated = true;
            InvApiService.setAuthorizationHeader();
            ElnApiService.setAuthorizationHeader();
            return;
          }
          window.location.href = "/login";
        }),
      );
  }

  private scheduleRefresh(delay: number) {
    this.timeoutId = setTimeout(() => void this.authenticate().catch(() => undefined), delay);
  }

  signOut() {
    if (this.timeoutId) clearTimeout(this.timeoutId);
    this.timeoutId = null;
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
