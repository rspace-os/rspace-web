/*
 * Whether `new java.net.URI(value)` parses a string, which is how the server
 * checks a URI field (`InventoryUriField.validate`). Neither WHATWG `URL` nor
 * RFC 3986 parsers agree with it: `URL` rejects relative references and
 * percent-encodes spaces, and `java.net.URI` follows RFC 2396 with its own
 * deviations. This is a port of `java.net.URI.Parser`, reduced to accept or
 * reject.
 */

const ALPHA = "A-Za-z";
const ALPHANUM = `${ALPHA}0-9`;
const UNRESERVED = `${ALPHANUM}\\-_.!~*'()`;
const RESERVED = ";/?:@&=+$,\\[\\]";

/*
 * A character outside US-ASCII is allowed wherever escapes are, unless it is a
 * control or space character to Java (`Character.isISOControl`,
 * `Character.isSpaceChar`). U+0080 is a control character.
 */
const OTHER =
  "\\u00A1-\\u167F\\u1681-\\u1FFF\\u200B-\\u2027\\u202A-\\u202E\\u2030-\\u205E\\u2060-\\u2FFF\\u3001-\\uFFFF";
const ESCAPED = "%[0-9A-Fa-f]{2}";

/** A run of `chars`, escapes and other characters. */
const escapable = (chars: string) => `(?:[${chars}${OTHER}]|${ESCAPED})*`;

const URIC = escapable(UNRESERVED + RESERVED);
const PATH = escapable(`${UNRESERVED}:@&=+$,;/`);
const USERINFO = escapable(`${UNRESERVED};:&=+$,`);
const REG_NAME = new RegExp(`^${escapable(`${UNRESERVED}$,;:@&=+`)}$`);

const anchored = (pattern: string) => new RegExp(`^${pattern}$`);
const URIC_ONLY = anchored(URIC);
const PATH_ONLY = anchored(PATH);
const USERINFO_ONLY = anchored(USERINFO);
const SCHEME = /^[A-Za-z][A-Za-z0-9+\-.]*$/;
// as `java.net.URI.Parser`'s `L_SCOPE_ID`
const SCOPE_ID = /^[A-Za-z0-9_.]+$/;

const MAX_PORT = 2147483647;

function isIPv4(value: string): boolean {
  const bytes = value.split(".");
  return bytes.length === 4 && bytes.every((b) => /^\d+$/.test(b) && Number(b) <= 255);
}

/** RFC 2373 text form, as `parseIPv6Reference` reads it. */
function isIPv6(value: string): boolean {
  const halves = value.split("::");
  if (halves.length > 2) return false;
  const groups = (half: string): Array<string> | null => {
    if (half === "") return [];
    const parts = half.split(":");
    const last = parts[parts.length - 1];
    const ipv4 = last.includes(".");
    if (ipv4 && !isIPv4(last)) return null;
    const hex = ipv4 ? parts.slice(0, -1) : parts;
    if (!hex.every((part) => /^[0-9A-Fa-f]{1,4}$/.test(part))) return null;
    return ipv4 ? [...hex, "", ""] : hex;
  };
  const [head, tail] = halves;
  const before = groups(head);
  if (before === null) return false;
  if (tail === undefined) return before.length === 8;
  // an IPv4 address may only end the address
  if (before.length > 0 && head.split(":").at(-1)?.includes(".")) return false;
  const after = groups(tail);
  if (after === null) return false;
  return before.length + after.length < 8;
}

/** `parseServer` with a bracketed host: the only form an authority holding '[' or ']' can take. */
function isServerWithIPv6(authority: string): boolean {
  const at = authority.indexOf("@");
  const userinfo = at >= 0 ? authority.slice(0, at) : "";
  if (!USERINFO_ONLY.test(userinfo)) return false;
  const server = /^\[([^\]]*)\](?::(\d*))?$/.exec(authority.slice(at + 1));
  if (!server) return false;
  const [, address, port = ""] = server;
  const scope = address.indexOf("%");
  if (scope >= 0 && !SCOPE_ID.test(address.slice(scope + 1))) return false;
  if (!isIPv6(scope >= 0 ? address.slice(0, scope) : address)) return false;
  return port === "" || Number(port) <= MAX_PORT;
}

/**
 * `parseAuthority`: a registry-based authority is any run of its characters,
 * so a server-based one only has to parse when it holds the brackets of an
 * IPv6 host.
 */
function isAuthority(authority: string): boolean {
  return REG_NAME.test(authority) || isServerWithIPv6(authority);
}

/**
 * `parseHierarchical`: an optional authority, a path, then an optional query.
 * An empty authority is allowed only before a path, query or fragment.
 */
function isHierarchical(part: string, fragmentFollows: boolean): boolean {
  let rest = part;
  if (rest.startsWith("//")) {
    const end = rest.slice(2).search(/[/?#]/);
    const authority = end < 0 ? rest.slice(2) : rest.slice(2, end + 2);
    rest = end < 0 ? "" : rest.slice(end + 2);
    if (authority === "" ? rest === "" && !fragmentFollows : !isAuthority(authority)) return false;
  }
  const query = rest.indexOf("?");
  if (query < 0) return PATH_ONLY.test(rest);
  return PATH_ONLY.test(rest.slice(0, query)) && URIC_ONLY.test(rest.slice(query + 1));
}

export function isJavaUri(value: string): boolean {
  const hash = value.indexOf("#");
  const beforeFragment = hash < 0 ? value : value.slice(0, hash);
  const fragmentFollows = hash >= 0;
  if (fragmentFollows && !URIC_ONLY.test(value.slice(hash + 1))) return false;

  // a scheme is whatever precedes the first ':', unless a '/', '?' or '#' comes first
  const colon = beforeFragment.search(/[:/?]/);
  if (colon < 0 || beforeFragment[colon] !== ":") return isHierarchical(beforeFragment, fragmentFollows);
  if (!SCHEME.test(beforeFragment.slice(0, colon))) return false;
  const schemeSpecific = beforeFragment.slice(colon + 1);
  if (schemeSpecific.startsWith("/")) return isHierarchical(schemeSpecific, fragmentFollows);
  return schemeSpecific !== "" && URIC_ONLY.test(schemeSpecific);
}
