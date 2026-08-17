import { ExternalLinkIcon, MicroscopeIcon } from "lucide-react";
import type * as React from "react";

import { Badge } from "@/modules/common/ui/badge";
import { Item, ItemContent, ItemMedia, ItemTitle } from "@/modules/common/ui/item";

type GlobalIdPlacement = "description" | "title";

function GlobalIdBadge({ globalId, href, label }: { globalId: string; href: string; label: string }) {
  return (
    <Badge variant="outline" className="font-mono" render={<a href={href} aria-label={label} />}>
      <ExternalLinkIcon aria-hidden="true" />
      {globalId}
    </Badge>
  );
}

function InventoryItem({
  name,
  globalId,
  href,
  idLinkLabel,
  idPlacement = "description",
  compact = false,
  className,
  children,
  ...props
}: Omit<React.ComponentProps<typeof Item>, "children"> & {
  name: React.ReactNode;
  globalId: string;
  href: string;
  /**
   * Accessible name for the global-ID link. Required: the badge text alone is
   * an opaque identifier, and this layer has no i18n of its own, so the caller
   * owns the wording.
   */
  idLinkLabel: string;
  idPlacement?: GlobalIdPlacement;
  /**
   * Single-line layout: the name and the global ID share the title row and the
   * second line is dropped, so `children` and `idPlacement` are ignored.
   */
  compact?: boolean;
  children?: React.ReactNode;
}) {
  const badge = <GlobalIdBadge globalId={globalId} href={href} label={idLinkLabel} />;
  const idInTitle = compact || idPlacement === "title";

  return (
    <Item className={className} {...props}>
      {/* ponytail: one icon for every record type. Key it off the item type
          (container / sample / subsample / template) when those land. */}
      <ItemMedia variant="icon">
        <MicroscopeIcon aria-hidden="true" />
      </ItemMedia>
      <ItemContent>
        <ItemTitle>
          <span className="min-w-0 truncate">{name}</span>
          {idInTitle ? badge : null}
        </ItemTitle>
        {/*
          A div rather than ItemDescription: ItemDescription is a <p>, and line 2
          carries a badge plus arbitrary caller content. The muted typography is
          reused from ItemDescription so the two placements stay visually identical.
        */}
        {compact ? null : (
          <div
            data-slot="item-description"
            className="flex min-w-0 items-center gap-1.5 text-left text-sm font-normal text-muted-foreground"
          >
            {idInTitle ? null : badge}
            {children ? <span className="flex min-w-0 items-center gap-1.5 truncate">{children}</span> : null}
          </div>
        )}
      </ItemContent>
    </Item>
  );
}

export type { GlobalIdPlacement };
export { InventoryItem };
