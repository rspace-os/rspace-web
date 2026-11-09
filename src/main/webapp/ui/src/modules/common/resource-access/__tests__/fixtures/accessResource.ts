import { apiV2Resources } from "@/modules/common/api/v2/apiV2Resources";
import {
  getLeaveBookingConfigurationAccessMutationOptions,
  getReplaceBookingConfigurationAccessMutationOptions,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";
import type { AccessMutationFactories, AccessResource } from "../../resourceAccess";

export const accessResource = apiV2Resources["booking-configurations"] satisfies AccessResource;
export const accessMutationOptions = {
  replaceAccess: getReplaceBookingConfigurationAccessMutationOptions,
  leaveAccess: getLeaveBookingConfigurationAccessMutationOptions,
} satisfies AccessMutationFactories;
