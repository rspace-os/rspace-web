import { TextEncoder, TextDecoder } from "node:util";
import { vi, expect, afterEach, afterAll } from "vitest";
import "@testing-library/jest-dom/vitest";
import createFetchMock from "vitest-fetch-mock";
import {
  silenceConsole,
  silenceProcessOutput,
} from "@/__tests__/helpers/silenceConsole";
import { setup, toBeAccessible } from "@sa11y/vitest";
import { cleanup } from "@testing-library/react";

setup();

expect.extend({ toBeAccessible });

const fetchMocker = createFetchMock(vi);

fetchMocker.enableMocks();
// @ts-expect-error Mocking
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => {
  cleanup();
});

const restoreConsole = silenceConsole(
  ["error"],
  ["Could not fetch set of users in the same group as current user"],
);
const restoreStderr = silenceProcessOutput(["stderr"], ["AggregateError"]);
afterAll(() => {
  restoreConsole();
  restoreStderr();
});

/*
 * Polyfill for TextEncoder and TextDecoder in Jest tests.
 *
 * These classes are available in browsers but not in Node.js by default.
 * Some dependencies (such as MUI, emotion, or others) may require TextEncoder/TextDecoder
 * for encoding/decoding text, even in test environments.
 * This ensures tests that rely on these APIs do not fail with "ReferenceError: TextEncoder is not defined".
 */
if (typeof globalThis.TextEncoder !== "function") {
  globalThis.TextEncoder = TextEncoder;
}
if (typeof globalThis.TextDecoder !== "function") {
  // @ts-expect-error Only needed for mocking
  globalThis.TextDecoder = TextDecoder;
}
