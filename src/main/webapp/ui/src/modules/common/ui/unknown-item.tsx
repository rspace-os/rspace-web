import { CircleQuestionMarkIcon } from "lucide-react";
import type * as React from "react";
import { useTranslation } from "react-i18next";

import { Item, ItemContent, ItemMedia, ItemTitle } from "@/modules/common/ui/item";

/** Stable presentation for a missing or unreadable related record. */
function UnknownItem(props: React.ComponentProps<typeof Item>) {
  const { t } = useTranslation("common");

  return (
    <Item {...props}>
      <ItemMedia variant="icon">
        <CircleQuestionMarkIcon aria-hidden="true" />
      </ItemMedia>
      <ItemContent>
        <ItemTitle>{t("values.unknownItem")}</ItemTitle>
      </ItemContent>
    </Item>
  );
}

export { UnknownItem };
