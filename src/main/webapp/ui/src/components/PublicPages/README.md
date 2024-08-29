# Public Pages

This directory contains react components that render whole webpages that are
public, which is to say that the user may not be authenticated. This is
typically so that someone who is a user can send a link to a colleague, or even
published more widely if their deployment is not behind a firewall.

This comes with some complexity due to the way that the Inventory frontend is
architected. There is a global state object that is defined in the various
files in ../../stores/stores which are all highly coupled together. As such,
some of simpler capabilities such as displaying an alert toasts can't be used
without also being able to check the contents of the current user's bench.
Worse still, most of the model classes (../../stores/models/) and many of the
react components depend on this global state either directly or indirectly.

**None of this code can be used on the public pages.**

## But what if I accidentally do?

If anyone accidentally adds a dependency on the stores to a class or component
that these public pages have an indirect dependency on then the public page
will break. Now, this will hopefully be caught by the cypress test for that
public page (so do make sure any new public pages have a cypress test!) but
the error message that you get wont necessarily help you in identifying what
exactly the cause of the issue is. For example, the following error message was
to be found in the JavaScript console after
[IdentifierModel](../../stores/models/IdentifierModel.js) imported
[InvApiService](../../common/InvApiService.js) as the latter has a dependency
on [getRootStore](../../stores/stores/RootStore.js):

```
Uncaught ReferenceError: Cannot access '__WEBPACK_DEFAULT_EXPORT__' before initialization
    at Module.default (ApiServiceBase.js:3:42)
    at eval (ElnApiService.js:7:71)
    at ./src/common/ElnApiService.js (inventoryRecordIdentifierPublicPage.js:4213:1)
    at __webpack_require__ (runtime.js:31:42)
    at eval (AuthStore.js:9:79)
    at ./src/stores/stores/AuthStore.js (inventoryRecordIdentifierPublicPage.js:4906:1)
    at __webpack_require__ (runtime.js:31:42)
    at eval (RootStore.js:6:68)
    at ./src/stores/stores/RootStore.js (inventoryRecordIdentifierPublicPage.js:4983:1)
    at __webpack_require__ (runtime.js:31:42)
```

## So then how do I fix the issue?

[Dependency-cruiser][depcruiser] to the rescue!

Run `npm run depcruise` from `src/main/webapp/ui` and amongst the output,
you're looking for a fragment that looks like this:

```
warn Public pages must not use stores: src/components/PublicPages/IdentifierPublicPage.js → src/stores/stores/ImportStore.js
      src/components/PublicPages/IdentifierPublicPage.js →
      src/stores/models/IdentifierModel.js →
      src/common/InvApiService.js →
      src/stores/stores/RootStore.js →
      src/stores/stores/ImportStore.js
```

What this says is that `IdentifierPublicPage.js` has an indirect dependency on
`ImportStore.js` via this chain of imports. The fact that it's ImportStore is
not relevant; any of the stores that are reachable are problematic as they are
all tightly coupled. What you need to do is to find a way to break this chain.

In this case, the easiest place to do this to break the dependency that
IdentifierModel.js has on InvApiService.js by having it only import the Flow
types and have the values passed at runtime by dependency injection rather than
reaching for the global variable. Try to break the chain as early as possible
as the further down you go the more other places in the codebase will need to
be modified: IdentifierModel is only used by the parent class of all of the
Inventory records classes and the components that render the IGSN forms so its
easier to make the necessary change than all the places that use the Inventory
API service.

If dependency-cruiser by itself doesn't help, as there may end up being lots of
false positives if we don't end up keeping on top of what dependency-cruiser is
reporting, then try finding the git commit that introduced the issue (here `git
bisect` is your friend). If the commit is small then it may well be obvious
what the new import statement is.

If the change is large and nothing else is helping, then try removing chunks of
the public page in question until it starts working again. If the issue is a
react component, or something imported by a react component, then removing it
from the page (by removing all the places it is rendered and its import) will
allow the page to render. Then its just a case of repeating the process down
the component tree.




[depcruiser]: https://github.com/sverweij/dependency-cruiser
