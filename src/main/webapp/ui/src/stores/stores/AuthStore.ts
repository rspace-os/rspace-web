import { action, makeObservable, observable } from "mobx";
import axios from "@/common/axios";
import ElnApiService from "../../common/ElnApiService";
import InvApiService from "../../common/InvApiService";
import JwtService from "../../common/JwtService";
import { getErrorMessage } from "../../util/error";
import { mkAlert } from "../contexts/Alert";
import type { RootStore } from "./RootStore";
import {
  type ApiInventorySystemSettings,
  dataciteSettingsToIgsnPayload,
  type SystemSettings,
  systemSettingsFromApiResponse,
} from "./systemSettingsMapping";

/* The public view is for a document made accessible to non RSpace users, List Of Materials access initialises AuthStore */
const publicView = document.getElementById("public_document_view") !== null;

/*
 * A bare axios instance for fetching the OAuth token. Unlike the shared instance it carries no 401
 * response interceptor: a 401 on the token endpoint means the RSpace session itself has expired, so
 * the request must simply reject and let the caller unwind to /login. Routing it through the shared
 * interceptor instead would make it try to recover a 401 by fetching a new token - i.e. re-enter
 * this very request - which, with the shared in-flight refreshPromise, deadlocks.
 */
const tokenClient = axios.create();

export type {
  DataCiteServerUrl,
  IntegrationState,
  SystemSettings,
} from "./systemSettingsMapping";

export default class AuthStore {
  rootStore: RootStore;
  isAuthenticated: boolean = false;
  isSynchronizing: boolean = true;
  isSigningOut: boolean = false;
  timeoutId: null | NodeJS.Timeout = null;
  systemSettings: SystemSettings | undefined;
  /** Shared in-flight token refresh, so concurrent 401s trigger a single renewal. */
  refreshPromise: Promise<void> | null = null;

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
    const oAuthUrl = `${publicView ? "/public/publicView" : ""}/userform/ajax/inventoryOauthToken`;
    return tokenClient
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

          // Renew shortly before the token expires.
          this.timeoutId = setTimeout(() => void this.refreshToken(), this.msUntilRenewal(token));
        }),
      )
      .then(() => {})
      .catch(() => {
        // @ts-expect-error I can update the location by assigning a string to it
        window.location = "/login";
      });
  }

  /**
   * Obtains a fresh OAuth token without flipping isAuthenticated, so the caller can retry a failed
   * request while the app stays mounted (unlike authenticate(), which remounts it). Used by the 401
   * interceptor and the pre-expiry timer. Concurrent callers share a single in-flight refresh; a
   * genuine failure to obtain a token drops local auth state and unwinds to /login.
   */
  refreshToken(): Promise<void> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
    const oAuthUrl = `${publicView ? "/public/publicView" : ""}/userform/ajax/inventoryOauthToken`;
    this.refreshPromise = tokenClient
      .get<{ data: string }>(oAuthUrl)
      .then(
        action((response) => {
          // A sign-out may have completed while this refresh was in flight; don't revive the session.
          if (this.isSigningOut) {
            return;
          }
          const token = response.data.data;

          if (typeof token === "undefined") {
            // Backend returned login.html rather than a token: the session is gone. Drop local auth
            // state before redirecting so the 401 interceptor stops retrying with the stale token.
            this.endSession();
            return;
          }
          JwtService.saveToken(token);
          InvApiService.setAuthorizationHeader();
          ElnApiService.setAuthorizationHeader();
          this.isAuthenticated = true;
          this.isSynchronizing = false;

          // Renew shortly before the token expires.
          this.timeoutId = setTimeout(() => void this.refreshToken(), this.msUntilRenewal(token));
        }),
      )
      .then(() => {})
      .catch(
        action(() => {
          // The refresh itself failed; drop local auth state so callers stop retrying, then redirect.
          this.endSession();
        }),
      )
      .finally(() => {
        this.refreshPromise = null;
      });
    return this.refreshPromise;
  }

  /** Drops local auth state and sends the user to the login page. */
  private endSession(): void {
    this.isAuthenticated = false;
    JwtService.destroyToken();
    window.location.href = "/login";
  }

  /**
   * Milliseconds until the token should be renewed: a short buffer before actual expiry so the
   * renewal happens ahead of time rather than at the boundary (clamped to >= 0).
   */
  private msUntilRenewal(token: string): number {
    const bufferSeconds = 30;
    return Math.max(0, (JwtService.secondsToExpiry(token) - bufferSeconds) * 1000);
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

      // Renew shortly before the token expires.
      this.timeoutId = setTimeout(() => void this.refreshToken(), this.msUntilRenewal(token));
      return Promise.resolve();
    }

    return this.authenticate();
  }

  setSystemSettings(settings: SystemSettings): void {
    this.systemSettings = settings;
  }

  async getSystemSettings(): Promise<void> {
    try {
      const { data } = await InvApiService.get<ApiInventorySystemSettings>("system/settings", "");
      // the endpoint now returns identifiersSettings keyed by type; the dialog uses the IGSN entry
      this.setSystemSettings(systemSettingsFromApiResponse(data));
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not get System Settings.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        }),
      );
      console.error(`Error fetching System Settings`, error);
      throw error;
    }
  }

  async updateSystemSettings<SettingFor extends keyof SystemSettings>(
    _settingFor: SettingFor,
    newSettings: SystemSettings[SettingFor],
  ): Promise<void> {
    try {
      // PUT now takes a single identifier-settings object routed by `provider`. The dialog only
      // configures IGSN, so send the IGSN payload (provider = IGSN_DATACITE).
      // This is needed because the payload has changed but the UI has not, this adapt the old UI
      // to work with the new payload.
      // The UI strategic solution that will handle both configurations
      // (PDINST Datacite, PDINST b2inst and IGSN datacite)
      // will be handled by the jira ticket https://researchspace.atlassian.net/browse/RSDEV-1180
      await InvApiService.put<void>("system/settings", dataciteSettingsToIgsnPayload(newSettings));
      this.rootStore.uiStore.addAlert(
        mkAlert({
          message: `System Settings have been updated.`,
          variant: "success",
        }),
      );
    } catch (error) {
      this.rootStore.uiStore.addAlert(
        mkAlert({
          title: `Could not update System Settings.`,
          message: getErrorMessage(error, "Unknown reason."),
          variant: "error",
        }),
      );
      console.error(`Error updating system settings`, error);
      throw error;
    }
  }
}
