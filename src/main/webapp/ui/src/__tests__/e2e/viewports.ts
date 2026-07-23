import { devices } from "@playwright/test";

const { defaultBrowserType: _defaultBrowserType, ...MOBILE_DEVICE } = devices["Pixel 10 Pro XL"];

export { MOBILE_DEVICE };
