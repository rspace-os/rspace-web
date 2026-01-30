import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  AddRaidIdentifierAssociationResponse,
  AssociateRaidIdentifierRequestBody,
  DeleteRaidIdentifierAssociationResponse,
} from "@/modules/raid/schema";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { AjaxOperationFailureResponseSchema } from "@/modules/common/api/schema";
import { groupQueryKeys } from "@/modules/groups/queries";

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
    } as AssociateRaidIdentifierRequestBody),
  });

  if (!response.ok) {
    throw new Error(`Failed to add RaID identifier: ${response.statusText}`);
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
    mutationFn: ({
      raidServerAlias,
      raidIdentifier,
    }: {
      raidServerAlias: string;
      raidIdentifier: string;
    }) => addRaidIdentifierAjax({ groupId, raidServerAlias, raidIdentifier }),
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
    throw new Error(`Failed to remove RaID identifier: ${response.statusText}`);
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
}