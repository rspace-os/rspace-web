import { useMutation, useQueryClient } from "@tanstack/react-query";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  DeleteStoichiometryResponseSchema,
  MoleculeInfo,
  MoleculeInfoSchema,
  StoichiometryRequest,
  StoichiometryRequestSchema,
  StoichiometryResponse,
  StoichiometryResponseSchema,
} from "@/modules/stoichiometry/schema";
import { stoichiometryQueryKeys } from "@/modules/stoichiometry/queries";
import {
  resolveToken,
  STOICHIOMETRY_API_BASE_URL,
  TokenParams,
  toStoichiometryError,
} from "@/modules/stoichiometry/utils";

export type CalculateStoichiometryParams = {
  recordId: number;
  chemId?: number;
  revision?: number;
};

export type UpdateStoichiometryParams = {
  stoichiometryId: number;
  stoichiometryData: StoichiometryRequest;
};

export type DeleteStoichiometryParams = {
  stoichiometryId: number;
};

export type GetMoleculeInfoParams = {
  smiles: string;
};

export type CalculateStoichiometryMutationParams = CalculateStoichiometryParams;
export type UpdateStoichiometryMutationParams = UpdateStoichiometryParams;
export type DeleteStoichiometryMutationParams = DeleteStoichiometryParams;
export type GetMoleculeInfoMutationParams = GetMoleculeInfoParams;

export async function calculateStoichiometry(
  {
    recordId,
    chemId,
    revision,
  }: CalculateStoichiometryParams,
  token: string,
): Promise<StoichiometryResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set("recordId", String(recordId));
  if (chemId !== undefined) {
    searchParams.set("chemId", String(chemId));
  }
  if (revision !== undefined) {
    searchParams.set("revision", String(revision));
  }

  const response = await fetch(
    `${STOICHIOMETRY_API_BASE_URL}/stoichiometry?${searchParams.toString()}`,
    {
      method: "POST",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${token}`,
      },
    },
  );

  const data: unknown = await response.json();
  if (!response.ok) {
    throw toStoichiometryError(
      data,
      `Failed to calculate stoichiometry: ${response.statusText}`,
    );
  }

  return parseOrThrow(StoichiometryResponseSchema, data);
}

export async function updateStoichiometry(
  {
    stoichiometryId,
    stoichiometryData,
  }: UpdateStoichiometryParams,
  token: string,
): Promise<StoichiometryResponse> {
  const searchParams = new URLSearchParams();
  searchParams.set("stoichiometryId", String(stoichiometryId));

  const requestBody = parseOrThrow(StoichiometryRequestSchema, stoichiometryData);

  const response = await fetch(
    `${STOICHIOMETRY_API_BASE_URL}/stoichiometry?${searchParams.toString()}`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(requestBody),
    },
  );

  const data: unknown = await response.json();
  if (!response.ok) {
    throw toStoichiometryError(
      data,
      `Failed to update stoichiometry: ${response.statusText}`,
    );
  }

  return parseOrThrow(StoichiometryResponseSchema, data);
}

export async function deleteStoichiometry(
  {
    stoichiometryId,
  }: DeleteStoichiometryParams,
  token: string,
): Promise<boolean> {
  const searchParams = new URLSearchParams();
  searchParams.set("stoichiometryId", String(stoichiometryId));

  const response = await fetch(
    `${STOICHIOMETRY_API_BASE_URL}/stoichiometry?${searchParams.toString()}`,
    {
      method: "DELETE",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${token}`,
      },
    },
  );

  let data: unknown = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }

  if (!response.ok) {
    throw toStoichiometryError(
      data,
      `Failed to delete stoichiometry: ${response.statusText}`,
    );
  }

  if (data === null) {
    return true;
  }

  const parsed = parseOrThrow(DeleteStoichiometryResponseSchema, data);
  return typeof parsed === "boolean" ? parsed : parsed.success;
}

export async function getMoleculeInfo(
  {
    smiles,
  }: GetMoleculeInfoParams,
  token: string,
): Promise<MoleculeInfo> {
  const response = await fetch(
    `${STOICHIOMETRY_API_BASE_URL}/stoichiometry/molecule/info`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({ chemical: smiles }),
    },
  );

  const data: unknown = await response.json();
  if (!response.ok) {
    throw toStoichiometryError(
      data,
      `Failed to retrieve molecule information: ${response.statusText}`,
    );
  }

  return parseOrThrow(MoleculeInfoSchema, data);
}

export function useCalculateStoichiometryMutation(tokenParams: TokenParams) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      recordId,
      chemId,
      revision,
    }: CalculateStoichiometryMutationParams) =>
      calculateStoichiometry(
        {
          recordId,
          chemId,
          revision,
        },
        await resolveToken(tokenParams),
      ),
    onSuccess: async (responseData) => {
      queryClient.setQueryData(
        stoichiometryQueryKeys.byId(responseData.id, responseData.revision),
        responseData,
      );
      await queryClient.invalidateQueries({
        queryKey: stoichiometryQueryKeys.all,
      });
    },
  });
}

export function useUpdateStoichiometryMutation(tokenParams: TokenParams) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      stoichiometryId,
      stoichiometryData,
    }: UpdateStoichiometryMutationParams) =>
      updateStoichiometry(
        {
          stoichiometryId,
          stoichiometryData,
        },
        await resolveToken(tokenParams),
      ),
    onSuccess: async (responseData) => {
      queryClient.setQueryData(
        stoichiometryQueryKeys.byId(responseData.id, responseData.revision),
        responseData,
      );
      await queryClient.invalidateQueries({
        queryKey: stoichiometryQueryKeys.all,
      });
    },
  });
}

export function useDeleteStoichiometryMutation(tokenParams: TokenParams) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ stoichiometryId }: DeleteStoichiometryMutationParams) =>
      deleteStoichiometry(
        {
          stoichiometryId,
        },
        await resolveToken(tokenParams),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: stoichiometryQueryKeys.all,
      });
    },
  });
}

export function useGetMoleculeInfoMutation(tokenParams: TokenParams) {
  return useMutation({
    mutationFn: async ({ smiles }: GetMoleculeInfoMutationParams) =>
      getMoleculeInfo(
        {
          smiles,
        },
        await resolveToken(tokenParams),
      ),
  });
}
