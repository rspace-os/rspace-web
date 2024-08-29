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

Things you can try:

  1. Find the recent change that broke the page, and that it is small and thus
     the new import obvious. `git bisect` is your friend.

  2. Chase each of the imports, starting in the component that is used as the
     entry point in the Webpack config. This will be tedious, and there might
     be an automated way to generate the import tree.
