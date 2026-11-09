import type { QueryKey } from "@tanstack/react-query";
import * as bookingcatalogue from "./generated/booking-catalogue/booking-catalogue.ts";
import * as bookingconfigurationtargets from "./generated/booking-configuration-targets/booking-configuration-targets.ts";
import * as bookingconfigurations from "./generated/booking-configurations/booking-configurations.ts";
import * as bookingsettings from "./generated/booking-settings/booking-settings.ts";
import * as bookings from "./generated/bookings/bookings.ts";
import * as config from "./generated/config/config.ts";
import * as containers from "./generated/containers/containers.ts";
import * as featureflags from "./generated/feature-flags/feature-flags.ts";
import * as instruments from "./generated/instruments/instruments.ts";
import * as maintenances from "./generated/maintenances/maintenances.ts";
import * as oauth from "./generated/oauth/oauth.ts";
import * as openapijson from "./generated/openapi-json/openapi-json.ts";
import * as users from "./generated/users/users.ts";

type ApiV2AsyncFunction = (...args: never[]) => Promise<unknown>;
type ApiV2KeyFunction = (...args: never[]) => QueryKey;

export interface ApiV2ReadOperation<
  TRequest extends ApiV2AsyncFunction = ApiV2AsyncFunction,
  TQueryKey extends ApiV2KeyFunction = ApiV2KeyFunction,
> {
  request: TRequest;
  queryKey: TQueryKey;
}

export interface ApiV2OperationSet {
  list?: ApiV2ReadOperation;
  count?: ApiV2ReadOperation;
  get?: ApiV2ReadOperation;
  create?: ApiV2AsyncFunction;
  update?: ApiV2AsyncFunction;
  delete?: ApiV2AsyncFunction;
  createMany?: ApiV2AsyncFunction;
  updateMany?: ApiV2AsyncFunction;
  deleteMany?: ApiV2AsyncFunction;
  access?: ApiV2ReadOperation;
  replaceAccess?: ApiV2AsyncFunction;
  leaveAccess?: ApiV2AsyncFunction;
  grantees?: ApiV2ReadOperation;
  audit?: ApiV2ReadOperation;
  runtimeFields?: ApiV2ReadOperation;
}

export type ApiV2ResourceEntry<TName extends string, TOperations extends ApiV2OperationSet> = {
  readonly name: TName;
} & TOperations;

