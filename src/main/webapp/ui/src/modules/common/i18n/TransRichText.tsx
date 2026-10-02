import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import { Link as TanStackLink, useRouter } from "@tanstack/react-router";
import type React from "react";
import { createContext, useContext } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link as RouterLink, useInRouterContext } from "react-router";
import i18n from "@/modules/common/i18n";
import type Resources from "@/modules/common/i18n/resources";
import { Kbd as BaseUiKbd } from "@/modules/common/ui/kbd";
import { Link as BaseUiLink } from "@/modules/common/ui/typography";

export type RichTextComponents = Record<string, React.ReactElement>;

export type HelpDocsArticle = keyof Resources["common"]["help"];

export const HELP_DOCS_ARTICLE_BASE = "https://researchspace.helpdocs.io/article";

function helpDocsArticleUrlFromSlug(slug: string) {
  return `${HELP_DOCS_ARTICLE_BASE}/${slug}`;
}

export function helpDocsArticleSlug(docLink: HelpDocsArticle) {
  return i18n.t(`common:help.${docLink}`);
}

export function helpDocsArticleUrl(docLink: HelpDocsArticle) {
  return helpDocsArticleUrlFromSlug(helpDocsArticleSlug(docLink));
}

type LinkProps = React.ComponentProps<typeof Link>;

/**
 * Renders `<internalLink to="/path">` in a message as a react-router
 * transition when a router is present, falling back to a plain anchor on
 * entry points that render outside a router.
 */
export function InternalLink({ to, ...rest }: LinkProps & { to?: string }) {
  const inRouter = useInRouterContext();
  if (inRouter) return <Link {...rest} component={RouterLink} to={to ?? ""} />;
  return <Link {...rest} href={to} />;
}

/**
 * Renders `<externalLink href="...">` in a message: always opens in a new
 * tab without a referrer.
 */
function ExternalLink(props: LinkProps) {
  return <Link {...props} target="_blank" rel="noreferrer" />;
}

/**
 * Renders `<helpDocs docLink="...">` in a message as a link to the RSpace
 * help documentation, opened in a new tab. The docLink value resolves through
 * `common:help`.
 */
function HelpDocsLink({ docLink, ...rest }: LinkProps & { docLink?: HelpDocsArticle }) {
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

type BaseUiLinkProps = React.ComponentProps<typeof BaseUiLink>;

// `to` is an arbitrary translation-catalog string, not something TanStack can
// type-check, and can point at a legacy (non-router) page — matchRoute decides
// whether a real transition is safe, falling back to a plain anchor otherwise.
function BaseUiInternalLink({ to, ...rest }: BaseUiLinkProps & { to?: string }) {
  const router = useRouter();
  const isRegisteredRoute = to !== undefined && router.matchRoute({ to: to as never }, { fuzzy: true }) !== false;
  if (isRegisteredRoute) return <BaseUiLink {...rest} render={<TanStackLink to={to as never} />} />;
  return <BaseUiLink {...rest} href={to} />;
}

function BaseUiExternalLink(props: BaseUiLinkProps) {
  return <BaseUiLink {...props} target="_blank" rel="noreferrer" />;
}

function BaseUiHelpDocsLink({ docLink, ...rest }: BaseUiLinkProps & { docLink?: HelpDocsArticle }) {
  const { t } = useTranslation("common");
  if (!docLink) {
    throw new Error("<helpDocs> rich text links require a docLink attribute.");
  }
  return (
    <BaseUiLink {...rest} href={helpDocsArticleUrlFromSlug(t(`help.${docLink}`))} target="_blank" rel="noreferrer" />
  );
}

const baseUiRichTextComponents: RichTextComponents = {
  externalLink: <BaseUiExternalLink />,
  helpDocs: <BaseUiHelpDocsLink />,
  internalLink: <BaseUiInternalLink />,
  br: <br />,
  cite: <cite />,
  code: <code />,
  kbd: <BaseUiKbd />,
  li: <li />,
  ol: <ol />,
  strong: <strong />,
  sub: <sub />,
  ul: <ul />,
};

export const RichTextComponentsContext = createContext<RichTextComponents>(muiRichTextComponents);

export { baseUiRichTextComponents, muiRichTextComponents };

type Props = Omit<React.ComponentProps<typeof Trans>, "components"> & {
  components?: RichTextComponents;
};

export default function TransRichText({ components, ...rest }: Props) {
  const map = useContext(RichTextComponentsContext);
  return <Trans {...rest} components={{ ...map, ...components }} />;
}
