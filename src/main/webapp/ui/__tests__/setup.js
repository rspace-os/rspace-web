import { enableFetchMocks } from "jest-fetch-mock";
enableFetchMocks();

/*
 * Polyfill for TextEncoder and TextDecoder in Jest tests.
 *
 * These classes are available in browsers but not in Node.js by default.
 * Some dependencies (such as MUI, emotion, or others) may require TextEncoder/TextDecoder
 * for encoding/decoding text, even in test environments.
 * This ensures tests that rely on these APIs do not fail with "ReferenceError: TextEncoder is not defined".
 */
import { TextEncoder, TextDecoder } from "util";
global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;

import { BroadcastChannel } from "node:worker_threads";

global.BroadcastChannel = BroadcastChannel;

import { ReadableStream, WritableStream } from "node:stream/web";
global.ReadableStream = ReadableStream;
global.WritableStream = WritableStream;
