import type { FlatNamespace, ParseKeys } from "i18next";
import i18n from "@/modules/common/i18n";

// i18next's own overloads infer Ret from a literal `Key`; a value typed as
// the full cross-namespace key union defeats that inference. This narrower
// view of the same function keeps the call trivial while `lazyT`'s own
// parameter type still validates the key against the real catalog.
const translate = i18n.t as (key: string) => string;

/**
 * Defers translation of a key until the returned thunk is called, resolving
 * through the current i18next language at that time. For contexts that build
 * static, module-level configuration (e.g. Valibot schemas) where a React
 * `useTranslation` hook isn't available and the message must still follow
 * language switches. Pass a namespace-qualified key (e.g. "common:foo.bar").
 */
export const lazyT =
  (key: ParseKeys<readonly FlatNamespace[]>): (() => string) =>
  () =>
    translate(key);
