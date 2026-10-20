import { setupServer } from "msw/node";
import { oauthTokenHandler } from "./mocks/oauthTokenMocks";

/*
 * Authenticated pages mint REST API v2 tokens, so answer that request by default
 * (initial handlers survive `resetHandlers()`); suites can still override it.
 */
export const server = setupServer(oauthTokenHandler(true));
