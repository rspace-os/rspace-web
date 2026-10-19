// PROTOTYPE ONLY. Three Storybook concepts for the proposed recurring-booking flow.
/* biome-ignore-all lint/style/noJsxLiterals: throwaway Storybook copy is intentionally not entering the translation catalog. */

import { InfoIcon } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { BookingSummaryCard } from "@/modules/booking/components/BookingSummaryCard";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import { Alert, AlertDescription, AlertTitle } from "@/modules/common/ui/alert";
import { Badge } from "@/modules/common/ui/badge";
import { Button } from "@/modules/common/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/modules/common/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/common/ui/dialog";
import { Label } from "@/modules/common/ui/label";
import { RadioGroup, RadioGroupItem } from "@/modules/common/ui/radio-group";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/modules/common/ui/table";
import { Textarea } from "@/modules/common/ui/textarea";
import { Heading } from "@/modules/common/ui/typography";
import type { Inspection } from "./RecurringBookingCodexEntryConcepts.story";
import { formatDate } from "./RecurringBookingCodexPreview.story";
import { workspaceOccurrences } from "./recurringBookingCodexFixtures";

const cancellationReasonMaxLength = 500;

type WorkspaceRow = (typeof workspaceOccurrences)[number];
type EditScope = "THIS" | "FOLLOWING";
type CancelScope = EditScope | "SERIES";

function workspaceDocument(row: WorkspaceRow): BookingListDocument {
  return {
    id: row.id,
    version: 2,
    target: {
      relationTo: "booking-instruments",
      value: {
        id: 123,
        name: "Confocal microscope",
        deleted: false,
        parentContainerName: "Imaging lab",
        parentContainerGlobalId: "IC456",
      },
      globalId: "IN123",
    },
    canViewConfiguration: true,
    timezone: "Europe/London",
    start: row.startInstant,
    end: row.endInstant,
    state: row.cancelled ? "CANCELLED" : "CONFIRMED",
    kind: "BOOKING",
    requesterId: 84,
    purpose: "Cell imaging protocol",
    bookedBy: "Ada Lovelace (ada)",
    createdBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: !row.isPast && !row.cancelled,
    canCancel: !row.isPast && !row.cancelled,
    createdAt: "2026-08-01T09:00:00Z",
    updatedAt: "2026-08-02T10:00:00Z",
  };
}

function actionRows(scope: CancelScope, selected: WorkspaceRow): WorkspaceRow[] {
  return workspaceOccurrences.filter((row) => {
    if (row.isPast || row.cancelled) return false;
    if (scope === "THIS") return row.id === selected.id;
    if (scope === "FOLLOWING") return row.position >= selected.position;
    return true;
  });
}

function rangeLabel(rows: readonly WorkspaceRow[]): string {
  if (!rows.length) return "No confirmed future bookings are affected.";
  return `${rows.length} confirmed booking${rows.length === 1 ? "" : "s"} · ${formatDate(rows[0].date)} to ${formatDate(rows[rows.length - 1].date)}`;
}

