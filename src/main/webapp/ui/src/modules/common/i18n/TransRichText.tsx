import Link, { type LinkProps } from "@mui/material/Link";
import type React from "react";
import { createContext, createElement, useContext } from "react";
import { Trans } from "react-i18next";

export type RichTextComponents = Record<string, React.ReactElement>;

export const richTextLink = (props: LinkProps = {}): React.ReactElement => createElement(Link, props);

const muiRichTextComponents: RichTextComponents = {
  a: richTextLink(),
  br: <br />,
  code: <code />,
  strong: <strong />,
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
