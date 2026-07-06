import { HttpResponse, http, type RequestHandler } from "msw";

/*
 * A static, far-future-expiry JWT for the browser-test OAuth token endpoint.
 * The client only decodes this token to read its `exp`; it never verifies the
 * signature, so a fixed token with exp = 4102444800 (year 2100) is always valid.
 */
export const OAUTH_TOKEN =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6NDEwMjQ0NDgwMCwicmVmcmVzaFRva2VuSGFzaCI6ImZlMTVmYTNkNWUzZDVhNDdlMzNlOWUzNDIyOWIxZWEyMzE0YWQ2ZTZmMTNmYTQyYWRkY2E0ZjE0Mzk1ODJhNGQifQ.dumm-signature_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

export const oauthTokenHandler = (): RequestHandler =>
  http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: OAUTH_TOKEN }));
