// PROTOTYPE ONLY (RPD-183 issue 27). Three ways to explain what Owner, Manager, Booker, and
// Viewer mean at the point where an administrator chooses one, on the inline-summary access-row
// layout selected for issue 20. MenuDescriptions puts each role's sentence inside its menu
// option; SentenceBelow keeps the production menu and describes the selected role under the
// control (associated via aria-describedby, which the production RoleMenu cannot yet accept —
// recorded as an API gap); AboutRoles adds an adjacent disclosure with a comparison table.
// The copy is the shipped booking.json access.roles.*.description text, deliberately unchanged,
// plus separate invariant footnotes; content gaps against the issue's minimum-meaning table are
// recorded in the decision record instead of being silently rewritten here. Two fixture cards
// cover a Manager caller (Owner absent from menus, Owner rows locked, duplicate display names,
// a group grantee, All users) and the final Owner editing their own locked row.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
import type { Meta, StoryObj } from "@storybook/tanstack-react";
import { CheckIcon, ChevronDownIcon, LockIcon } from "lucide-react";
import * as React from "react";
import { useTranslation } from "react-i18next";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { bookingResourceAccessAdapter } from "@/modules/booking/pages/bookable-items/bookingResourceAccess";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { GranteeIdentity, RoleMenu } from "@/modules/common/resource-access/AccessRowParts";
import { type AccessRow, buildAccessRows } from "@/modules/common/resource-access/accessRows";
import type { ResourceAccessAdapter, ResourceAccessRole } from "@/modules/common/resource-access/ResourceAccessEditor";
import type { ResourceAccessAssignment, ResourceAccessDocument } from "@/modules/common/resource-access/schemas";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/common/ui/card";
import { Checkbox } from "@/modules/common/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/modules/common/ui/collapsible";
import { Label } from "@/modules/common/ui/label";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { cn } from "@/modules/common/utils/cn";
import { MANAGER_DOCUMENT, SAVED_DOCUMENT } from "./effectiveAccessPrototypeFixtures";

type Variant = "menu-descriptions" | "sentence-below" | "about-roles";

/** Invariants the shipped role descriptions do not carry; shown once per editor, not per row. */
const INVARIANT_NOTES = [
  "At least one Owner must always remain.",
  "Managers cannot grant or remove Owner.",
  "All users applies to everyone with an account and cannot be removed.",
] as const;

// --- Variant 1: descriptions inside each menu option -------------------------------------------

