import type MockAdapter from "axios-mock-adapter";

export type VisibleTabs = {
  inventory: boolean;
  myLabGroups: boolean;
  published: boolean;
  system: boolean;
};

const defaultVisibleTabs: VisibleTabs = {
  inventory: true,
  myLabGroups: true,
  published: true,
  system: true,
};

/*
 * The content of this JWT is not asserted by any test, only the fact that the
 * oauth-token endpoint returned a parseable token. Keep it as a stable opaque
 * string so individual tests do not each carry their own copy.
 */
const STUB_JWT =
  "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MzQzNDI5NTYsImV4cCI6MTczNDM0NjU1NiwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.HCKre3g_P1wmGrrrnQncvFeT9pAePFSc4UPuyP5oehI";

/**
 * Default body for `/api/v1/userDetails/uiNavigationData`. Exported so tests
 * that mock that endpoint themselves can reuse the standard shape.
 */
export function uiNavigationData(visibleTabs: Partial<VisibleTabs> = {}) {
  return {
    bannerImgSrc: "/public/banner",
    visibleTabs: { ...defaultVisibleTabs, ...visibleTabs },
    userDetails: {
      username: "user1a",
      fullName: "user user",
      email: "user@user.com",
      orcidId: null,
      orcidAvailable: false,
      profileImgSrc: null,
      lastSession: "2025-03-25T15:45:57.000Z",
    },
    operatedAs: false,
    nextMaintenance: null,
  };
}

/**
 * Stubs the bootstrap axios calls the rspace app chrome (AppBar + HelpDocs)
 * fires on mount: oauth token, livechat properties, ui navigation data, and the
 * banner image. Pass `visibleTabs` to override individual tab visibility.
 *
 * If the test also needs to suppress unhandled-rejection noise from any other
 * bootstrap calls, register `mockAxios.onAny().reply(200, {})` AFTER your own
 * test-specific handlers. `axios-mock-adapter` matches handlers in registration
 * order, so a catch-all registered first would intercept your specific stubs.
 */
export function stubAppChrome(mockAxios: MockAdapter, options: { visibleTabs?: Partial<VisibleTabs> } = {}): void {
  mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, { data: STUB_JWT });
  mockAxios.onGet("/session/ajax/livechatProperties").reply(200, { livechatEnabled: false });
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, uiNavigationData(options.visibleTabs));
  mockAxios.onGet("/public/banner").reply(200, "fake image data");
}
