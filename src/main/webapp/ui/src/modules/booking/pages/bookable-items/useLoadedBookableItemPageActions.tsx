import { reset, useForm } from "@formisch/react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import * as v from "valibot";
import {
  SchedulingSettingsSchema,
  schedulingSettingsEntries,
} from "@/modules/booking/configuration/schedulingSettings";
import {
  invalidateListBookingConfigurations,
  useDeleteBookingConfiguration,
  useLeaveBookingConfigurationAccess,
  useUpdateBookingConfiguration,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import { type BookingConfigurationsUpdate } from "@/modules/common/api/v2/generated/models/bookingConfigurationsUpdate";
import { BookingConfigurationsUpdateSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import { type BookingConfigurationLifecycleAction } from "./BookingConfigurationActionsMenu";
import { type BookingConfiguration } from "./bookingConfiguration";
import { bookingConfigurationTargetQuery } from "./bookingConfigurationApi";

export const bookableItemConfigurationFormSchema = v.intersect([
  v.object({ ...BookingConfigurationsUpdateSchema.entries, enabled: v.boolean(), ...schedulingSettingsEntries }),
  SchedulingSettingsSchema,
]);

export type BookableItemTab = "bookings" | "details" | "audit" | "access";

export function configurationInput(configuration: BookingConfiguration) {
  return {
    enabled: configuration.enabled,
    slotGranularityMinutes: configuration.slotGranularityMinutes,
    incrementUnit: configuration.incrementUnit,
    openingHours: configuration.openingHours.map((entry) => ({ ...entry })),
    bufferBeforeMinutes: configuration.bufferBeforeMinutes,
    bufferAfterMinutes: configuration.bufferAfterMinutes,
    maxBookingDurationMinutes: configuration.maxBookingDurationMinutes,
    allowDoubleBooking: configuration.allowDoubleBooking,
  };
}

export function useLoadedBookableItemPageActions({
  configuration,
  globalId,
  tab,
}: {
  configuration: BookingConfiguration;
  globalId: string;
  tab: BookableItemTab;
}) {
  const { edit = false } = useSearch({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const queryClient = useQueryClient();
  const [saveAnnouncement, setSaveAnnouncement] = useState<"saved" | "archived" | "restored" | null>(null);
  const [staleEdit, setStaleEdit] = useState(false);
  const [baseVersion, setBaseVersion] = useState(configuration.configurationVersion);
  const [openedEnabled, setOpenedEnabled] = useState(configuration.enabled);
  const [pendingDisable, setPendingDisable] = useState<{
    input: BookingConfigurationsUpdate;
    version: number;
  } | null>(null);
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
  const form = useForm({
    schema: bookableItemConfigurationFormSchema,
    initialInput: configurationInput(configuration),
  });
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
  const updateOperation = useUpdateBookingConfiguration();
  const deleteOperation = useDeleteBookingConfiguration();
  const leaveOperation = useLeaveBookingConfigurationAccess();
  const updateMutation = useMutation({
    mutationFn: ({ input, version }: { input: BookingConfigurationsUpdate; version: number }) =>
      updateOperation.mutateAsync({ id: configuration.id, data: input, headers: { "If-Match": `"${version}"` } }),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async () => {
      await invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false });
      setSaveAnnouncement("saved");
      setStaleEdit(false);
      setEdit(false);
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        setStaleEdit(true);
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQuery(globalId).queryKey,
        });
      }
    },
  });
  const archiveMutation = useMutation({
    mutationFn: () =>
      deleteOperation.mutateAsync({
        id: configuration.id,
        headers: { "If-Match": `"${configuration.configurationVersion}"` },
      }),
    onSuccess: async () => {
      await invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false });
      setArchiveOpen(false);
      setSaveAnnouncement("archived");
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQuery(globalId).queryKey,
        });
      }
    },
  });
  const restoreMutation = useMutation({
    mutationFn: () =>
      updateOperation.mutateAsync({
        id: configuration.id,
        data: { state: "ACTIVE" },
        headers: { "If-Match": `"${configuration.configurationVersion}"` },
      }),
    onSuccess: async () => {
      await invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false });
      setSaveAnnouncement("restored");
    },
    onError: async (error) => {
      if (typeof error === "object" && error !== null && "status" in error && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQuery(globalId).queryKey,
        });
      }
    },
  });
  const permanentDeleteMutation = useMutation({
    mutationFn: () =>
      deleteOperation.mutateAsync({
        id: configuration.id,
        headers: { "If-Match": `"${configuration.configurationVersion}"` },
        params: { permanent: true },
      }),
    onSuccess: async () => {
      await invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false });
      setPermanentDeleteOpen(false);
      void navigate({ to: "/booking/config/bookable-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (
        typeof error === "object" &&
        error !== null &&
        "status" in error &&
        (error.status === 409 || error.status === 412)
      ) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQuery(globalId).queryKey,
        });
      }
    },
  });
  const leaveMutation = useMutation({
    mutationFn: () => leaveOperation.mutateAsync({ id: configuration.id }),
    onSuccess: async () => {
      await invalidateListBookingConfigurations(queryClient, undefined, { cancelRefetch: false });
      void navigate({ to: "/booking", ignoreBlocker: true });
    },
  });
  useEffect(() => {
    if (!editing) {
      reset(form, { initialInput: configurationInput(configuration) });
      setBaseVersion(configuration.configurationVersion);
      setOpenedEnabled(configuration.enabled);
      setPendingDisable(null);
    }
  }, [configuration, editing, form]);
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
    if (
      (tab === "audit" && !configuration.capabilities.canViewAudit) ||
      (tab === "access" && !configuration.capabilities.canViewAccess)
    ) {
      setTab("bookings");
    }
  }, [configuration.capabilities.canViewAccess, configuration.capabilities.canViewAudit, tab]);
  const cancelEdit = () => {
    reset(form, { initialInput: configurationInput(configuration) });
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
    actionsButtonRef,
    active,
    archiveMutation,
    archiveOpen,
    baseVersion,
    canEdit,
    cancelEdit,
    editButtonRef,
    editing,
    form,
    handleLifecycleAction,
    leaveMutation,
    leaveOpen,
    navigate,
    openedEnabled,
    pendingDisable,
    permanentDeleteConfirmation,
    permanentDeleteMutation,
    permanentDeleteOpen,
    restoreMutation,
    saveAnnouncement,
    saveButtonRef,
    setArchiveOpen,
    setEdit,
    setLeaveOpen,
    setPendingDisable,
    setPermanentDeleteConfirmation,
    setPermanentDeleteOpen,
    setTab,
    staleEdit,
    updateMutation,
  };
}
