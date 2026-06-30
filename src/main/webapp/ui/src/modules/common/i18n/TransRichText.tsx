import Link from "@mui/material/Link";
import type React from "react";
import { createContext, useContext } from "react";
import { Trans } from "react-i18next";

export type RichTextComponents = { a: React.ReactElement };

const muiRichTextComponents: RichTextComponents = { a: <Link /> };

export const RichTextComponentsContext = createContext<RichTextComponents>(muiRichTextComponents);

export { muiRichTextComponents };

type Props = Omit<React.ComponentProps<typeof Trans>, "components"> & {
  components?: Partial<RichTextComponents>;
};

export default function TransRichText({ components, ...rest }: Props): React.ReactNode {
  const map = useContext(RichTextComponentsContext);
  return <Trans {...rest} components={{ ...map, ...components }} />;
}
