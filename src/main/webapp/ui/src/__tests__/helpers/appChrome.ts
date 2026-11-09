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
 * fires on mount: livechat properties, ui navigation data, and the
 * banner image. Pass `visibleTabs` to override individual tab visibility.
 *
 * If the test also needs to suppress unhandled-rejection noise from any other
 * bootstrap calls, register `mockAxios.onAny().reply(200, {})` AFTER your own
 * test-specific handlers. `axios-mock-adapter` matches handlers in registration
 * order, so a catch-all registered first would intercept your specific stubs.
 */
export function stubAppChrome(mockAxios: MockAdapter, options: { visibleTabs?: Partial<VisibleTabs> } = {}): void {
  mockAxios.onGet("/session/ajax/livechatProperties").reply(200, { livechatEnabled: false });
  mockAxios.onGet("/api/v1/userDetails/uiNavigationData").reply(200, uiNavigationData(options.visibleTabs));
  mockAxios.onGet("/public/banner").reply(200, "fake image data");
}
