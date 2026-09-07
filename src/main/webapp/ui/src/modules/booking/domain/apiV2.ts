export function bookingApiV2Headers(token: string, initial?: HeadersInit): Headers {
  const headers = new Headers(initial);
  headers.set("Authorization", `Bearer ${token}`);
  headers.set("X-Requested-With", "XMLHttpRequest");
  return headers;
}

export function bookingApiV2JsonHeaders(token: string, initial?: HeadersInit): Headers {
  const headers = bookingApiV2Headers(token, initial);
  headers.set("Content-Type", "application/json");
  return headers;
}
