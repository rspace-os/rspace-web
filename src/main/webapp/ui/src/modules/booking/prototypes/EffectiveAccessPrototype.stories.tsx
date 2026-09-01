// PROTOTYPE ONLY (RPD-183 issue 20). Three ways to explain effective access and role sources on
// the production access editor's row spine (GranteeIdentity / RoleMenu / RowActions are imported,
// not forked): InlineSummary keeps an effective-role line in the row with a Collapsible source
// list; SourceChip compresses it to a chip that opens an anchored Popover; RowDrawer moves the
// full source story into a Sheet for narrow screens and long source lists. All three share the
// same fixtures (typed against the production schemas), the same remove-preview behaviour, and
// the same acceptance play. Grantees with only inherited access cannot appear in today's
// assignments-only document; they render in a clearly-marked proposed section so the API gap is
// visible rather than hidden. All state is in-memory; nothing persists and no network runs.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { GlobeIcon, InfoIcon, PencilIcon, UsersIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { bookingResourceAccessAdapter } from "@/modules/booking/pages/bookable-items/bookingResourceAccess";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { GranteeIdentity, RoleMenu, RowActions } from "@/modules/common/resource-access/AccessRowParts";
import {
  type AccessRow,
  buildAccessRows,
  draftWithout,
  draftWithRestored,
  draftWithRole,
} from "@/modules/common/resource-access/accessRows";
import type { ResourceAccessAdapter } from "@/modules/common/resource-access/ResourceAccessEditor";
import type { ResourceAccessAssignment, ResourceGrantee } from "@/modules/common/resource-access/schemas";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { Label } from "@/modules/common/ui/label";
import { Popover, PopoverContent, PopoverTitle, PopoverTrigger } from "@/modules/common/ui/popover";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/modules/common/ui/sheet";
import { cn } from "@/modules/common/utils/cn";
import {
  effectiveAfterDirectRemoval,
  effectiveRoleOf,
  INHERITED_ONLY_GRANTEES,
  nonDirectSources,
  type PrototypeRoleSource,
  SAVED_DOCUMENT,
} from "./effectiveAccessPrototypeFixtures";

type Variant = "inline" | "chip" | "drawer";

// --- Shared source vocabulary ----------------------------------------------------------------

function roleLabel(adapter: ResourceAccessAdapter, key: string | null): string {
  if (key === null) return "No access";
  return adapter.roles.find((role) => role.key === key)?.label ?? key;
}

/** One source, one sentence: who grants it, what it grants, and whether it is changeable here. */
function sourceParts(adapter: ResourceAccessAdapter, source: PrototypeRoleSource) {
  if (source.kind === "DIRECT") {
    return {
      Icon: PencilIcon,
      name: "Direct assignment",
      note: "Changeable on this page.",
      role: roleLabel(adapter, source.role),
    };
  }
  if (source.kind === "AUDIENCE") {
    return {
      Icon: GlobeIcon,
      name: "All users",
      note: "Applies to everyone with an account; it cannot be removed per person.",
      role: roleLabel(adapter, source.role),
    };
  }
  return {
    Icon: UsersIcon,
    name: source.grantee ? `Group: ${source.grantee.name}` : "A deleted group (identity unavailable)",
    note: source.grantee ? "Managed through the group's membership, not here." : "This source no longer resolves.",
    role: roleLabel(adapter, source.role),
  };
}

