/*
 * jsdom in Vitest does not define matchMedia so we have to define a mock that can be
 * used in tests that depend on components that make use of the browser API.
 * This mock follows the same approach recommended for browser APIs that jsdom
 * does not implement in test environments.
 */
import { mockMatchMedia } from "@/__tests__/helpers/mockMatchMedia";

mockMatchMedia();
