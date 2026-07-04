import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import type React from "react";
import { createContext, useContext } from "react";
import { Trans } from "react-i18next";
import { Link as RouterLink, useInRouterContext } from "react-router";

export type RichTextComponents = Record<string, React.ReactElement>;

export const HELP_DOCS_ARTICLE_BASE = "https://researchspace.helpdocs.io/article";

export function helpDocsArticleUrl(slug: string): string {
  return `${HELP_DOCS_ARTICLE_BASE}/${slug}`;
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
 * Renders `<helpDocs slug="...">` in a message as a link to the RSpace help
 * documentation, opened in a new tab.
 */
function HelpDocsLink({ slug, ...rest }: LinkProps & { slug?: string }): React.ReactNode {
  return <Link {...rest} href={helpDocsArticleUrl(slug ?? "")} target="_blank" rel="noreferrer" />;
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

export const RichTextComponentsContext = createContext<RichTextComponents>(muiRichTextComponents);

export { muiRichTextComponents };

type Props = Omit<React.ComponentProps<typeof Trans>, "components"> & {
  components?: RichTextComponents;
};

export default function TransRichText({ components, ...rest }: Props): React.ReactNode {
  const map = useContext(RichTextComponentsContext);
  return <Trans {...rest} components={{ ...map, ...components }} />;
}
