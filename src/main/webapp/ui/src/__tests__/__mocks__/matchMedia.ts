/*
 * jest-dom does not define matchMedia so we have to define a mock that can be
 * used in tests that depend on components that make use of the browser API.
 * This code is taken from the Jest docs:
 * https://jestjs.io/docs/manual-mocks#mocking-methods-which-are-not-implemented-in-jsdom
 */
import { mockMatchMedia } from "@/__tests__/helpers/mockMatchMedia";

mockMatchMedia();
