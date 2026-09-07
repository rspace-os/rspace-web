import { ExternalLinkIcon, MapPinIcon, MicroscopeIcon } from "lucide-react";
import type * as React from "react";
import { Badge } from "@/modules/common/ui/badge";
import { Item, ItemContent, ItemMedia, ItemTitle } from "@/modules/common/ui/item";
import { cn } from "@/modules/common/utils/cn";

type GlobalIdPlacement = "description" | "title";

function GlobalIdBadge({
  globalId,
  href,
  label,
  link,
}: {
  globalId: string;
  href?: string;
  label?: string;
  link?: React.ReactElement;
}) {
  if (!href && !link) {
    return (
      <Badge variant="outline" className="font-mono">
        {globalId}
      </Badge>
    );
  }
  if (link) {
    return (
      <Badge variant="outline" className="font-mono" render={link}>
        {globalId}
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className="font-mono" render={<a href={href} aria-label={label ?? globalId} />}>
      <ExternalLinkIcon aria-hidden="true" />
      {globalId}
    </Badge>
  );
}

function InventoryLocationLink({
  name,
  globalId,
  compact = false,
}: {
  name?: string | null;
  globalId?: string | null;
  /** Keeps the complete location treatment on one compact line. */
  compact?: boolean;
}) {
  if (name == null || globalId == null) return null;

  const location = (
    <>
      <MapPinIcon aria-hidden="true" className="size-3.5 shrink-0" />
      <a
        href={`/globalId/${globalId}`}
        className="inline-flex min-w-0 items-center gap-1 text-foreground hover:underline"
      >
        <span className="truncate">{name}</span>
        <ExternalLinkIcon aria-hidden="true" className="size-3.5 shrink-0" />
      </a>
    </>
  );

  return compact ? (
    <span className="inline-flex max-w-full min-w-0 items-center gap-1 whitespace-nowrap text-xs">{location}</span>
  ) : (
    location
  );
}

function InventoryItem({
  name,
  nameAs: Name = "span",
  globalId,
  href,
  idLink,
  idLinkLabel,
  idPlacement = "description",
  compact = false,
  locationPlacement = "inline",
  className,
  children,
  ...props
}: Omit<React.ComponentProps<typeof Item>, "children"> & {
  name: React.ReactNode;
  /** Semantic element used for the record name. The default preserves the existing inline title. */
  nameAs?: "span" | "h1";
  globalId: string;
  href?: string;
  /** Router-aware link element for SPA destinations. */
  idLink?: React.ReactElement;
  /**
   * Accessible name for the global-ID link. Supply it with `href`. When `href`
   * is absent, the global ID is a non-interactive badge.
   */
  idLinkLabel?: string;
  idPlacement?: GlobalIdPlacement;
  /** Places the location content below the global ID instead of beside it. */
  locationPlacement?: "inline" | "below";
  /**
   * Single-line layout: the name and the global ID share the title row and the
   * second line is dropped, so `children` and `idPlacement` are ignored.
   */
  compact?: boolean;
  children?: React.ReactNode;
}) {
  const badge = <GlobalIdBadge globalId={globalId} href={href} label={idLinkLabel} link={idLink} />;
  const idInTitle = compact || idPlacement === "title";

  return (
    <Item data-inventory-item className={className} {...props}>
      {/* ponytail: one icon for every record type. Key it off the item type
          (container / sample / subsample / template) when those land. */}
      <ItemMedia variant="icon">
        <MicroscopeIcon aria-hidden="true" />
      </ItemMedia>
      <ItemContent>
        <ItemTitle>
          <Name className="min-w-0 truncate">{name}</Name>
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
            className={cn(
              "flex min-w-0 gap-1.5 text-left text-sm font-normal text-muted-foreground",
              locationPlacement === "below" ? "flex-col items-start" : "items-center",
            )}
          >
            {idInTitle ? null : badge}
            {children ? (
              <span className="flex min-w-0 max-w-full items-center gap-1.5 truncate">{children}</span>
            ) : null}
          </div>
        )}
      </ItemContent>
    </Item>
  );
}

export type { GlobalIdPlacement };
export { InventoryItem, InventoryLocationLink };