function SourceList({ adapter, sources }: { adapter: ResourceAccessAdapter; sources: readonly PrototypeRoleSource[] }) {
  return (
    <ul className="max-h-48 space-y-2 overflow-y-auto pr-1 text-sm">
      {sources.map((source, index) => {
        const { Icon, name, note, role } = sourceParts(adapter, source);
        return (
          <li key={index} className="flex items-start gap-2">
            <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
            <span className="min-w-0">
              <span className="flex flex-wrap items-center gap-1.5">
                <span className="min-w-0 break-words font-medium">{name}</span>
                <Badge variant="secondary">{role}</Badge>
              </span>
              <span className="block text-xs text-muted-foreground">{note}</span>
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/** Why removing the direct assignment may not remove access: shown inline for staged removals. */
function RemovePreview({ adapter, grantee }: { adapter: ResourceAccessAdapter; grantee: ResourceGrantee }) {
  const remaining = effectiveAfterDirectRemoval(grantee.roleSources);
  const inherited = nonDirectSources(grantee.roleSources);
  return (
    <p className="flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400">
      <InfoIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
      {remaining === null ? (
        <span>After saving, {grantee.name} loses all access to this configuration.</span>
      ) : (
        <span>
          After saving, {grantee.name} still has {roleLabel(adapter, remaining)} through{" "}
          {inherited.map((source) => sourceParts(adapter, source).name).join(" and ")}.
        </span>
      )}
    </p>
  );
}

// --- The three disclosure variants ------------------------------------------------------------

type DisclosureProps = {
  adapter: ResourceAccessAdapter;
  grantee: ResourceGrantee;
  directRole: string | null;
  variant: Variant;
};

/** The always-visible effective-role line every variant shares. */
function EffectiveLine({ adapter, grantee, directRole }: Omit<DisclosureProps, "variant">) {
  const effective = effectiveRoleOf(directRole, grantee.roleSources);
  const inheritedOnly = directRole === null;
  const boosted = !inheritedOnly && effective !== directRole;
  return (
    <span className="text-xs text-muted-foreground">
      Effective role: <span className="font-medium text-foreground">{roleLabel(adapter, effective)}</span>
      {boosted ? " (stronger than the direct role)" : inheritedOnly ? " (no direct role)" : ""}
    </span>
  );
}

function SourceDisclosure({ adapter, grantee, directRole, variant }: DisclosureProps) {
  const count = grantee.roleSources.length;
  const triggerLabel = `Access details for ${grantee.name}`;
  const summary = `${count} ${count === 1 ? "source" : "sources"}`;

  if (variant === "inline") {
    return (
      <Collapsible className="min-w-0">
        <span className="flex min-w-0 flex-wrap items-center gap-x-2">
          <EffectiveLine adapter={adapter} grantee={grantee} directRole={directRole} />
          <CollapsibleTrigger
            render={<Button type="button" variant="link" size="sm" className="h-auto p-0 text-xs" />}
            aria-label={triggerLabel}
          >
            {summary}
          </CollapsibleTrigger>
        </span>
        <CollapsibleContent className="mt-2 rounded-sm border bg-muted/30 p-2">
          <SourceList adapter={adapter} sources={grantee.roleSources} />
        </CollapsibleContent>
      </Collapsible>
    );
  }

  if (variant === "chip") {
    return (
      <span className="flex min-w-0 flex-wrap items-center gap-x-2">
        <EffectiveLine adapter={adapter} grantee={grantee} directRole={directRole} />
        <Popover>
          <PopoverTrigger
            render={<Button type="button" variant="outline" size="sm" className="h-6 rounded-full px-2 text-xs" />}
            aria-label={triggerLabel}
          >
            {summary}
          </PopoverTrigger>
          <PopoverContent align="start" className="w-80 max-w-[calc(100vw-2rem)]">
            <PopoverTitle className="mb-2 text-sm font-medium">Where {grantee.name}'s access comes from</PopoverTitle>
            <SourceList adapter={adapter} sources={grantee.roleSources} />
          </PopoverContent>
        </Popover>
      </span>
    );
  }

  return (
    <span className="flex min-w-0 flex-wrap items-center gap-x-2">
      <EffectiveLine adapter={adapter} grantee={grantee} directRole={directRole} />
      <Sheet>
        <SheetTrigger
          render={<Button type="button" variant="link" size="sm" className="h-auto p-0 text-xs" />}
          aria-label={triggerLabel}
        >
          {summary}
        </SheetTrigger>
        <SheetContent side="right" className="w-full max-w-96 overflow-y-auto p-4">
          <SheetHeader className="p-0">
            <SheetTitle>Access details for {grantee.name}</SheetTitle>
            <SheetDescription>
              Direct role: {roleLabel(adapter, directRole)} · Effective role:{" "}
              {roleLabel(adapter, effectiveRoleOf(directRole, grantee.roleSources))}
            </SheetDescription>
          </SheetHeader>
          <div className="mt-3">
            <SourceList adapter={adapter} sources={grantee.roleSources} />
          </div>
        </SheetContent>
      </Sheet>
    </span>
  );
}

// --- The editor page --------------------------------------------------------------------------

function EffectiveAccessPrototype({ variant }: { variant: Variant }) {
  const { t } = useTranslation("booking");
  const adapter = React.useMemo(() => bookingResourceAccessAdapter(t), [t]);
  const [saved, setSaved] = React.useState<readonly ResourceAccessAssignment[]>(SAVED_DOCUMENT.assignments);
  const [draft, setDraft] = React.useState<readonly ResourceAccessAssignment[]>(SAVED_DOCUMENT.assignments);
  const [announcement, setAnnouncement] = React.useState("");
  const [narrow, setNarrow] = React.useState(false);
  const callerKey = SAVED_DOCUMENT.caller.granteeKey ?? null;
  const rows = buildAccessRows(saved, draft, callerKey);
  const dirty = rows.some((row) => row.status !== "unchanged");

  const stagedLabelFor = (row: AccessRow): string | null => {
    if (row.status === "added") return "Staged: added";
    if (row.status === "changed")
      return `Staged: ${roleLabel(adapter, row.fromRole)} → ${roleLabel(adapter, row.assignment.role)}`;
    if (row.status === "removed") return "Staged: removal";
    return null;
  };

  const lastOwner = draft.filter((assignment) => assignment.role === adapter.ownerRole).length <= 1;

  const rowChrome = (row: AccessRow) => {
    const grantee = row.assignment.grantee;
    const isLockedOwner = row.assignment.role === adapter.ownerRole && lastOwner && row.status !== "removed";
    return (
      <div className="min-w-0 space-y-1.5">
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-2">
          <GranteeIdentity row={row} stagedLabel={stagedLabelFor(row)} />
          <span className="flex items-center gap-1.5">
            <RoleMenu
              row={row}
              roles={adapter.roles.filter((role) => role.allowedGranteeKinds.includes(grantee.kind))}
              disabled={row.status === "removed"}
              lockReason={isLockedOwner ? "The final Owner's role is locked. Add another Owner first." : null}
              onChange={(role) => {
                setDraft((current) => draftWithRole(current, row.key, role));
                setAnnouncement(`Unsaved changes: ${grantee.name}'s role updated.`);
              }}
            />
            <RowActions
              row={row}
              leaveLabel={adapter.leaveLabel}
              blockedReason={isLockedOwner ? "Add another Owner first." : null}
              onRemove={() => {
                setDraft((current) => draftWithout(current, row.key));
                setAnnouncement(`Unsaved changes: ${grantee.name}'s direct assignment will be removed.`);
              }}
              onRestore={() => {
                setDraft((current) => draftWithRestored(saved, current, row.assignment));
                setAnnouncement(`Unsaved changes: ${grantee.name} restored.`);
              }}
              onLeave={() => {
                setDraft((current) => draftWithout(current, row.key));
                setAnnouncement("Unsaved changes: your direct assignment will be removed.");
              }}
            />
          </span>
        </div>
        <div className="pl-4">
          <SourceDisclosure
            adapter={adapter}
            grantee={grantee}
            directRole={row.status === "removed" ? null : row.assignment.role}
            variant={variant}
          />
          {row.status === "removed" ? <RemovePreview adapter={adapter} grantee={grantee} /> : null}
        </div>
      </div>
    );
  };

  return (
    <div className={cn("space-y-4 p-4", narrow && "max-w-[320px]")}>
      <div className="flex items-center gap-2">
        <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
        <Label htmlFor="narrow" className="text-xs">
          Constrain page to 320 px
        </Label>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Access — Confocal microscope</CardTitle>
          <CardDescription>
            Direct roles are edited here. The effective role also counts group and All-users access, so it can be
            stronger than the direct role and can survive removing it.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="divide-y">
            {rows.map((row) => (
              <li key={row.key} className={cn("py-3", row.status === "removed" && "opacity-60")}>
                {rowChrome(row)}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-sm">Access without a direct role</CardTitle>
          <CardDescription>
            Proposed addition: these people appear in no assignment row today, yet they have effective access through
            groups or All users. The current API cannot list them.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="divide-y">
            {INHERITED_ONLY_GRANTEES.map((grantee) => (
              <li key={grantee.key} className="space-y-1.5 py-3">
                <GranteeIdentity
                  row={{
                    key: grantee.key,
                    assignment: { grantee, role: "" },
                    status: "unchanged",
                    fromRole: null,
                    isSelf: false,
                  }}
                  stagedLabel={null}
                />
                <div className="pl-4">
                  <SourceDisclosure adapter={adapter} grantee={grantee} directRole={null} variant={variant} />
                </div>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      {announcement !== "" ? (
        <p role="status" className="text-xs text-muted-foreground">
          {announcement}
        </p>
      ) : null}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setDraft(saved);
            setAnnouncement("Changes discarded; saved access is unchanged.");
          }}
        >
          Cancel
        </Button>
        <Button
          type="button"
          disabled={!dirty}
          onClick={() => {
            setSaved(draft);
            setAnnouncement("Saved.");
          }}
        >
          Save changes
        </Button>
      </div>
    </div>
  );
}

function PrototypePage({ variant }: { variant: Variant }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <EffectiveAccessPrototype variant={variant} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Effective Access And Sources",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance -------------------------------------------------------------------------------

const runAcceptance =
  (variant: Variant): NonNullable<Story["play"]> =>
  async ({ canvasElement, step }) => {
    // Portalled popovers and sheets land in body; one semantic root avoids mixing query scopes.
    const canvas = within(canvasElement.ownerDocument.body);
    const details = (name: string) => canvas.getByRole("button", { name: `Access details for ${name}` });
    // Popovers and sheets animate in, so the detail scope must be re-resolved on every retry
    // rather than captured once before the panel finishes mounting.
    const inDetails = async (assert: (scope: ReturnType<typeof within>) => void) => {
      await waitFor(() => {
        const root =
          variant === "inline"
            ? canvasElement
            : (canvasElement.ownerDocument.body.querySelector("[role=dialog]") as HTMLElement | null);
        expect(root).not.toBeNull();
        assert(within(root as HTMLElement));
      });
    };
    const closeDetails = async () => {
      if (variant !== "inline") await userEvent.keyboard("{Escape}");
    };

    // The first story in the file pays the lazy i18n mount cost: wait for the page to exist.
    await canvas.findByRole("button", { name: "Access details for Grace Hopper" });

    await step("source details open by keyboard with the grantee in the accessible name", async () => {
      const trigger = details("Grace Hopper");
      trigger.focus();
      await userEvent.keyboard("{Enter}");
      await inDetails((scope) => {
        expect(scope.getByText("Group: Cell Biology Lab")).toBeInTheDocument();
        expect(scope.getByText(/Managed through the group's membership/)).toBeInTheDocument();
      });
      await closeDetails();
    });

    await step("an unavailable source is described without a broken link", async () => {
      const trigger = details("Leo Szilard");
      await userEvent.click(trigger);
      await inDetails((scope) => {
        const unavailable = scope.getByText("A deleted group (identity unavailable)");
        expect(unavailable).toBeInTheDocument();
        expect(unavailable.closest("a")).toBeNull();
      });
      await closeDetails();
    });

    await step("ten sources stay readable inside a scrollable list", async () => {
      await userEvent.click(details("Sam Okafor"));
      await inDetails((scope) => expect(scope.getAllByRole("listitem").length).toBeGreaterThanOrEqual(10));
      await closeDetails();
      if (variant === "inline") await userEvent.click(details("Sam Okafor")); // collapse again
    });

    await step("removing a direct role previews the remaining effective role", async () => {
      await userEvent.click(canvas.getByRole("button", { name: "Remove Charles Babbage" }));
      expect(
        await canvas.findByText(/Charles Babbage still has Manager through Group: Engineering Support/),
      ).toBeInTheDocument();
      await userEvent.click(canvas.getByRole("button", { name: "Restore Charles Babbage" }));

      await userEvent.click(canvas.getByRole("button", { name: "Remove Ada Lovelace" }));
      expect(await canvas.findByText(/Ada Lovelace loses all access/)).toBeInTheDocument();
      await userEvent.click(canvas.getByRole("button", { name: "Restore Ada Lovelace" }));
    });

    await step("the final Owner is locked with a reason", async () => {
      expect(canvas.getByText("The final Owner's role is locked. Add another Owner first.")).toBeInTheDocument();
    });

    await step("320 px: no overflow, details do not obscure Save and Cancel", async () => {
      await userEvent.click(canvas.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
      const page = canvasElement.querySelector(".space-y-4") as HTMLElement;
      await waitFor(() => expect(page.getBoundingClientRect().width).toBeLessThanOrEqual(320));
      expect(page.scrollWidth).toBeLessThanOrEqual(page.clientWidth + 1);

      // The long group name must wrap rather than widen the page.
      expect(canvas.getByText(/Interdisciplinary Quantitative Biosciences/)).toBeInTheDocument();
      expect(page.scrollWidth).toBeLessThanOrEqual(page.clientWidth + 1);

      // Base UI popups aria-hide the rest of the page while open, so grab Save first and assert
      // it stays present (the geometric non-overlap is checked in the screenshot review).
      const save = canvas.getByRole("button", { name: "Save changes" });
      const trigger = details("Grace Hopper");
      await userEvent.click(trigger);
      expect(canvasElement.ownerDocument.contains(save)).toBe(true);
      await closeDetails();
      // Leave the inline variant expanded so the post-play axe scan covers the open state.
      if (variant !== "inline") {
        await waitFor(() =>
          expect(canvasElement.ownerDocument.body.querySelector("[role=dialog]")).not.toBeInTheDocument(),
        );
      }
    });
  };

export const InlineSummary: Story = {
  args: { variant: "inline" },
  play: import.meta.env.MODE === "test" ? runAcceptance("inline") : undefined,
};

export const SourceChip: Story = {
  args: { variant: "chip" },
  play: import.meta.env.MODE === "test" ? runAcceptance("chip") : undefined,
};

export const RowDrawer: Story = {
  args: { variant: "drawer" },
  play: import.meta.env.MODE === "test" ? runAcceptance("drawer") : undefined,
};
