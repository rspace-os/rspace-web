import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import type React from "react";
import { createContext, Fragment, useContext } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link as RouterLink, useInRouterContext } from "react-router";
import i18n from "@/modules/common/i18n";
import type Resources from "@/modules/common/i18n/resources";

export type RichTextComponents = Record<string, React.ReactElement>;

export type HelpDocsArticle = keyof Resources["common"]["help"];

export const HELP_DOCS_ARTICLE_BASE = "https://researchspace.helpdocs.io/article";

function helpDocsArticleUrlFromSlug(slug: string): string {
  return `${HELP_DOCS_ARTICLE_BASE}/${slug}`;
}

export function helpDocsArticleSlug(docLink: HelpDocsArticle): string {
  return i18n.t(`common:help.${docLink}`);
}

export function helpDocsArticleUrl(docLink: HelpDocsArticle): string {
  return helpDocsArticleUrlFromSlug(helpDocsArticleSlug(docLink));
}

type LinkProps = React.ComponentProps<typeof Link>;

/**
 * Renders `<internalLink to="/path">` in a message as a react-router
 * transition when a router is present, falling back to a plain anchor on
 * entry points that render outside a router.
 */
export function InternalLink({ to, ...rest }: LinkProps & { to?: string }): React.ReactNode {
  const inRouter = useInRouterContext();
  if (inRouter) return <Link {...rest} component={RouterLink} to={to ?? ""} />;
  return <Link {...rest} href={to} />;
}

/**
 * Renders `<externalLink href="...">` in a message: always opens in a new
 * tab without a referrer.
 */
function ExternalLink(props: LinkProps): React.ReactNode {
  return <Link {...props} target="_blank" rel="noreferrer" />;
}

/**
 * Renders `<helpDocs docLink="...">` in a message as a link to the RSpace
 * help documentation, opened in a new tab. The docLink value resolves through
 * `common:help`.
 */
function HelpDocsLink({ docLink, ...rest }: LinkProps & { docLink?: HelpDocsArticle }): React.ReactNode {
  const { t } = useTranslation("common");
  if (!docLink) {
    throw new Error("<helpDocs> rich text links require a docLink attribute.");
  }
  return <Link {...rest} href={helpDocsArticleUrlFromSlug(t(`help.${docLink}`))} target="_blank" rel="noreferrer" />;
}

const muiRichTextComponents: RichTextComponents = {
  externalLink: <ExternalLink />,
  helpDocs: <HelpDocsLink />,
  internalLink: <InternalLink />,
  br: <br />,
  cite: <cite />,
  code: <code />,
  kbd: <Typography component="kbd" variant="button" />,
  li: <li />,
  ol: <ol />,
  strong: <strong />,
  sub: <sub />,
  ul: <ul />,
};

/**
 * Wraps a free-text value (e.g. a sample or person's name) for use as a `components` entry's own
 * child, so it renders as literal text no matter what it contains - never parsed for markup.
 *
 * Trans/TransRichText build their output by parsing the *translated string* into a tree and only
 * then filling in `components`/`values`. Passing a value through `values` (e.g. `t(key, { name })`
 * with the catalog string `<strong>{name}</strong>`) substitutes the raw value into that string
 * *before* parsing, so markup-like characters in the value (`<a>`, `<strong>`, an indexed `<1>`, …)
 * get parsed as real elements rather than shown as literal text - this app disables HTML-escaping
 * app-wide (`interpolation: { escapeValue: false }` in modules/common/i18n/index.ts) specifically
 * so *translators'* own markup in a catalog string renders as real elements, so it can't be turned
 * on just for one value.
 *
 * The safe alternative is to keep the value out of the translated string entirely: give it an
 * empty placeholder tag in the catalog (e.g. `<name></name>`) and supply the real value through
 * `components`, wrapped with this function, e.g. `components={{ name: <strong>{richText(fullName)}
 * </strong> }}`. Trans only keeps a `components` entry's own children - instead of the (empty)
 * parsed result - when those children pass its own `hasValidReactChildren` check: an array of
 * valid React elements. A bare string or number fails that check, so it's wrapped in a one-item
 * Fragment array here.
 */
export function richText(value: React.ReactNode): Array<React.ReactElement> {
  return [<Fragment key="richText">{value}</Fragment>];
}

export const RichTextComponentsContext = createContext<RichTextComponents>(muiRichTextComponents);

export { muiRichTextComponents };

type Props = Omit<React.ComponentProps<typeof Trans>, "components"> & {
  components?: RichTextComponents;
};

export default function TransRichText({ components, ...rest }: Props): React.ReactNode {
  const map = useContext(RichTextComponentsContext);
  return <Trans {...rest} components={{ ...map, ...components }} />;
}
