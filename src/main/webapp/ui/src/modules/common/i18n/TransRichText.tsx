import Link from "@mui/material/Link";
import type React from "react";
import { createContext, useContext } from "react";
import { Trans } from "react-i18next";

/**
 * The translator-facing rich-text vocabulary: tag names whose *component*
 * differs between UI libraries (MUI today, Base UI later). A translation string
 * uses these tags (e.g. `<a href="/docs">open docs</a>`) and the active map
 * decides what they render as. Both library maps must satisfy this exact shape,
 * so the vocabulary cannot drift between libraries — adding a tag to one
 * without the other is a compile error.
 *
 * The link tag is `a` (not `link`): `link` is an HTML void element, so
 * react-i18next's HTML parser self-closes it and drops the inner text.
 *
 * Plain emphasis tags (`<strong>`, `<em>`, `<br>`, `<p>`) are intentionally NOT
 * here: they render identically as raw HTML in every library, so `Trans`
 * handles them for free and they need no swap.
 */
export type RichTextComponents = { a: React.ReactElement };

/** Default (MUI) vocabulary. */
const muiRichTextComponents: RichTextComponents = { a: <Link /> };

/**
 * Active rich-text vocabulary. Defaults to MUI so any `TransRichText` works
 * with zero wiring; `I18nRoot` re-provides this so a Base UI root can override
 * it for its subtree.
 */
export const RichTextComponentsContext = createContext<RichTextComponents>(muiRichTextComponents);

export { muiRichTextComponents };

type Props = Omit<React.ComponentProps<typeof Trans>, "components"> & {
  /** Per-call overrides merged over the active map — e.g. a runtime-computed link href. */
  components?: Partial<RichTextComponents>;
};

/**
 * Renders an i18n message that contains rich text, mapping translator tags to
 * the active UI library's components. A thin wrapper over react-i18next's
 * `Trans`: it injects the {@link RichTextComponentsContext} map so call sites
 * share one vocabulary instead of re-declaring `components` each time.
 *
 * Trust model: translation catalogs are developer-authored and trusted. `Trans`
 * renders their markup as React elements (text becomes React-escaped children),
 * never via `dangerouslySetInnerHTML`/`shouldUnescape` — keep it that way. The
 * only user-controlled risk is a dynamic href passed through a per-call `link`
 * override; validating that is the call site's responsibility, exactly as for
 * any other `<Link href={…}>`.
 */
export default function TransRichText({ components, ...rest }: Props): React.ReactNode {
  const map = useContext(RichTextComponentsContext);
  return <Trans {...rest} components={{ ...map, ...components }} />;
}
