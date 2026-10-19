/**
 * A secret as the server exchanges it (RSDEV-1525): `null` is a stored secret,
 * which is never returned to the browser, and posting `null` back keeps it;
 * `""` means there is none; any other string is a new secret being entered.
 */
export type Secret = string | null;

/** What a secret field should hold once its value has been saved: stored (null), or "" if cleared. */
export function secretAfterSave(value: Secret): Secret {
  return value === "" ? "" : null;
}
