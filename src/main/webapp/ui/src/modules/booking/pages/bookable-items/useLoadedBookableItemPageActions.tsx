import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { leaveResource } from "@/modules/common/resource-access/resourceAccess";
import type { BookingConfigurationLifecycleAction } from "./BookingConfigurationActionsMenu";
import { calendarSubscriptionQueryKey } from "./bookableItemCalendarSubscription";
import {
  archiveBookingConfiguration,
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
  invalidateBookingCatalogueQueries,
  permanentlyDeleteBookingConfiguration,
  restoreBookingConfiguration,
} from "./bookingConfiguration";

export type BookableItemTab = "bookings" | "details" | "audit" | "access";

async function updateBookingConfiguration(
  id: number,
  version: number,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/booking-configurations/${id}?${search}`, {
    method: "PATCH",
    headers: apiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

type LoadedBookableItemPageActionsOptions = {
  configuration: BookingConfiguration;
  globalId: string;
  tab: BookableItemTab;
  token: string;
};

export function useLoadedBookableItemPageActions({
  configuration,
  globalId,
  tab,
  token,
}: LoadedBookableItemPageActionsOptions) {
  const { edit = false } = useSearch({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const queryClient = useQueryClient();
  const [saveAnnouncement, setSaveAnnouncement] = useState<"saved" | "archived" | "restored" | null>(null);
  const [staleEdit, setStaleEdit] = useState(false);
  const [rulesSaveBlocked, setRulesSaveBlocked] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [permanentDeleteOpen, setPermanentDeleteOpen] = useState(false);
  const [permanentDeleteConfirmation, setPermanentDeleteConfirmation] = useState("");
  const [leaveOpen, setLeaveOpen] = useState(false);
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const saveButtonRef = useRef<HTMLButtonElement>(null);
  const actionsButtonRef = useRef<HTMLButtonElement>(null);
  const wasEditing = useRef(false);
  const canEdit = configuration.capabilities.canEditConfiguration;
  const active = configuration.state === "ACTIVE";
  const editing = active && canEdit && edit;
  const setEdit = (next: boolean) =>
    void navigate({
      search: next ? { edit: true } : {},
      replace: true,
    });

  const setTab = (next: BookableItemTab) =>
    void navigate({
      to: "/booking/bookable-items/$globalId/{-$tab}",
      params: { globalId, tab: next === "bookings" ? undefined : next },
      search: edit ? { edit: true } : {},
      replace: true,
      resetScroll: false,
    });

  const updateMutation = useMutation({
    mutationFn: ({ input, version }: { input: BookingConfigurationUpdateInput; version: number }) =>
      updateBookingConfiguration(configuration.id, version, input, token),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        invalidateBookingCatalogueQueries(queryClient),
      ]);
      setSaveAnnouncement("saved");
      setStaleEdit(false);
      setEdit(false);
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        setStaleEdit(true);
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const archiveMutation = useMutation({
    mutationFn: () => archiveBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-catalogue"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setArchiveOpen(false);
      setSaveAnnouncement("archived");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const restoreMutation = useMutation({
    mutationFn: () => restoreBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-catalogue"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setSaveAnnouncement("restored");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const permanentDeleteMutation = useMutation({
    mutationFn: () =>
      permanentlyDeleteBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] });
      setPermanentDeleteOpen(false);
      void navigate({ to: "/booking/config/bookable-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && (error.status === 409 || error.status === 412)) {
        await queryClient.refetchQueries({
          queryKey: ["api-v2", "booking-configurations", "target", globalId],
        });
      }
    },
  });
  const leaveMutation = useMutation({
    mutationFn: () => leaveResource("booking-configurations", configuration.id, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        invalidateBookingCatalogueQueries(queryClient),
      ]);
      void navigate({ to: "/booking", ignoreBlocker: true });
    },
  });

  useEffect(() => {
    if (wasEditing.current && !editing) editButtonRef.current?.focus();
    wasEditing.current = editing;
  }, [editing]);

  useEffect(() => {
    if (updateMutation.isError && !updateMutation.isPending) saveButtonRef.current?.focus();
  }, [updateMutation.isError, updateMutation.isPending]);

  useEffect(() => {
    // onSuccess runs before the mutation clears pending and re-enables this button.
    if (
      archiveMutation.isPending ||
      restoreMutation.isPending ||
      (!archiveMutation.isSuccess && !restoreMutation.isSuccess)
    ) {
      return;
    }
    const frame = requestAnimationFrame(() => actionsButtonRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [archiveMutation.isPending, archiveMutation.isSuccess, restoreMutation.isPending, restoreMutation.isSuccess]);

  useEffect(() => {
    if (!active && edit) setEdit(false);
  }, [active, edit]);

  useEffect(() => {
    if (tab === "audit" && !configuration.capabilities.canViewAudit) {
      setTab("bookings");
    }
  }, [configuration.capabilities.canViewAudit, tab]);

  const cancelEdit = () => {
    setSaveAnnouncement(null);
    setStaleEdit(false);
    setEdit(false);
  };

  const handleLifecycleAction = (action: BookingConfigurationLifecycleAction) => {
    if (action === "archive") setArchiveOpen(true);
    if (action === "restore") restoreMutation.mutate();
    if (action === "permanent-delete") {
      setPermanentDeleteConfirmation("");
      setPermanentDeleteOpen(true);
    }
  };

  return {
    saveAnnouncement,
    staleEdit,
    archiveOpen,
    setArchiveOpen,
    permanentDeleteOpen,
    setPermanentDeleteOpen,
    permanentDeleteConfirmation,
    setPermanentDeleteConfirmation,
    leaveOpen,
    setLeaveOpen,
    editButtonRef,
    saveButtonRef,
    actionsButtonRef,
    canEdit,
    active,
    editing,
    setEdit,
    setTab,
    updateMutation,
    archiveMutation,
    restoreMutation,
    permanentDeleteMutation,
    leaveMutation,
    cancelEdit,
    handleLifecycleAction,
    rulesSaveBlocked,
    setRulesSaveBlocked,
  };
}
