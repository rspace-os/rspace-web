export function apiV2Headers(token: string, initial?: HeadersInit): Headers {
  const headers = new Headers(initial);
  headers.set("Authorization", `Bearer ${token}`);
  headers.set("X-Requested-With", "XMLHttpRequest");
  return headers;
}

export function apiV2JsonHeaders(token: string, initial?: HeadersInit): Headers {
  const headers = apiV2Headers(token, initial);
  headers.set("Content-Type", "application/json");
  return headers;
}
