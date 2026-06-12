import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AjaxOperationFailureResponseSchema } from "@/modules/common/api/schema";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { groupQueryKeys } from "@/modules/groups/queries";
// biome-ignore lint/style/useImportType: initial biome migration
import {
  AddRaidIdentifierAssociationResponse,
  // biome-ignore lint/correctness/noUnusedImports: initial biome migration
  AssociateRaidIdentifierRequestBody,
  DeleteRaidIdentifierAssociationResponse,
} from "@/modules/raid/schema";

export const addRaidIdentifierAjax = async ({
  groupId,
  raidServerAlias,
  raidIdentifier,
}: {
  groupId: string;
  raidServerAlias: string;
  raidIdentifier: string;
}): Promise<AddRaidIdentifierAssociationResponse> => {
  const response = await fetch(`/apps/raid/associate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
    },
    body: JSON.stringify({
      projectGroupId: Number(groupId),
      raid: {
        raidServerAlias,
        raidIdentifier,
      },
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to add RAiD identifier: ${response.statusText}`);
  }

  if (response.status !== 201) {
    // Success responses return 201 with empty body
    const data: unknown = await response.json();

    return parseOrThrow(AjaxOperationFailureResponseSchema, data);
  }

  return true;
};

export const useAddRaidIdentifierMutation = ({ groupId }: { groupId: string }) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ raidServerAlias, raidIdentifier }: { raidServerAlias: string; raidIdentifier: string }) =>
      addRaidIdentifierAjax({ groupId, raidServerAlias, raidIdentifier }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: groupQueryKeys.groupById(groupId),
      });
    },
  });
};

export const removeRaidIdentifierAjax = async ({
  groupId,
}: {
  groupId: string;
}): Promise<DeleteRaidIdentifierAssociationResponse> => {
  const response = await fetch(`/apps/raid/disassociate/${groupId}`, {
    method: "POST",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to remove RAiD identifier: ${response.statusText}`);
  }

  if (response.status !== 201) {
    // Success responses return 201 with empty body
    const data: unknown = await response.json();

    return parseOrThrow(AjaxOperationFailureResponseSchema, data);
  }

  return true;
};

export const useRemoveRaidIdentifierMutation = ({ groupId }: { groupId: string }) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => removeRaidIdentifierAjax({ groupId }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: groupQueryKeys.groupById(groupId) });
    },
  });
};