function ScopeDialog({
  open,
  onOpenChange,
  mode,
  selected,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "edit" | "cancel";
  selected: WorkspaceRow;
  onConfirm: (summary: string) => void;
}) {
  const [scope, setScope] = useState<CancelScope>("THIS");
  const [reason, setReason] = useState("");
  const reasonId = useId();
  const wasOpen = useRef(false);
  const rows = actionRows(scope, selected);
  const isCancel = mode === "cancel";
  useEffect(() => {
    if (open && !wasOpen.current) {
      setScope("THIS");
      setReason("");
    }
    wasOpen.current = open;
  }, [open]);
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isCancel ? "Cancel booking" : "Edit booking"} · #{selected.position} of 12
          </DialogTitle>
          <DialogDescription>Choose which confirmed future occurrences this action covers.</DialogDescription>
        </DialogHeader>
        <RadioGroup
          aria-label="Series action scope"
          value={scope}
          onValueChange={(value) => setScope(value as CancelScope)}
          className="gap-3"
        >
          <Label className="flex items-start gap-3 rounded-sm border p-3">
            <RadioGroupItem value="THIS" className="mt-0.5" />
            <span>
              <span className="font-medium">This booking</span>
              <span className="mt-1 block text-sm text-muted-foreground">
                {rangeLabel(actionRows("THIS", selected))}
              </span>
            </span>
          </Label>
          <Label className="flex items-start gap-3 rounded-sm border p-3">
            <RadioGroupItem value="FOLLOWING" className="mt-0.5" />
            <span>
              <span className="font-medium">This and following</span>
              <span className="mt-1 block text-sm text-muted-foreground">
                {rangeLabel(actionRows("FOLLOWING", selected))}
              </span>
            </span>
          </Label>
          {isCancel ? (
            <Label className="flex items-start gap-3 rounded-sm border p-3">
              <RadioGroupItem value="SERIES" className="mt-0.5" />
              <span>
                <span className="font-medium">All remaining in series</span>
                <span className="mt-1 block text-sm text-muted-foreground">
                  {rangeLabel(actionRows("SERIES", selected))}
                </span>
              </span>
            </Label>
          ) : null}
        </RadioGroup>
        {isCancel ? (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">Past and already cancelled bookings are excluded.</p>
            <div className="space-y-2">
              <Label htmlFor={reasonId}>Cancellation reason (optional)</Label>
              <Textarea
                id={reasonId}
                value={reason}
                maxLength={cancellationReasonMaxLength}
                rows={3}
                aria-describedby={`${reasonId}-count`}
                onChange={(event) => setReason(event.currentTarget.value)}
              />
              <p id={`${reasonId}-count`} className="text-right text-xs text-muted-foreground" aria-live="polite">
                {reason.length}/{cancellationReasonMaxLength}
              </p>
            </div>
          </div>
        ) : null}
        {!isCancel && scope === "FOLLOWING" ? (
          <Alert>
            <InfoIcon />
            <AlertTitle>Following bookings will be recreated</AlertTitle>
            <AlertDescription>
              Any earlier moved occurrence in this range is replaced by the new series.
            </AlertDescription>
          </Alert>
        ) : null}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Close
          </Button>
          <Button
            variant={isCancel ? "destructive" : "default"}
            disabled={rows.length === 0}
            onClick={() =>
              onConfirm(
                `${isCancel ? "Cancellation" : "Edit"} scope selected · ${rangeLabel(rows)}${isCancel && reason.trim() ? ` · Reason: ${reason.trim()}` : ""} · no changes applied in this prototype`,
              )
            }
          >
            {isCancel ? "Review cancellation scope" : "Continue to edit"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function WorkspaceConcept({ reportState }: { reportState: (state: Inspection) => void }) {
  const [selectedPosition, setSelectedPosition] = useState(6);
  const [editOpen, setEditOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [actionNotice, setActionNotice] = useState("");
  const selected = workspaceOccurrences.find((row) => row.position === selectedPosition) ?? workspaceOccurrences[5];
  useEffect(
    () => reportState({ concept: "workspace", selectedPosition, editOpen, cancelOpen, actionNotice }),
    [actionNotice, cancelOpen, editOpen, reportState, selectedPosition],
  );
  return (
    <main className="mx-auto max-w-7xl space-y-5 p-5 lg:p-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">Concept 3 · manage one series as a workspace</p>
          <Heading level={3} as="h2">
            Recurring bookings
          </Heading>
        </div>
        <Badge variant="outline">12 occurrences · Weekly on Mondays · Europe/London</Badge>
      </div>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,0.7fr)]">
        <Card size="sm" className="gap-0 py-0">
          <CardHeader className="border-b py-4">
            <CardTitle className="text-base">Confocal microscope · Cell imaging protocol</CardTitle>
            <p className="text-xs text-muted-foreground">Position labels stay stable when one occurrence is moved.</p>
          </CardHeader>
          <div className="[&_td]:whitespace-normal [&_th]:whitespace-normal">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Position</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Time</TableHead>
                  <TableHead>State</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {workspaceOccurrences.map((row) => (
                  <TableRow
                    key={row.id}
                    data-state={row.position === selected.position ? "selected" : undefined}
                    className={row.position === selected.position ? "bg-muted" : ""}
                  >
                    <TableCell>
                      <Button
                        variant="link"
                        size="sm"
                        className="h-auto p-0"
                        aria-pressed={row.position === selected.position}
                        onClick={() => setSelectedPosition(row.position)}
                      >
                        {row.position} of 12
                      </Button>
                    </TableCell>
                    <TableCell>{formatDate(row.date)}</TableCell>
                    <TableCell>
                      {row.start}–{row.end}
                    </TableCell>
                    <TableCell>
                      {row.cancelled ? (
                        <Badge variant="secondary">Cancelled</Badge>
                      ) : row.isPast ? (
                        <Badge variant="outline">Past</Badge>
                      ) : row.moved ? (
                        <Badge variant="outline">Moved</Badge>
                      ) : (
                        <Badge variant="secondary">Confirmed</Badge>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Card>
        <aside className="space-y-4">
          <div className="flex items-center gap-2">
            <h3 className="font-medium">Occurrence detail</h3>
            <Badge variant="outline">{selected.position} of 12</Badge>
          </div>
          <BookingSummaryCard booking={workspaceDocument(selected)} timeZone="Europe/London" />
          <Card size="sm">
            <CardHeader>
              <CardTitle className="text-base">Series facts</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <p>
                <span className="text-muted-foreground">Repeat:</span> Weekly on Mondays
              </p>
              <p>
                <span className="text-muted-foreground">Selected:</span> {formatDate(selected.date)} · 10:00–11:30
              </p>
              {selected.moved ? (
                <p className="text-muted-foreground">This occurrence was moved from its original time.</p>
              ) : null}
              <p className="text-xs text-muted-foreground">
                Past and cancelled occurrences remain visible in history and are omitted from scope counts.
              </p>
            </CardContent>
          </Card>
          {actionNotice ? (
            <Alert>
              <InfoIcon />
              <AlertTitle>Prototype scope selection</AlertTitle>
              <AlertDescription>{actionNotice}</AlertDescription>
            </Alert>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              disabled={selected.isPast || selected.cancelled}
              onClick={() => {
                setActionNotice("");
                setEditOpen(true);
              }}
            >
              Edit scope
            </Button>
            <Button
              variant="destructive"
              disabled={selected.isPast || selected.cancelled}
              onClick={() => {
                setActionNotice("");
                setCancelOpen(true);
              }}
            >
              Cancel scope
            </Button>
          </div>
        </aside>
      </div>
      <ScopeDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        mode="edit"
        selected={selected}
        onConfirm={(notice) => {
          setEditOpen(false);
          setActionNotice(notice);
        }}
      />
      <ScopeDialog
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        mode="cancel"
        selected={selected}
        onConfirm={(notice) => {
          setCancelOpen(false);
          setActionNotice(notice);
        }}
      />
    </main>
  );
}