export const apiV2Resources = {
  "booking-configurations": {
    name: "booking-configurations",
    list: {
      request: bookingconfigurations.listBookingConfigurations,
      queryKey: bookingconfigurations.getListBookingConfigurationsQueryKey,
    },
    count: {
      request: bookingconfigurations.countBookingConfigurations,
      queryKey: bookingconfigurations.getCountBookingConfigurationsQueryKey,
    },
    get: {
      request: bookingconfigurations.getBookingConfiguration,
      queryKey: bookingconfigurations.getGetBookingConfigurationQueryKey,
    },
    create: bookingconfigurations.createBookingConfiguration,
    update: bookingconfigurations.updateBookingConfiguration,
    delete: bookingconfigurations.deleteBookingConfiguration,
    createMany: bookingconfigurations.createManyBookingConfigurations,
    updateMany: bookingconfigurations.updateManyBookingConfigurations,
    deleteMany: bookingconfigurations.deleteManyBookingConfigurations,
    access: {
      request: bookingconfigurations.getBookingConfigurationAccess,
      queryKey: bookingconfigurations.getGetBookingConfigurationAccessQueryKey,
    },
    replaceAccess: bookingconfigurations.replaceBookingConfigurationAccess,
    leaveAccess: bookingconfigurations.leaveBookingConfigurationAccess,
    grantees: {
      request: bookingconfigurations.searchBookingConfigurationAccessGrantees,
      queryKey: bookingconfigurations.getSearchBookingConfigurationAccessGranteesQueryKey,
    },
    audit: {
      request: bookingconfigurations.listBookingConfigurationAuditEvents,
      queryKey: bookingconfigurations.getListBookingConfigurationAuditEventsQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"booking-configurations", ApiV2OperationSet>,
  bookings: {
    name: "bookings",
    list: { request: bookings.listBookings, queryKey: bookings.getListBookingsQueryKey },
    count: { request: bookings.countBookings, queryKey: bookings.getCountBookingsQueryKey },
    get: { request: bookings.getBooking, queryKey: bookings.getGetBookingQueryKey },
    create: bookings.createBooking,
    update: bookings.updateBooking,
    audit: { request: bookings.listBookingAuditEvents, queryKey: bookings.getListBookingAuditEventsQueryKey },
  } satisfies ApiV2ResourceEntry<"bookings", ApiV2OperationSet>,
  containers: {
    name: "containers",
    list: { request: containers.listContainers, queryKey: containers.getListContainersQueryKey },
    count: { request: containers.countContainers, queryKey: containers.getCountContainersQueryKey },
    get: { request: containers.getContainer, queryKey: containers.getGetContainerQueryKey },
    audit: { request: containers.listContainerAuditEvents, queryKey: containers.getListContainerAuditEventsQueryKey },
  } satisfies ApiV2ResourceEntry<"containers", ApiV2OperationSet>,
  "feature-flags": {
    name: "feature-flags",
    list: { request: featureflags.listFeatureFlags, queryKey: featureflags.getListFeatureFlagsQueryKey },
    count: { request: featureflags.countFeatureFlags, queryKey: featureflags.getCountFeatureFlagsQueryKey },
    get: { request: featureflags.getFeatureFlag, queryKey: featureflags.getGetFeatureFlagQueryKey },
    update: featureflags.updateFeatureFlag,
    audit: {
      request: featureflags.listFeatureFlagAuditEvents,
      queryKey: featureflags.getListFeatureFlagAuditEventsQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"feature-flags", ApiV2OperationSet>,
  instruments: {
    name: "instruments",
    list: { request: instruments.listInstruments, queryKey: instruments.getListInstrumentsQueryKey },
    count: { request: instruments.countInstruments, queryKey: instruments.getCountInstrumentsQueryKey },
    get: { request: instruments.getInstrument, queryKey: instruments.getGetInstrumentQueryKey },
    audit: {
      request: instruments.listInstrumentAuditEvents,
      queryKey: instruments.getListInstrumentAuditEventsQueryKey,
    },
    runtimeFields: {
      request: instruments.getInstrumentRuntimeFields,
      queryKey: instruments.getGetInstrumentRuntimeFieldsQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"instruments", ApiV2OperationSet>,
  maintenances: {
    name: "maintenances",
    list: { request: maintenances.listMaintenances, queryKey: maintenances.getListMaintenancesQueryKey },
    count: { request: maintenances.countMaintenances, queryKey: maintenances.getCountMaintenancesQueryKey },
    get: { request: maintenances.getMaintenance, queryKey: maintenances.getGetMaintenanceQueryKey },
    create: maintenances.createMaintenance,
    update: maintenances.updateMaintenance,
    delete: maintenances.deleteMaintenance,
    createMany: maintenances.createManyMaintenances,
    updateMany: maintenances.updateManyMaintenances,
    deleteMany: maintenances.deleteManyMaintenances,
    audit: {
      request: maintenances.listMaintenanceAuditEvents,
      queryKey: maintenances.getListMaintenanceAuditEventsQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"maintenances", ApiV2OperationSet>,
  users: {
    name: "users",
    list: { request: users.listUsers, queryKey: users.getListUsersQueryKey },
    count: { request: users.countUsers, queryKey: users.getCountUsersQueryKey },
    get: { request: users.getUser, queryKey: users.getGetUserQueryKey },
    audit: { request: users.listUserAuditEvents, queryKey: users.getListUserAuditEventsQueryKey },
  } satisfies ApiV2ResourceEntry<"users", ApiV2OperationSet>,
  "booking-catalogue": {
    name: "booking-catalogue",
    list: {
      request: bookingcatalogue.searchBookingCatalogue,
      queryKey: bookingcatalogue.getSearchBookingCatalogueQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"booking-catalogue", ApiV2OperationSet>,
  "booking-configuration-targets": {
    name: "booking-configuration-targets",
    list: {
      request: bookingconfigurationtargets.searchBookingConfigurationTargets,
      queryKey: bookingconfigurationtargets.getSearchBookingConfigurationTargetsQueryKey,
    },
  } satisfies ApiV2ResourceEntry<"booking-configuration-targets", ApiV2OperationSet>,
  "booking-settings": {
    name: "booking-settings",
    get: { request: bookingsettings.getBookingSettings, queryKey: bookingsettings.getGetBookingSettingsQueryKey },
  } satisfies ApiV2ResourceEntry<"booking-settings", ApiV2OperationSet>,
  config: {
    name: "config",
    get: { request: config.getApiV2Config, queryKey: config.getGetApiV2ConfigQueryKey },
  } satisfies ApiV2ResourceEntry<"config", ApiV2OperationSet>,
  oauth: { name: "oauth", create: oauth.createUiOAuthToken } satisfies ApiV2ResourceEntry<"oauth", ApiV2OperationSet>,
  "openapi.json": {
    name: "openapi.json",
    get: { request: openapijson.getApiV2OpenApiDocument, queryKey: openapijson.getGetApiV2OpenApiDocumentQueryKey },
  } satisfies ApiV2ResourceEntry<"openapi.json", ApiV2OperationSet>,
};
