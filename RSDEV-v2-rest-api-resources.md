# REST API v2 resource status

This file records the resources in the first REST API v2 release.

Use `DevDocs/DeveloperNotes/RestApiV2Collections.md` for the current implementation rules. The
OpenAPI document at `/api/v2/openapi.json` is the public HTTP contract.

## Users

The `users` collection has standard collection routes. Its policy gives a system administrator
access to all users. Another authenticated user can read only their own row.

The `/api/v2/users/me` route is a concrete controller route. It returns the current API user.

User write operations exist in the standard route set. They do not change data. This temporary
behavior lets clients use one route shape while the team defines user write rules.

## Configuration

The `/api/v2/config` route returns a public allowlist of deployment properties. The implementation
does not reflect over all properties. It does not expose credentials or secret paths.

This route is not a registered collection. Its controller supplies its OpenAPI documentation.

## Maintenance

The `maintenances` collection exposes stored `ScheduledMaintenance` fields.

An anonymous caller can read a maintenance row only when the row is not deleted and its end date
is in the future. A system administrator can read all rows and can run write operations.

The collection supports list, count, read, create, bulk create, update, bulk update, delete, and
bulk delete operations. `MaintenanceManager` owns domain validation and authorization.

The response does not contain formatted date fields or time-dependent helper fields. Clients use
the stored ISO-8601 dates.

## Booking configuration

The `booking-configurations` collection stores the booking state for an inventory target. The
current implementation supports instruments. The stored target uses a typed reference.

The collection uses the existing audit infrastructure. The default resource audit routes expose
the permitted audit data.

See `RSDEV-1187-booking-design.md` for future booking and scheduling decisions.
