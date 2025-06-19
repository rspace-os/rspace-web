/*
 * Polyfill for TextEncoder and TextDecoder in Jest tests.
 *
 * These classes are available in browsers but not in Node.js by default.
 * Some dependencies (such as MUI, emotion, or others) may require TextEncoder/TextDecoder
 * for encoding/decoding text, even in test environments.
 * This ensures tests that rely on these APIs do not fail with "ReferenceError: TextEncoder is not defined".
 */
const { TextEncoder, TextDecoder } = require("util");
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;
