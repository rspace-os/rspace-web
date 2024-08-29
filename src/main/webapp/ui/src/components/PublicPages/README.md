# Public Pages

This directory contains react components that render whole webpages that are
public, which is to say that the user may not be authenticated. This is
typically so that someone who is a user can send a link to a colleague, or even
published more widely if their deployment is not behind a firewall.

This comes with some complexity due to the way that the Inventory frontend is
architected. There is a global state object that is defined in the various
files in ../../stores/stores which are all highly coupled together. As such,
some of simpler capabilities such as storing the list of alert toast that are
currently being shown can't be used without also being able to check the
contents of the current user's bench. Worse still, most of the model classes
(../../stores/models/) and many of the react components depend on this global
state either directly or indirectly.

*None of this code can be used on the public pages.*

## But what if I accidentally do?