function RoleMenuWithDescriptions({
  row,
  roles,
  onChange,
}: {
  row: AccessRow;
  roles: readonly ResourceAccessRole[];
  onChange: (role: string) => void;
}) {
  const name = row.assignment.grantee.name;
  const current = roles.find(({ key }) => key === row.assignment.role);
  return (
    <Menu>
      <MenuTrigger
        render={
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-56 justify-between rounded-sm max-sm:w-auto max-sm:min-w-0 max-sm:flex-1"
          />
        }
        aria-label={`Direct role for ${name}`}
      >
        {current?.label ?? row.assignment.role}
        <ChevronDownIcon aria-hidden="true" />
      </MenuTrigger>
      {/* Wider than the production w-56: each option carries its one-sentence description. */}
      <MenuContent align="start" className="w-80 max-w-[calc(100vw-2rem)] rounded-sm">
        {roles.map((role) => (
          <MenuItem key={role.key} onClick={() => onChange(role.key)} className="items-start">
            <CheckIcon
              className={cn("mt-0.5 size-4 shrink-0", role.key !== row.assignment.role && "invisible")}
              aria-hidden="true"
            />
            <span className="min-w-0">
              <span className="block font-medium">{role.label}</span>
              <span className="block text-xs text-muted-foreground">{role.description}</span>
            </span>
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );
}

// --- Variant 2: a sentence below the control, tied to it with aria-describedby ------------------

/**
 * Production RoleMenu cannot take aria-describedby, so this minimal fork adds only that.
 * The decision record proposes the one-prop change instead of keeping this copy.
 */
function DescribedRoleMenu({
  row,
  roles,
  describedBy,
  onChange,
}: {
  row: AccessRow;
  roles: readonly ResourceAccessRole[];
  describedBy: string;
  onChange: (role: string) => void;
}) {
  const name = row.assignment.grantee.name;
  const current = roles.find(({ key }) => key === row.assignment.role);
  return (
    <Menu>
      <MenuTrigger
        render={
          <Button
            type="button"
            size="sm"
            variant="outline"
            className="w-56 justify-between rounded-sm max-sm:w-auto max-sm:min-w-0 max-sm:flex-1"
          />
        }
        aria-label={`Direct role for ${name}`}
        aria-describedby={describedBy}
      >
        {current?.label ?? row.assignment.role}
        <ChevronDownIcon aria-hidden="true" />
      </MenuTrigger>
      <MenuContent align="start" className="w-56 rounded-sm">
        {roles.map((role) => (
          <MenuItem key={role.key} onClick={() => onChange(role.key)}>
            <CheckIcon
              className={cn("size-4 shrink-0", role.key !== row.assignment.role && "invisible")}
              aria-hidden="true"
            />
            {role.label}
          </MenuItem>
        ))}
      </MenuContent>
    </Menu>
  );
}

function SentenceBelowControl({
  row,
  roles,
  onChange,
}: {
  row: AccessRow;
  roles: readonly ResourceAccessRole[];
  onChange: (role: string) => void;
}) {
  const descriptionId = React.useId();
  const current = roles.find(({ key }) => key === row.assignment.role);
  return (
    <span className="flex min-w-0 flex-col items-start gap-1">
      <DescribedRoleMenu row={row} roles={roles} describedBy={descriptionId} onChange={onChange} />
      {/* Not a live region: the user just picked the role; re-announcing on focus is enough. */}
      <span id={descriptionId} className="max-w-72 text-xs text-muted-foreground">
        {current?.description ?? ""}
      </span>
    </span>
  );
}

// --- Variant 3: an adjacent About roles disclosure ----------------------------------------------

function AboutRolesDisclosure({ roles }: { roles: readonly ResourceAccessRole[] }) {
  return (
    <Collapsible>
      <CollapsibleTrigger render={<Button type="button" variant="link" size="sm" className="h-auto p-0 text-xs" />}>
        About roles
      </CollapsibleTrigger>
      <CollapsibleContent className="mt-2 rounded-sm border bg-muted/30 p-2">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Role</TableHead>
              <TableHead>What it allows</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {roles.map((role) => (
              <TableRow key={role.key}>
                <TableCell className="align-top font-medium">{role.label}</TableCell>
                <TableCell className="whitespace-normal text-xs text-muted-foreground">{role.description}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <ul className="mt-2 list-disc space-y-0.5 pl-4 text-xs text-muted-foreground">
          {INVARIANT_NOTES.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      </CollapsibleContent>
    </Collapsible>
  );
}

// --- One editor card over a fixture document -----------------------------------------------------

function lockReasonFor(
  adapter: ResourceAccessAdapter,
  document: ResourceAccessDocument,
  row: AccessRow,
): string | null {
  const isOwnerRow = row.assignment.role === adapter.ownerRole;
  if (isOwnerRow && !document.caller.capabilities.canManageOwners) {
    return "Only an Owner can change an Owner's role.";
  }
  const owners = document.assignments.filter((assignment) => assignment.role === adapter.ownerRole);
  if (isOwnerRow && owners.length <= 1) {
    return "You are the only Owner. Add another Owner before changing this role.";
  }
  return null;
}

function EditorCard({
  title,
  description,
  document,
  variant,
}: {
  title: string;
  description: string;
  document: ResourceAccessDocument;
  variant: Variant;
}) {
  const { t } = useTranslation("booking");
  const adapter = React.useMemo(() => bookingResourceAccessAdapter(t), [t]);
  const [draft, setDraft] = React.useState<readonly ResourceAccessAssignment[]>(document.assignments);
  const rows = buildAccessRows(document.assignments, draft, document.caller.granteeKey ?? null);

  const assignableRoles = (row: AccessRow): readonly ResourceAccessRole[] =>
    adapter.roles.filter(
      (role) =>
        role.allowedGranteeKinds.includes(row.assignment.grantee.kind) &&
        (document.caller.capabilities.canManageOwners || role.key !== adapter.ownerRole),
    );

  const control = (row: AccessRow) => {
    const roles = assignableRoles(row);
    const lockReason = lockReasonFor(adapter, document, row);
    const onChange = (role: string) =>
      setDraft((current) =>
        current.map((assignment) => (assignment.grantee.key === row.key ? { ...assignment, role } : assignment)),
      );
    if (lockReason !== null) {
      // A locked row only reads its label from the list, and the label must resolve even for
      // roles the caller cannot assign (a Manager looking at an Owner row).
      return <RoleMenu row={row} roles={adapter.roles} disabled={false} lockReason={lockReason} onChange={onChange} />;
    }
    if (variant === "about-roles") {
      return <RoleMenu row={row} roles={roles} disabled={false} lockReason={null} onChange={onChange} />;
    }
    if (variant === "menu-descriptions")
      return <RoleMenuWithDescriptions row={row} roles={roles} onChange={onChange} />;
    return <SentenceBelowControl row={row} roles={roles} onChange={onChange} />;
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        {variant === "about-roles" ? <AboutRolesDisclosure roles={adapter.roles} /> : null}
        <ul className="divide-y">
          {rows.map((row) => (
            <li key={row.key} className="flex min-w-0 flex-wrap items-start justify-between gap-2 py-3">
              <GranteeIdentity row={row} stagedLabel={null} />
              <span className="flex min-w-0 flex-col items-end gap-1">
                {control(row)}
                {lockReasonFor(adapter, document, row) !== null ? (
                  <span className="flex items-center gap-1 text-xs text-muted-foreground">
                    <LockIcon className="size-3 shrink-0" aria-hidden="true" />
                    {lockReasonFor(adapter, document, row)}
                  </span>
                ) : null}
                {row.assignment.grantee.kind === "AUDIENCE" ? (
                  <span className="text-xs text-muted-foreground">Applies to everyone; cannot be removed.</span>
                ) : null}
              </span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function RoleDescriptionsPrototype({ variant }: { variant: Variant }) {
  const [narrow, setNarrow] = React.useState(false);
  return (
    <div className={cn("space-y-4 p-4", narrow && "max-w-[320px]")}>
      <div className="flex items-center gap-2">
        <Checkbox id="narrow" checked={narrow} onCheckedChange={(checked) => setNarrow(checked === true)} />
        <Label htmlFor="narrow" className="text-xs">
          Constrain page to 320 px
        </Label>
      </div>
      <EditorCard
        title="A Manager edits access"
        description="Owner is absent from the menus rather than temptingly selectable, and Owner rows are locked. The two Alex Chens are indistinguishable — a recorded finding, not a feature."
        document={MANAGER_DOCUMENT}
        variant={variant}
      />
      <EditorCard
        title="The final Owner edits their own row"
        description="The role is locked with a concise explanation instead of a dead menu."
        document={SAVED_DOCUMENT}
        variant={variant}
      />
    </div>
  );
}

function PrototypePage({ variant }: { variant: Variant }) {
  return (
    <I18nRoot namespaces={["booking", "common"]}>
      <RoleDescriptionsPrototype variant={variant} />
    </I18nRoot>
  );
}

const meta = {
  title: "Booking/Prototypes/Role Descriptions",
  component: PrototypePage,
  parameters: { layout: "fullscreen" },
} satisfies Meta<typeof PrototypePage>;

export default meta;
type Story = StoryObj<typeof meta>;

// --- Acceptance -------------------------------------------------------------------------------

const runAcceptance =
  (variant: Variant): NonNullable<Story["play"]> =>
  async ({ canvasElement, step }) => {
    const body = within(canvasElement.ownerDocument.body);
    const managerCard = within(
      (await body.findByText("A Manager edits access")).closest("[data-slot=card]") as HTMLElement,
    );
    const ownerCard = within(
      body.getByText("The final Owner edits their own row").closest("[data-slot=card]") as HTMLElement,
    );

    await step("a Manager sees no Owner option and cannot touch Owner rows", async () => {
      await userEvent.click(managerCard.getByRole("button", { name: "Direct role for Ada Lovelace" }));
      expect(await body.findByRole("menuitem", { name: /Booker/ })).toBeInTheDocument();
      expect(body.queryByRole("menuitem", { name: /Owner/ })).not.toBeInTheDocument();
      await userEvent.keyboard("{Escape}");
      await waitFor(() => expect(body.queryByRole("menu")).not.toBeInTheDocument());
      // The Owner-held row offers no menu at all, only the locked label and reason.
      expect(managerCard.queryByRole("button", { name: "Direct role for Priya Sharma" })).not.toBeInTheDocument();
      expect(managerCard.getAllByText("Only an Owner can change an Owner's role.").length).toBeGreaterThan(0);
    });

    await step("the final Owner's own row is locked with a concise reason", async () => {
      expect(ownerCard.queryByRole("button", { name: "Direct role for Priya Sharma" })).not.toBeInTheDocument();
      expect(
        ownerCard.getAllByText("You are the only Owner. Add another Owner before changing this role.").length,
      ).toBeGreaterThan(0);
    });

    await step("All users offers only audience roles", async () => {
      await userEvent.click(managerCard.getByRole("button", { name: "Direct role for All users" }));
      expect(await body.findByRole("menuitem", { name: /Viewer/ })).toBeInTheDocument();
      expect(body.queryByRole("menuitem", { name: /Manager/ })).not.toBeInTheDocument();
      expect(body.queryByRole("menuitem", { name: /Owner/ })).not.toBeInTheDocument();
      await userEvent.keyboard("{Escape}");
      await waitFor(() => expect(body.queryByRole("menu")).not.toBeInTheDocument());
    });

    if (variant === "menu-descriptions") {
      await step("each menu option carries its description", async () => {
        await userEvent.click(managerCard.getByRole("button", { name: "Direct role for Ada Lovelace" }));
        const option = await body.findByRole("menuitem", { name: /Booker/ });
        expect(option).toHaveTextContent(/create bookings/i);
        await userEvent.keyboard("{Escape}");
        await waitFor(() => expect(body.queryByRole("menu")).not.toBeInTheDocument());
      });
    }

    if (variant === "sentence-below") {
      await step("the sentence tracks the selected role and is tied to the control", async () => {
        const trigger = managerCard.getByRole("button", { name: "Direct role for Ada Lovelace" });
        expect(trigger).toHaveAccessibleDescription(/create bookings/i);
        await userEvent.click(trigger);
        await userEvent.click(await body.findByRole("menuitem", { name: /Viewer/ }));
        await waitFor(() => expect(trigger).toHaveAccessibleDescription(/view the schedule and subscribe/i));
      });
    }

    if (variant === "about-roles") {
      await step("the About roles disclosure compares all four roles once", async () => {
        const triggers = body.getAllByRole("button", { name: "About roles" });
        await userEvent.click(triggers[0]);
        const table = await managerCard.findByRole("table");
        expect(within(table).getAllByRole("row")).toHaveLength(5);
        expect(managerCard.getByText("At least one Owner must always remain.")).toBeInTheDocument();
      });
    }

    await step("320 px: descriptions fit without horizontal overflow", async () => {
      await userEvent.click(body.getByRole("checkbox", { name: /Constrain page to 320 px/ }));
      const page = canvasElement.querySelector(".space-y-4") as HTMLElement;
      await waitFor(() => expect(page.getBoundingClientRect().width).toBeLessThanOrEqual(320));
      expect(page.scrollWidth).toBeLessThanOrEqual(page.clientWidth + 1);
    });
  };

export const MenuDescriptions: Story = {
  args: { variant: "menu-descriptions" },
  play: import.meta.env.MODE === "test" ? runAcceptance("menu-descriptions") : undefined,
};

export const SentenceBelow: Story = {
  args: { variant: "sentence-below" },
  play: import.meta.env.MODE === "test" ? runAcceptance("sentence-below") : undefined,
};

export const AboutRoles: Story = {
  args: { variant: "about-roles" },
  play: import.meta.env.MODE === "test" ? runAcceptance("about-roles") : undefined,
};
