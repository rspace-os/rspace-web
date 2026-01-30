import React from "react";
import { vi, expect, afterEach, afterAll } from "vitest";
import createFetchMock from "vitest-fetch-mock";
import * as matchers from "@testing-library/jest-dom/matchers";
import {
  silenceConsole,
  silenceProcessOutput,
} from "@/__tests__/helpers/silenceConsole";
const fetchMocker = createFetchMock(vi);

// sets globalThis.fetch and globalThis.fetchMock to our mocked version
fetchMocker.enableMocks();
expect.extend(matchers);
globalThis.expect = expect;
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
globalThis.afterEach = afterEach;

const restoreConsole = silenceConsole(["error"], [
  "Could not fetch set of users in the same group as current user",
]);
const restoreStderr = silenceProcessOutput(["stderr"], ["AggregateError"]);
afterAll(() => {
  restoreConsole();
  restoreStderr();
});

const TransitionMock = React.forwardRef(({ in: inProp, children }, ref) => {
  if (inProp === false) {
    return null;
  }
  if (React.isValidElement(children)) {
    return React.cloneElement(children, { ref });
  }
  return children ?? null;
});
TransitionMock.displayName = "TransitionMock";

vi.mock("@mui/material/Grow", () => ({
  __esModule: true,
  default: TransitionMock,
}));

vi.mock("@mui/material/Fade", () => ({
  __esModule: true,
  default: TransitionMock,
}));

vi.mock("react-transition-group", () => ({
  Transition: ({ children, in: inProp }) =>
    typeof children === "function"
      ? children(inProp ? "entered" : "exited", {})
      : children ?? null,
  CSSTransition: ({ children }) => children ?? null,
  TransitionGroup: ({ children }) => children ?? null,
}));

vi.mock("@/hooks/api/useUiPreference", async () => {
  const actual = await vi.importActual("@/hooks/api/useUiPreference");
  return {
    ...actual,
    UiPreferences: ({ children }) => children,
    default: (_preference, opts) => [opts.defaultValue, vi.fn()],
  };
});

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({
    getToken: async () => "token",
  }),
}));

vi.mock("@/hooks/api/useWhoAmI", () => ({
  default: () => ({
    tag: "success",
    value: {
      id: 1,
      username: "test",
      firstName: "Test",
      lastName: "User",
      hasPiRole: false,
      hasSysAdminRole: false,
      email: "test@example.com",
      bench: null,
      workbenchId: null,
      getBench: () =>
        Promise.reject(
          new Error("Not implemented by this Person implementation"),
        ),
      isCurrentUser: true,
      fullName: "Test User",
      label: "Test User (test)",
    },
  }),
}));

vi.mock("@/hooks/websockets/useWebSocketNotifications", () => ({
  default: () => ({
    notificationCount: 0,
    messageCount: 0,
    specialMessageCount: 0,
  }),
}));

if (typeof HTMLImageElement !== "undefined") {
  Object.defineProperty(HTMLImageElement.prototype, "width", {
    configurable: true,
    get() {
      return this.__mockWidth ?? 1;
    },
    set(value) {
      this.__mockWidth = value;
    },
  });
  Object.defineProperty(HTMLImageElement.prototype, "height", {
    configurable: true,
    get() {
      return this.__mockHeight ?? 1;
    },
    set(value) {
      this.__mockHeight = value;
    },
  });
  Object.defineProperty(HTMLImageElement.prototype, "complete", {
    configurable: true,
    get() {
      return true;
    },
  });
  Object.defineProperty(HTMLImageElement.prototype, "naturalWidth", {
    configurable: true,
    get() {
      return this.width || 1;
    },
  });
  Object.defineProperty(HTMLImageElement.prototype, "naturalHeight", {
    configurable: true,
    get() {
      return this.height || 1;
    },
  });
  const originalDescriptor = Object.getOwnPropertyDescriptor(
    HTMLImageElement.prototype,
    "src",
  );
  Object.defineProperty(HTMLImageElement.prototype, "src", {
    configurable: true,
    get() {
      return originalDescriptor?.get ? originalDescriptor.get.call(this) : "";
    },
    set(_value) {
      queueMicrotask(() => {
        this.dispatchEvent(new Event("load"));
      });
    },
  });
}

if (typeof HTMLCanvasElement !== "undefined") {
  HTMLCanvasElement.prototype.getContext = function () {
    return {
      canvas: this,
      save: () => {},
      restore: () => {},
      drawImage: () => {},
      clearRect: () => {},
      translate: () => {},
      rotate: () => {},
      scale: () => {},
      setTransform: () => {},
      measureText: (text) => ({ width: String(text).length }),
      beginPath: () => {},
      rect: () => {},
      clip: () => {},
      fillRect: () => {},
      getImageData: () => ({ data: [] }),
      putImageData: () => {},
    };
  };
  HTMLCanvasElement.prototype.toDataURL = function () {
    return "data:image/png;base64,";
  };
  HTMLCanvasElement.prototype.toBlob = function (callback) {
    callback(new Blob([], { type: "image/png" }));
  };
}
/*
 * Polyfill for TextEncoder and TextDecoder in Jest tests.
 *
 * These classes are available in browsers but not in Node.js by default.
 * Some dependencies (such as MUI, emotion, or others) may require TextEncoder/TextDecoder
 * for encoding/decoding text, even in test environments.
 * This ensures tests that rely on these APIs do not fail with "ReferenceError: TextEncoder is not defined".
 */
import { TextEncoder, TextDecoder } from "util";
if (typeof globalThis.TextEncoder !== "function") {
  globalThis.TextEncoder = TextEncoder;
}
if (typeof globalThis.TextDecoder !== "function") {
  globalThis.TextDecoder = TextDecoder;
}
