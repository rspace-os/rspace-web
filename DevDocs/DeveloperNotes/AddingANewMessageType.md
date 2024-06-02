# Steps

## Create a new message type

Create a new MessageType enum - decide what the available responses to
the request are, and if it is 'yes/no' binary decision.

## Add permissions

Add permissions as to who can create such a request:
- for an RSpace Role-based permission: in a Liquibase changeset to be
  applied to all liquibase contexts.
- for a group-role permission - in `DefaultPermissionFactory`

## Edit RequestFactory

Create the appropriate MessageOrRequest subclass for the Message Type if
necessary.

## Implement a RSpaceRequestHandler for the request

There are 2 types of handler that add request-specific functionality
into the Request mechanism:
- `RSpaceRequestOnCreateHandler` will run when a message is first created
- `RSpaceRequestUpdateHandler` is called when each respondent replies to
  the message

You might want to implement 0,1 or 2 handlers for your new message type.
- register implementations as Spring beans in `com.axiope.service.cfg`
  and register in `BaseConfig` `addRequestHandlers`.

## Add any custom messages

We've started using Velocity templates for notification messages - these
are like JSPs but can be processed by the application, not the
container, so can be used in emails. If need be, create a new `.vm` file in
`WEB-INF/velocityTemplates` and configure `RequestNotificationMessageGenerator`
class to parameterize the template.
