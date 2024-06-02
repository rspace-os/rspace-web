(function (f) { if (typeof exports === "object" && typeof module !== "undefined") { module.exports = f() } else if (typeof define === "function" && define.amd) { define([], f) } else { var g; if (typeof window !== "undefined") { g = window } else if (typeof global !== "undefined") { g = global } else if (typeof self !== "undefined") { g = self } else { g = this } g.Egnyte = f() } })(function () {
  var define, module, exports; return (function e(t, n, r) { function s(o, u) { if (!n[o]) { if (!t[o]) { var a = typeof require == "function" && require; if (!u && a) return a(o, !0); if (i) return i(o, !0); var f = new Error("Cannot find module '" + o + "'"); throw f.code = "MODULE_NOT_FOUND", f } var l = n[o] = { exports: {} }; t[o][0].call(l.exports, function (e) { var n = t[o][1][e]; return s(n ? n : e) }, l, l.exports, e, t, n, r) } return n[o].exports } var i = typeof require == "function" && require; for (var o = 0; o < r.length; o++)s(r[o]); return s })({
    1: [function (require, module, exports) {
      /*
       * PinkySwear.js 2.2.2 - Minimalistic implementation of the Promises/A+ spec
       * 
       * Public Domain. Use, modify and distribute it any way you like. No attribution required.
       *
       * NO WARRANTY EXPRESSED OR IMPLIED. USE AT YOUR OWN RISK.
       *
       * PinkySwear is a very small implementation of the Promises/A+ specification. After compilation with the
       * Google Closure Compiler and gzipping it weighs less than 500 bytes. It is based on the implementation for 
       * Minified.js and should be perfect for embedding. 
       *
       *
       * PinkySwear has just three functions.
       *
       * To create a new promise in pending state, call pinkySwear():
       *         var promise = pinkySwear();
       *
       * The returned object has a Promises/A+ compatible then() implementation:
       *          promise.then(function(value) { alert("Success!"); }, function(value) { alert("Failure!"); });
       *
       *
       * The promise returned by pinkySwear() is a function. To fulfill the promise, call the function with true as first argument and
       * an optional array of values to pass to the then() handler. By putting more than one value in the array, you can pass more than one
       * value to the then() handlers. Here an example to fulfill a promsise, this time with only one argument: 
       *         promise(true, [42]);
       *
       * When the promise has been rejected, call it with false. Again, there may be more than one argument for the then() handler:
       *         promise(true, [6, 6, 6]);
       *         
       * You can obtain the promise's current state by calling the function without arguments. It will be true if fulfilled,
       * false if rejected, and otherwise undefined.
       * 		   var state = promise(); 
       * 
       * https://github.com/timjansen/PinkySwear.js
       */
      (function (target) {
        var undef;

        function isFunction(f) {
          return typeof f == 'function';
        }
        function isObject(f) {
          return typeof f == 'object';
        }
        function defer(callback) {
          if (typeof setImmediate != 'undefined')
            setImmediate(callback);
          else if (typeof process != 'undefined' && process['nextTick'])
            process['nextTick'](callback);
          else
            setTimeout(callback, 0);
        }

        target[0][target[1]] = function pinkySwear(extend) {
          var state;           // undefined/null = pending, true = fulfilled, false = rejected
          var values = [];     // an array of values as arguments for the then() handlers
          var deferred = [];   // functions to call when set() is invoked

          var set = function (newState, newValues) {
            if (state == null && newState != null) {
              state = newState;
              values = newValues;
              if (deferred.length)
                defer(function () {
                  for (var i = 0; i < deferred.length; i++)
                    deferred[i]();
                });
            }
            return state;
          };

          set['then'] = function (onFulfilled, onRejected) {
            var promise2 = pinkySwear(extend);
            var callCallbacks = function () {
              try {
                var f = (state ? onFulfilled : onRejected);
                if (isFunction(f)) {
                  function resolve(x) {
                    var then, cbCalled = 0;
                    try {
                      if (x && (isObject(x) || isFunction(x)) && isFunction(then = x['then'])) {
                        if (x === promise2)
                          throw new TypeError();
                        then['call'](x,
                          function () { if (!cbCalled++) resolve.apply(undef, arguments); },
                          function (value) { if (!cbCalled++) promise2(false, [value]); });
                      }
                      else
                        promise2(true, arguments);
                    }
                    catch (e) {
                      if (!cbCalled++)
                        promise2(false, [e]);
                    }
                  }
                  resolve(f.apply(undef, values || []));
                }
                else
                  promise2(state, values);
              }
              catch (e) {
                promise2(false, [e]);
              }
            };
            if (state != null)
              defer(callCallbacks);
            else
              deferred.push(callCallbacks);
            return promise2;
          };
          if (extend) {
            set = extend(set);
          }
          return set;
        };
      })(typeof module == 'undefined' ? [window, 'pinkySwear'] : [module, 'exports']);


    }, {}], 2: [function (require, module, exports) {
      var ua = typeof window !== 'undefined' ? window.navigator.userAgent : ''
        , isOSX = /OS X/.test(ua)
        , isOpera = /Opera/.test(ua)
        , maybeFirefox = !/like Gecko/.test(ua) && !isOpera

      var i, output = module.exports = {
        0: isOSX ? '<menu>' : '<UNK>'
        , 1: '<mouse 1>'
        , 2: '<mouse 2>'
        , 3: '<break>'
        , 4: '<mouse 3>'
        , 5: '<mouse 4>'
        , 6: '<mouse 5>'
        , 8: '<backspace>'
        , 9: '<tab>'
        , 12: '<clear>'
        , 13: '<enter>'
        , 16: '<shift>'
        , 17: '<control>'
        , 18: '<alt>'
        , 19: '<pause>'
        , 20: '<caps-lock>'
        , 21: '<ime-hangul>'
        , 23: '<ime-junja>'
        , 24: '<ime-final>'
        , 25: '<ime-kanji>'
        , 27: '<escape>'
        , 28: '<ime-convert>'
        , 29: '<ime-nonconvert>'
        , 30: '<ime-accept>'
        , 31: '<ime-mode-change>'
        , 27: '<escape>'
        , 32: '<space>'
        , 33: '<page-up>'
        , 34: '<page-down>'
        , 35: '<end>'
        , 36: '<home>'
        , 37: '<left>'
        , 38: '<up>'
        , 39: '<right>'
        , 40: '<down>'
        , 41: '<select>'
        , 42: '<print>'
        , 43: '<execute>'
        , 44: '<snapshot>'
        , 45: '<insert>'
        , 46: '<delete>'
        , 47: '<help>'
        , 91: '<meta>'  // meta-left -- no one handles left and right properly, so we coerce into one.
        , 92: '<meta>'  // meta-right
        , 93: isOSX ? '<meta>' : '<menu>'      // chrome,opera,safari all report this for meta-right (osx mbp).
        , 95: '<sleep>'
        , 106: '<num-*>'
        , 107: '<num-+>'
        , 108: '<num-enter>'
        , 109: '<num-->'
        , 110: '<num-.>'
        , 111: '<num-/>'
        , 144: '<num-lock>'
        , 145: '<scroll-lock>'
        , 160: '<shift-left>'
        , 161: '<shift-right>'
        , 162: '<control-left>'
        , 163: '<control-right>'
        , 164: '<alt-left>'
        , 165: '<alt-right>'
        , 166: '<browser-back>'
        , 167: '<browser-forward>'
        , 168: '<browser-refresh>'
        , 169: '<browser-stop>'
        , 170: '<browser-search>'
        , 171: '<browser-favorites>'
        , 172: '<browser-home>'

        // ff/osx reports '<volume-mute>' for '-'
        , 173: isOSX && maybeFirefox ? '-' : '<volume-mute>'
        , 174: '<volume-down>'
        , 175: '<volume-up>'
        , 176: '<next-track>'
        , 177: '<prev-track>'
        , 178: '<stop>'
        , 179: '<play-pause>'
        , 180: '<launch-mail>'
        , 181: '<launch-media-select>'
        , 182: '<launch-app 1>'
        , 183: '<launch-app 2>'
        , 186: ';'
        , 187: '='
        , 188: ','
        , 189: '-'
        , 190: '.'
        , 191: '/'
        , 192: '`'
        , 219: '['
        , 220: '\\'
        , 221: ']'
        , 222: "'"
        , 223: '<meta>'
        , 224: '<meta>'       // firefox reports meta here.
        , 226: '<alt-gr>'
        , 229: '<ime-process>'
        , 231: isOpera ? '`' : '<unicode>'
        , 246: '<attention>'
        , 247: '<crsel>'
        , 248: '<exsel>'
        , 249: '<erase-eof>'
        , 250: '<play>'
        , 251: '<zoom>'
        , 252: '<no-name>'
        , 253: '<pa-1>'
        , 254: '<clear>'
      }

      for (i = 58; i < 65; ++i) {
        output[i] = String.fromCharCode(i)
      }

      // 0-9
      for (i = 48; i < 58; ++i) {
        output[i] = (i - 48) + ''
      }

      // A-Z
      for (i = 65; i < 91; ++i) {
        output[i] = String.fromCharCode(i)
      }

      // num0-9
      for (i = 96; i < 106; ++i) {
        output[i] = '<num-' + (i - 96) + '>'
      }

      // F1-F24
      for (i = 112; i < 136; ++i) {
        output[i] = 'F' + (i - 111)
      }

    }, {}], 3: [function (require, module, exports) {
      "use strict";
      var window = require(4)
      var once = require(5)
      var parseHeaders = require(9)



      module.exports = createXHR
      createXHR.XMLHttpRequest = window.XMLHttpRequest || noop
      createXHR.XDomainRequest = "withCredentials" in (new createXHR.XMLHttpRequest()) ? createXHR.XMLHttpRequest : window.XDomainRequest


      function isEmpty(obj) {
        for (var i in obj) {
          if (obj.hasOwnProperty(i)) return false
        }
        return true
      }

      function createXHR(options, callback) {
        function readystatechange() {
          if (xhr.readyState === 4) {
            loadFunc()
          }
        }

        function getBody() {
          // Chrome with requestType=blob throws errors arround when even testing access to responseText
          var body = undefined

          if (xhr.response) {
            body = xhr.response
          } else if (xhr.responseType === "text" || !xhr.responseType) {
            body = xhr.responseText || xhr.responseXML
          }

          if (isJson) {
            try {
              body = JSON.parse(body)
            } catch (e) { }
          }

          return body
        }

        var failureResponse = {
          body: undefined,
          headers: {},
          statusCode: 0,
          method: method,
          url: uri,
          rawRequest: xhr
        }

        function errorFunc(evt) {
          clearTimeout(timeoutTimer)
          if (!(evt instanceof Error)) {
            evt = new Error("" + (evt || "Unknown XMLHttpRequest Error"))
          }
          evt.statusCode = 0
          callback(evt, failureResponse)
        }

        // will load the data & process the response in a special response object
        function loadFunc() {
          if (aborted) return
          var status
          clearTimeout(timeoutTimer)
          if (options.useXDR && xhr.status === undefined) {
            //IE8 CORS GET successful response doesn't have a status field, but body is fine
            status = 200
          } else {
            status = (xhr.status === 1223 ? 204 : xhr.status)
          }
          var response = failureResponse
          var err = null

          if (status !== 0) {
            response = {
              body: getBody(),
              statusCode: status,
              method: method,
              headers: {},
              url: uri,
              rawRequest: xhr
            }
            if (xhr.getAllResponseHeaders) { //remember xhr can in fact be XDR for CORS in IE
              response.headers = parseHeaders(xhr.getAllResponseHeaders())
            }
          } else {
            err = new Error("Internal XMLHttpRequest Error")
          }
          callback(err, response, response.body)

        }

        if (typeof options === "string") {
          options = { uri: options }
        }

        options = options || {}
        if (typeof callback === "undefined") {
          throw new Error("callback argument missing")
        }
        callback = once(callback)

        var xhr = options.xhr || null

        if (!xhr) {
          if (options.cors || options.useXDR) {
            xhr = new createXHR.XDomainRequest()
          } else {
            xhr = new createXHR.XMLHttpRequest()
          }
        }

        var key
        var aborted
        var uri = xhr.url = options.uri || options.url
        var method = xhr.method = options.method || "GET"
        var body = options.body || options.data
        var headers = xhr.headers = options.headers || {}
        var sync = !!options.sync
        var isJson = false
        var timeoutTimer

        if ("json" in options) {
          isJson = true
          headers["accept"] || headers["Accept"] || (headers["Accept"] = "application/json") //Don't override existing accept header declared by user
          if (method !== "GET" && method !== "HEAD") {
            headers["content-type"] || headers["Content-Type"] || (headers["Content-Type"] = "application/json") //Don't override existing accept header declared by user
            body = JSON.stringify(options.json)
          }
        }

        xhr.onreadystatechange = readystatechange
        xhr.onload = loadFunc
        xhr.onerror = errorFunc
        // IE9 must have onprogress be set to a unique function.
        xhr.onprogress = function () {
          // IE must die
        }
        xhr.ontimeout = errorFunc
        xhr.open(method, uri, !sync, options.username, options.password)
        //has to be after open
        if (!sync) {
          xhr.withCredentials = !!options.withCredentials
        }
        // Cannot set timeout with sync request
        // not setting timeout on the xhr object, because of old webkits etc. not handling that correctly
        // both npm's request and jquery 1.x use this kind of timeout, so this is being consistent
        if (!sync && options.timeout > 0) {
          timeoutTimer = setTimeout(function () {
            aborted = true//IE9 may still call readystatechange
            xhr.abort("timeout")
            var e = new Error("XMLHttpRequest timeout")
            e.code = "ETIMEDOUT"
            errorFunc(e)
          }, options.timeout)
        }

        if (xhr.setRequestHeader) {
          for (key in headers) {
            if (headers.hasOwnProperty(key)) {
              xhr.setRequestHeader(key, headers[key])
            }
          }
        } else if (options.headers && !isEmpty(options.headers)) {
          throw new Error("Headers cannot be set on an XDomainRequest object")
        }

        if ("responseType" in options) {
          xhr.responseType = options.responseType
        }

        if ("beforeSend" in options &&
          typeof options.beforeSend === "function"
        ) {
          options.beforeSend(xhr)
        }

        xhr.send(body)

        return xhr


      }

      function noop() { }

    }, { "4": 4, "5": 5, "9": 9 }], 4: [function (require, module, exports) {
      if (typeof window !== "undefined") {
        module.exports = window;
      } else if (typeof global !== "undefined") {
        module.exports = global;
      } else if (typeof self !== "undefined") {
        module.exports = self;
      } else {
        module.exports = {};
      }

    }, {}], 5: [function (require, module, exports) {
      module.exports = once

      once.proto = once(function () {
        Object.defineProperty(Function.prototype, 'once', {
          value: function () {
            return once(this)
          },
          configurable: true
        })
      })

      function once(fn) {
        var called = false
        return function () {
          if (called) return
          called = true
          return fn.apply(this, arguments)
        }
      }

    }, {}], 6: [function (require, module, exports) {
      var isFunction = require(7)

      module.exports = forEach

      var toString = Object.prototype.toString
      var hasOwnProperty = Object.prototype.hasOwnProperty

      function forEach(list, iterator, context) {
        if (!isFunction(iterator)) {
          throw new TypeError('iterator must be a function')
        }

        if (arguments.length < 3) {
          context = this
        }

        if (toString.call(list) === '[object Array]')
          forEachArray(list, iterator, context)
        else if (typeof list === 'string')
          forEachString(list, iterator, context)
        else
          forEachObject(list, iterator, context)
      }

      function forEachArray(array, iterator, context) {
        for (var i = 0, len = array.length; i < len; i++) {
          if (hasOwnProperty.call(array, i)) {
            iterator.call(context, array[i], i, array)
          }
        }
      }

      function forEachString(string, iterator, context) {
        for (var i = 0, len = string.length; i < len; i++) {
          // no such thing as a sparse string.
          iterator.call(context, string.charAt(i), i, string)
        }
      }

      function forEachObject(object, iterator, context) {
        for (var k in object) {
          if (hasOwnProperty.call(object, k)) {
            iterator.call(context, object[k], k, object)
          }
        }
      }

    }, { "7": 7 }], 7: [function (require, module, exports) {
      module.exports = isFunction

      var toString = Object.prototype.toString

      function isFunction(fn) {
        var string = toString.call(fn)
        return string === '[object Function]' ||
          (typeof fn === 'function' && string !== '[object RegExp]') ||
          (typeof window !== 'undefined' &&
            // IE8 and below
            (fn === window.setTimeout ||
              fn === window.alert ||
              fn === window.confirm ||
              fn === window.prompt))
      };

    }, {}], 8: [function (require, module, exports) {

      exports = module.exports = trim;

      function trim(str) {
        return str.replace(/^\s*|\s*$/g, '');
      }

      exports.left = function (str) {
        return str.replace(/^\s*/, '');
      };

      exports.right = function (str) {
        return str.replace(/\s*$/, '');
      };

    }, {}], 9: [function (require, module, exports) {
      var trim = require(8)
        , forEach = require(6)
        , isArray = function (arg) {
          return Object.prototype.toString.call(arg) === '[object Array]';
        }

      module.exports = function (headers) {
        if (!headers)
          return {}

        var result = {}

        forEach(
          trim(headers).split('\n')
          , function (row) {
            var index = row.indexOf(':')
              , key = trim(row.slice(0, index)).toLowerCase()
              , value = trim(row.slice(index + 1))

            if (typeof (result[key]) === 'undefined') {
              result[key] = value
            } else if (isArray(result[key])) {
              result[key].push(value)
            } else {
              result[key] = [result[key], value]
            }
          }
        )

        return result
      }
    }, { "6": 6, "8": 8 }], 10: [function (require, module, exports) {
      module.exports = {
        handleQuota: true,
        QPS: 2,
        forwarderAddress: "app/integ/forwarder/1.4/apiForwarder.html",
        channelMarker: "'E",
        httpRequest: null,
        oldIEForwarder: false

      }
    }, {}], 11: [function (require, module, exports) {
      var slim = require(51);
      var filepicker = require(36)
      var prompt = require(44)
      var authPrompt = require(14)

      slim.plugin("filePicker", function (root, resources) {
        root.filePicker = filepicker(resources.API);
      });
      slim.plugin("authPrompt", authPrompt);
      slim.plugin("prompt", function (root, resources) {
        root.prompt = prompt;
      });

      module.exports = slim;
    }, { "14": 14, "36": 36, "44": 44, "51": 51 }], 12: [function (require, module, exports) {
      var RequestEngine = require(23);
      var AuthEngine = require(13);
      var StorageFacade = require(26);
      var Notes = require(21);
      var LinkFacade = require(19);
      var PermFacade = require(22);
      var UserPerms = require(28);
      var User = require(27);
      var Events = require(18);
      var Search = require(25);


      module.exports = function (options) {
        var auth = new AuthEngine(options);
        var requestEngine = new RequestEngine(auth, options);

        var storage = new StorageFacade(requestEngine);
        var notes = new Notes(requestEngine);
        var link = new LinkFacade(requestEngine);
        var perms = new PermFacade(requestEngine);
        var userPerms = new UserPerms(requestEngine);
        var user = new User(requestEngine);
        var search = new Search(requestEngine);
        var events = new Events(requestEngine);

        var api = {
          auth: auth,
          storage: storage,
          notes: notes,
          link: link,
          events: events,
          search: search,
          perms: perms,
          userPerms: userPerms,
          user: user
        };

        //onlt in IE8 and IE9
        if (!("withCredentials" in (new window.XMLHttpRequest()))) {
          if (options.acceptForwarding) {
            //will handle incoming forwards
            var responder = require(29);
            responder(options, api);
          } else {
            //IE 8 and 9 forwarding
            if (options.oldIEForwarder) {
              var forwarder = require(30);
              forwarder(options, api);
            }
          }
        }

        api.manual = requestEngine;

        return api;
      };

    }, { "13": 13, "18": 18, "19": 19, "21": 21, "22": 22, "23": 23, "25": 25, "26": 26, "27": 27, "28": 28, "29": 29, "30": 30 }], 13: [function (require, module, exports) {
      var oauthRegex = /access_token=([^&]+)/;
      var oauthDeniedRegex = /error=access_denied/;

      var promises = require(43);
      var helpers = require(47);
      var dom = require(45);
      var messages = require(48);
      var errorify = require(17);

      var ENDPOINTS_userinfo = require(31).userinfo;
      var ENDPOINTS_tokenauth = require(31).tokenauth;


      function Auth(options) {
        this.type = "Bearer";
        this.options = options;
        if (this.options.token) {
          this.token = this.options.token;
        }
        this.userInfo = null;
        this.getUserInfo = helpers.bindThis(this, this.getUserInfo);

      }


      var authPrototypeMethods = {};

      authPrototypeMethods._buildTokenQuery = function (redirect) {
        var url = this.options.egnyteDomainURL + ENDPOINTS_tokenauth + "?client_id=" + this.options.key + "&mobile=" + ~~(this.options.mobile) + "&redirect_uri=" + encodeURIComponent(redirect);
        if (this.options.scope) {
          url += "&scope=" + this.options.scope;
        }
        return url;
      }

      authPrototypeMethods._reloadForToken = function () {
        window.location.href = this._buildTokenQuery(window.location.href);
      }

      authPrototypeMethods._checkTokenResponse = function (success, denied, notoken, overrideWindow) {
        var win = overrideWindow || window;
        if (!this.token) {
          this.userInfo = null;
          var access = oauthRegex.exec(win.location.hash);
          if (access) {
            if (access.length > 1) {
              this.token = access[1];
              //overrideWindow || (window.location.hash = "");
              success && success();
            } else {
              //what now?
            }
          } else {
            if (oauthDeniedRegex.test(win.location.href)) {
              denied && denied();
            } else {
              notoken && notoken();
            }
          }
        } else {
          success && success();
        }
      }

      authPrototypeMethods.requestTokenReload = function (callback, denied) {
        this._checkTokenResponse(callback, denied, helpers.bindThis(this, this._reloadForToken));
      }

      authPrototypeMethods.requestTokenIframe = function (targetNode, callback, denied, emptyPageURL) {
        if (!this.token) {
          var self = this;
          var locationObject = window.location;

          emptyPageURL = (emptyPageURL) ? locationObject.protocol + "//" + locationObject.host + emptyPageURL : locationObject.href;
          var url = self._buildTokenQuery(emptyPageURL);
          var iframe = dom.createFrame(url, !!"scrollbars please");
          iframe.onload = function () {
            try {
              var location = iframe.contentWindow.location;
              var override = {
                location: {
                  hash: "" + location.hash,
                  href: "" + location.href
                }
              };

              self._checkTokenResponse(function () {
                iframe.src = "";
                targetNode.removeChild(iframe);
                callback();
              }, function () {
                iframe.src = "";
                targetNode.removeChild(iframe);
                denied();
              }, null, override);
            } catch (e) { }
          }
          targetNode.appendChild(iframe);
        } else {
          callback();
        }
      }


      authPrototypeMethods._postTokenUp = function () {
        var self = this;
        if (!this.token && window.name === this.options.channelMarker) {
          var channel = {
            marker: this.options.channelMarker,
            sourceOrigin: this.options.egnyteDomainURL
          };

          this._checkTokenResponse(function () {
            messages.sendMessage(window.opener, channel, "token", self.token);
          }, function () {
            messages.sendMessage(window.opener, channel, "denied", "");
          });

        }
      }

      authPrototypeMethods.requestTokenPopup = function (callback, denied, recvrURL) {
        var self = this;
        if (!this.token) {
          var url = this._buildTokenQuery(recvrURL);
          var win = window.open(url);
          win.name = this.options.channelMarker;
          var handler = messages.createMessageHandler(null, this.options.channelMarker, function (message) {
            listener.destroy();
            win.close();
            if (message.action === "token") {
              self.token = message.data;
              callback && callback();
            }
            if (message.action === "denied") {
              denied && denied();
            }
          });
          var listener = dom.addListener(window, "message", handler);
        } else {
          callback();
        }

      }

      authPrototypeMethods.requestTokenByPassword = function (username, password) {
        var self = this;

        return this.requestEngine.promiseRequest({
          method: "POST",
          url: this.options.egnyteDomainURL + ENDPOINTS_tokenauth + "",
          headers: {
            "content-type": "application/x-www-form-urlencoded"
          },
          body: [
            "client_id=" + this.options.key,
            "grant_type=password",
            "username=" + username,
            "password=" + password
          ].join("&")
        }, null, !!"forceNoAuth").then(function (result) { //result.response result.body
          self.token = result.body.access_token
          return self.token;
        });
      }

      authPrototypeMethods.authorizeXHR = function (xhr) {
        //assuming token_type was bearer, no use for XHR otherwise, right?
        xhr.setRequestHeader("Authorization", "Bearer " + this.token);
      }

      authPrototypeMethods.getHeaders = function () {
        return {
          "Authorization": "Bearer " + this.token
        };
      }


      authPrototypeMethods.isAuthorized = function () {
        return !!this.token;
      }

      authPrototypeMethods.getToken = function () {
        return this.token;
      }

      authPrototypeMethods.setToken = function (externalToken) {
        this.token = externalToken;
      }


      authPrototypeMethods.dropToken = function (externalToken) {
        this.token = null;
      }


      //======================================================================
      //api facade


      authPrototypeMethods.addRequestEngine = function (requestEngine) {
        this.requestEngine = requestEngine;
      }

      authPrototypeMethods.getUserInfo = function () {
        var self = this;
        if (self.userInfo || !this.requestEngine) {
          return promises(true).then(function () {
            return self.userInfo;
          });
        } else {
          return this.requestEngine.promiseRequest({
            method: "GET",
            url: this.requestEngine.getEndpoint() + ENDPOINTS_userinfo,
          }).then(function (result) { //result.response result.body
            self.userInfo = result.body;
            return result.body;
          });
        }
      }

      Auth.prototype = authPrototypeMethods;

      module.exports = Auth;
    }, { "17": 17, "31": 31, "43": 43, "45": 45, "47": 47, "48": 48 }], 14: [function (require, module, exports) {
      var prompt = require(44)
      var helpers = require(47)

      function egmitifyDomain(domain) {
        if (domain.indexOf('.') === -1) {
          domain += '.egnyte.com';
        }
        return domain;
      }

      module.exports = function (root, resources) {
        root.API.auth.requestTokenIframeWithPrompt = function (targetNode, callback, denied, emptyPageURL) {
          prompt(targetNode, {
            texts: {
              question: "Your egnyte domain address"
            },
            result: function (choice) {
              root.setDomain(helpers.httpsURL(egmitifyDomain(choice)));
              root.API.auth.requestTokenIframe(targetNode, callback, denied, emptyPageURL);
            }
          });
        }
      };
    }, { "44": 44, "47": 47 }], 15: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var ENDPOINTS = require(31);


      function genericUpload(requestEngine, decorate, pathFromRoot, headers, file) {
        pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";

        var opts = {
          headers: headers,
          method: "POST",
          url: requestEngine.getEndpoint() + ENDPOINTS.fschunked + helpers.encodeURIPath(pathFromRoot),
          body: file,
        }

        return requestEngine.promiseRequest(decorate(opts));
      }

      function ChunkedUploader(storage, pathFromRoot, mimeType) {
        this.storage = storage;
        this.path = pathFromRoot;
        this.mime = mimeType;
        this.num = 1;
        this.successful = 1;
        this.chunksPromised = [];
      }

      var chunkedUploaderProto = {};

      chunkedUploaderProto.setId = function (id) {
        this.id = id;
      };

      chunkedUploaderProto.sendChunk = function (content, num, verify) {
        var self = this;
        var requestEngine = this.storage.requestEngine;
        var decorate = this.storage.getDecorator();
        if (num) {
          self.num = num;
        } else {
          num = (++self.num);
        }
        var headers = {
          "x-egnyte-upload-id": self.id,
          "x-egnyte-chunk-num": self.num,

        };
        var promised = genericUpload(requestEngine, decorate, self.path, headers, content)
          .then(function (result) {
            verify && verify(result.response.headers["x-egnyte-chunk-sha512-checksum"]);
            self.successful++;
            return result;
          });
        self.chunksPromised.push(promised);
        return promised;

      };


      chunkedUploaderProto.sendLastChunk = function (content, verify) {
        var self = this;
        var requestEngine = this.storage.requestEngine;
        var decorate = this.storage.getDecorator();

        var headers = {
          "x-egnyte-upload-id": self.id,
          "x-egnyte-last-chunk": true,
          "x-egnyte-chunk-num": self.num + 1
        };
        if (self.mime) {
          headers["content-type"] = self.mime;
        }

        return promises.allSettled(this.chunksPromised)
          .then(function () {
            if (self.num === self.successful) {
              return genericUpload(requestEngine, decorate, self.path, headers, content)
                .then(function (result) {
                  verify && verify(result.response.headers["x-egnyte-chunk-sha512-checksum"]);
                  return ({
                    id: result.response.headers["etag"],
                    path: self.path
                  });
                });
            } else {
              throw new Error("Tried to commit a file with missing chunks (some uploads failed)");
            }
          });

      };

      ChunkedUploader.prototype = chunkedUploaderProto;

      exports.startChunkedUpload = function (pathFromRoot, fileOrBlob, mimeType, verify) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        var chunkedUploader = new ChunkedUploader(this, pathFromRoot, mimeType);
        return promises(true).then(function () {
          var file = fileOrBlob;
          var headers = {};
          if (mimeType) {
            headers["content-type"] = mimeType;
          }
          return genericUpload(requestEngine, decorate, pathFromRoot, headers, fileOrBlob);
        }).then(function (result) { //result.response result.body
          verify && verify(result.response.headers["x-egnyte-chunk-sha512-checksum"]);
          chunkedUploader.setId(result.response.headers["x-egnyte-upload-id"])
          return chunkedUploader;
        });

      }
    }, { "31": 31, "43": 43, "47": 47 }], 16: [function (require, module, exports) {
      var helpers = require(47);

      var defaultDecorators = {

        "impersonate": function (opts, data) {
          if (!opts.headers) {
            opts.headers = {}
          }
          if (data.username) {
            opts.headers["X-Egnyte-Act-As"] = data.username;
          }
          if (data.email) {
            opts.headers["X-Egnyte-Act-As-Email"] = data.email;
          }
          return opts;
        },
        "customizeRequest": function (opts, transformation) {
          return transformation(opts);
        }

      }



      function getDecorator() {
        var self = this;
        return function (opts) {
          helpers.each(self._decorators, function (decor, name) {
            if (self._decorations[name] !== undefined) {
              opts = decor(opts, self._decorations[name]);
            }
          });
          return opts;
        }
      }

      module.exports = {
        install: function (self) {

          function exposeDecorators(that) {
            helpers.each(that._decorators, function (decor, name) {
              that[name] = function (data) {
                var Decorated = function () { };
                Decorated.prototype = this;
                var instance = new Decorated();
                instance.getDecorator = getDecorator;
                instance._decorations = helpers.extend({}, this._decorations)
                instance._decorations[name] = data || null;
                exposeDecorators(instance);
                return instance;
              }
            });
          }

          self._decorators = helpers.extend({}, defaultDecorators);
          exposeDecorators(self);

          self.addDecorator = function (name, action) {
            this._decorators[name] = action;
            exposeDecorators(this);
          };
          self.getDecorator = function () {
            return helpers.id;
          }



        }
      }

    }, { "47": 47 }], 17: [function (require, module, exports) {
      //making sense of all the different error message bodies
      var isMsg = {
        "msg": 1,
        "message": 1,
        "errorMessage": 1
      };

      var htmlMsgRegex = /^\s*<h1>([^<]*)<\/h1>\s*$/gi;

      function findMessage(obj) {
        var result;
        for (var i in obj) {
          if (isMsg[i]) {
            return obj[i];
          }
          if (typeof obj[i] === "object") {
            result = findMessage(obj[i]);
            if (result) {
              return result;
            }
          }
        }
      }
      //this should understand all the message formats from the server and translate to a nice message
      function psychicMessageParser(mess, statusCode) {
        var nice;
        if (typeof mess === "string") {
          try {
            nice = findMessage(JSON.parse(mess));
            if (!nice) {
              //fallback if nothing found - return raw JSON string anyway
              nice = mess;
            }
          } catch (e) {
            nice = mess ? mess.replace(htmlMsgRegex, "$1") : "Unknown error";
          }
          if (statusCode === 404 && mess.length > 300) {
            //server returned a dirty 404
            nice = "Not found";
          }
        } else {
          nice = findMessage(mess);
        }
        return nice;
      }

      module.exports = function (result) {
        var error, code;
        if (result.response) {
          code = ~~(result.response.statusCode);
          error = result.error;
          error.statusCode = code;
          error.message = "" + psychicMessageParser(result.body || result.error.message, code);
          error.response = result.response;
          error.body = result.body;
        } else {
          error = result.error;
          error.statusCode = 0;
        }
        return error;
      }
    }, {}], 18: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var every = require(46);
      var decorators = require(16);

      var ENDPOINTS_events = require(31).events;
      var ENDPOINTS_eventscursor = require(31).eventscursor;

      function Events(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);
        this.addDecorator("filter", addFilter);
        this.addDecorator("notMy", notMy);
      }

      function addFilter(opts, data) {
        opts.params || (opts.params = {});
        if (data.folder) {
          opts.params.folder = data.folder;
        }
        if (data.type) {
          if (data.type.join) {
            opts.params.type = data.type.join("|");
          } else {
            opts.params.type = data.type;
          }
        }
        return opts;
      }

      function notMy(opts, data) {
        opts.params || (opts.params = {});
        opts.params.suppress = data ? data : "app";
        return opts;
      }



      var defaultCount = 20;


      Events.prototype = {
        getCursor: function () {
          var requestEngine = this.requestEngine;
          return requestEngine.promiseRequest({
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS_eventscursor
          }).then(function (result) {
            return result.body.latest_event_id;
          });
        },
        //options.start
        //options.emit
        //options.count (optional)
        getUpdate: function (options) {
          var self = this;
          var requestEngine = this.requestEngine;
          var decorate = this.getDecorator();

          return promises(true).then(function () {
            if (!(options.start >= 0)) {
              throw new Error("'start' option is required");
            }
            var count = options.count || defaultCount;
            return requestEngine.promiseRequest(decorate({
              method: "GET",
              url: requestEngine.getEndpoint() + ENDPOINTS_events,
              params: {
                id: options.start,
                count: count
              }
            }));
          }).then(function (result) {
            if (result.body && options.emit) {
              helpers.each(result.body.events, function (e) {
                setTimeout(function () {
                  options.emit(e);
                }, 0)
              });
            }
            return result;
          });
        },
        //options.start
        //options.interval >2000
        //options.emit
        //options.current
        //options.count (optional)
        //returns {stop:function}
        listen: function (options) {
          var self = this;

          return promises(true)
            .then(function () {
              if (!isNaN(options.start)) {
                return options.start;
              } else {
                return self.getCursor();
              }
            }).then(function (initial) {
              var start = initial;
              if (options.current) {
                options.current(start);
              }
              //returns controllong object
              return every(Math.max(options.interval || 30000, 2000), function (controller) {
                var count = options.count || defaultCount;
                return self.getUpdate({
                  count: count,
                  emit: options.emit,
                  start: start
                }).then(function (result) {
                  if (result.body) {
                    start = result.body.latest_id;
                    if (options.current) {
                      options.current(start);
                    }
                    if (result.body.events.length >= count) {
                      controller.repeat();
                    }
                  }
                  if (options.heartbeat) {
                    options.heartbeat(result.body);
                  }
                });
              }, options.error)

            });
        }

      };

      module.exports = Events;
    }, { "16": 16, "31": 31, "43": 43, "46": 46, "47": 47 }], 19: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var decorators = require(16);

      var ENDPOINTS_links = require(31).links;

      function Links(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);
      }

      var linksProto = {};

      linksProto.createLink = function (setup) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        var defaults = {
          path: null,
          type: "file",
          accessibility: "domain"
        };
        return promises(true)
          .then(function () {
            setup = helpers.extend(defaults, setup);
            setup.path = helpers.encodeNameSafe(setup.path);

            if (!setup.path) {
              throw new Error("Path attribute missing or incorrect");
            }

            return requestEngine.promiseRequest(decorate({
              method: "POST",
              url: requestEngine.getEndpoint() + ENDPOINTS_links,
              json: setup
            }));
          }).then(function (result) { //result.response result.body
            return result.body;
          });
      }


      linksProto.removeLink = function (id) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return requestEngine.promiseRequest(decorate({
          method: "DELETE",
          url: requestEngine.getEndpoint() + ENDPOINTS_links + "/" + id
        })).then(function (result) { //result.response result.body
          return result.response.statusCode;
        });
      }

      linksProto.listLink = function (id) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return requestEngine.promiseRequest(decorate({
          method: "GET",
          url: requestEngine.getEndpoint() + ENDPOINTS_links + "/" + id
        })).then(function (result) { //result.response result.body
          return result.body;
        });
      }


      linksProto.listLinks = function (filters) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true)
          .then(function () {
            filters.path = filters.path && helpers.encodeNameSafe(filters.path);

            return requestEngine.promiseRequest(decorate({
              method: "get",
              url: requestEngine.getEndpoint() + ENDPOINTS_links,
              params: filters
            }));
          }).then(function (result) { //result.response result.body
            return result.body;
          });
      }

      linksProto.findOne = function (filters) {
        var self = this;
        return self.listLinks(filters).then(function (list) {
          if (list.ids && list.ids.length > 0) {
            return self.listLink(list.ids[0]);
          } else {
            return null;
          }
        });
      }

      Links.prototype = linksProto;

      module.exports = Links;

    }, { "16": 16, "31": 31, "43": 43, "47": 47 }], 20: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);

      var ENDPOINTS_fsmeta = require(31).fsmeta;

      exports.lock = function (pathFromRoot, lockToken, timeout) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var body = {
            "action": "lock"
          }
          if (lockToken) {
            body.lock_token = lockToken;
          }
          if (timeout) {
            body.lock_timeout = timeout;
          }
          var opts = {
            method: "POST",
            url: requestEngine.getEndpoint() + ENDPOINTS_fsmeta + helpers.encodeURIPath(pathFromRoot),
            json: body
          };
          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          result.body.path = pathFromRoot;
          return result.body;
        });
      }


      exports.unlock = function (pathFromRoot, lockToken) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var body = {
            "action": "unlock",
            "lock_token": lockToken
          }
          var opts = {
            method: "POST",
            url: requestEngine.getEndpoint() + ENDPOINTS_fsmeta + helpers.encodeURIPath(pathFromRoot),
            json: body
          };
          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return {
            path: pathFromRoot
          };
        });
      }
    }, { "31": 31, "43": 43, "47": 47 }], 21: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var decorators = require(16);

      var ENDPOINTS_notes = require(31).notes;


      function Notes(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);
      }


      var notesProto = {};
      notesProto.path = function (pathFromRoot) {
        pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
        var self = this;
        return {
          addNote: function (body) {
            var requestEngine = self.requestEngine;
            var decorate = self.getDecorator();
            return promises(true).then(function () {

              var opts = {
                method: "POST",
                url: requestEngine.getEndpoint() + ENDPOINTS_notes,
                json: {
                  "path": pathFromRoot,
                  "body": body,
                }
              };
              return requestEngine.promiseRequest(decorate(opts));
            }).then(function (result) { //result.response result.body
              return {
                id: result.body.id
              };
            });

          },
          listNotes: function (params) {
            var requestEngine = self.requestEngine;
            var decorate = self.getDecorator();
            return promises(true).then(function () {
              pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
              var opts = {
                method: "GET",
                url: requestEngine.getEndpoint() + ENDPOINTS_notes
              };

              //xhr and request differ here
              opts.params = helpers.extend({
                "file": helpers.encodeURIPath(pathFromRoot)
              }, params);

              return requestEngine.promiseRequest(decorate(opts)).then(function (result) {
                return result.body;
              });
            });

          }
        };
      };

      notesProto.getNote = function (id) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS_notes + "/" + helpers.encodeURIPath(id)
          };
          return requestEngine.promiseRequest(decorate(opts)).then(function (result) {
            return result.body;
          });
        });

      };
      notesProto.removeNote = function (id) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          var opts = {
            method: "DELETE",
            url: requestEngine.getEndpoint() + ENDPOINTS_notes + "/" + helpers.encodeURIPath(id)
          };
          return requestEngine.promiseRequest(decorate(opts));
        });

      };


      Notes.prototype = notesProto;

      module.exports = Notes;
    }, { "16": 16, "31": 31, "43": 43, "47": 47 }], 22: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var decorators = require(16);
      var resourceIdentifier = require(24);

      var ENDPOINTS_perms = require(31).perms;

      function Perms(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);

        this.addDecorator("users", enlist("users"))
        this.addDecorator("groups", enlist("groups"))

      }

      function enlist(what) {
        return function (opts, data) {
          switch (opts.method) {
            case 'GET':
              opts.params || (opts.params = {});
              opts.params[what] = data.join("|");
              break;
            case 'POST':
              opts.json[what] = data;
              break;
          }
          return opts;
        }
      }


      var permsProto = {};

      permsProto.disallow = function (pathFromRoot) {
        return permsProto.allow.call(this, pathFromRoot, "None");
      }
      permsProto.allowView = function (pathFromRoot) {
        return permsProto.allow.call(this, pathFromRoot, "Viewer");
      }
      permsProto.allowEdit = function (pathFromRoot) {
        return permsProto.allow.call(this, pathFromRoot, "Editor");
      }
      permsProto.allowFullAccess = function (pathFromRoot) {
        return permsProto.allow.call(this, pathFromRoot, "Full");
      }
      permsProto.allowOwnership = function (pathFromRoot) {
        return permsProto.allow.call(this, pathFromRoot, "Owner");
      }

      permsProto.allow = function (pathFromRoot, permission) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();

        return promises(true)
          .then(function () {
            pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";
            var opts = {
              method: "POST",
              url: requestEngine.getEndpoint() + ENDPOINTS_perms + helpers.encodeURIPath(pathFromRoot),
              json: {
                "permission": permission
              }
            };
            return requestEngine.promiseRequest(decorate(opts));
          }).then(function (result) { //result.response result.body
            return result.response;
          });
      };

      permsProto.getPerms = function (pathFromRoot) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();

        return promises(true)
          .then(function () {
            pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";
            var opts = {
              method: "GET",
              url: requestEngine.getEndpoint() + ENDPOINTS_perms + helpers.encodeURIPath(pathFromRoot)
            };
            return requestEngine.promiseRequest(decorate(opts));
          }).then(function (result) { //result.response result.body
            return result.body;
          });
      };

      Perms.prototype = resourceIdentifier(permsProto, {
        pathPrefix: "/folder"
      });

      //to prevent confusion in users
      delete Perms.prototype.fileId;

      module.exports = Perms;

    }, { "16": 16, "24": 24, "31": 31, "43": 43, "47": 47 }], 23: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var dom = require(45);
      var messages = require(48);
      var errorify = require(17);
      var request = require(3);



      function Engine(auth, options) {
        this.auth = auth;
        this.options = options;

        this.requestHandler = (options.httpRequest) ? options.httpRequest : request;

        this.quota = {
          startOfTheSecond: 0,
          calls: 0,
          retrying: 0
        }
        this.queue = [];

        this.queueHandler = helpers.bindThis(this, _rollQueue);

        auth.addRequestEngine(this);

      }

      var enginePrototypeMethods = {
        Promise: promises
      };



      //======================================================================
      //request handling
      function params(obj) {
        var str = [];
        //cachebuster for IE
        //    if (typeof window !== "undefined" && window.XDomainRequest) {
        //        str.push("random=" + (~~(Math.random() * 9999)));
        //    }
        for (var p in obj) {
          if (obj.hasOwnProperty(p)) {
            str.push(encodeURIComponent(p) + "=" + encodeURIComponent(obj[p]));
          }
        }
        if (str.length) {
          return "?" + str.join("&");
        } else {
          return "";
        }
      }

      enginePrototypeMethods.getEndpoint = function () {
        return this.options.egnyteDomainURL + "/pubapi";
      }

      enginePrototypeMethods.promise = function (value) {
        return promises(value);
      }

      enginePrototypeMethods.sendRequest = function (opts, callback, forceNoAuth) {
        var self = this;
        opts = helpers.extend({}, self.options.requestDefaults, opts); //merging in the defaults
        var originalOpts = helpers.extend({}, opts); //just copying the object



        if (this.auth.isAuthorized() || forceNoAuth) {
          opts.url += params(opts.params);
          opts.headers = opts.headers || {};
          if (!forceNoAuth) {
            opts.headers["Authorization"] = this.auth.type + " " + this.auth.getToken();
          }
          if (!callback) {
            return self.requestHandler(opts);
          } else {
            var timer;
            var retry = function () {
              self.sendRequest(originalOpts, self.retryHandler(callback, retry, timer));
            };
            if (self.timerStart) {
              timer = self.timerStart();
            }

            return self.requestHandler(opts, self.retryHandler(callback, retry, timer));
          }
        } else {
          callback.call(this, new Error("Not authorized"), {
            statusCode: 0
          }, null);
        }

      }

      enginePrototypeMethods.retryHandler = function (callback, retry, timer) {
        var self = this;
        return function (error, response, body) {
          //build an error object for http errors
          if (!error && response.statusCode >= 400 && response.statusCode < 600) {
            error = new Error(body);
          }
          try {
            //this shouldn't be required, but server sometimes responds with content-type text/plain
            body = JSON.parse(body);
          } catch (e) { }

          if (response) {
            var retryAfter = response.headers["retry-after"];
            var masheryCode = response.headers["x-mashery-error-code"];
            //in case headers get returned as arrays, we only expect one value
            retryAfter = typeof retryAfter === "array" ? retryAfter[0] : retryAfter;
            masheryCode = typeof masheryCode === "array" ? masheryCode[0] : masheryCode;
          }

          if (
            response &&
            self.options.handleQuota &&
            response.statusCode === 403 &&
            retryAfter
          ) {
            if (masheryCode === "ERR_403_DEVELOPER_OVER_QPS") {
              //retry
              console && console.warn("developer over QPS, retrying");
              self.quota.retrying = 1000 * ~~(retryAfter);
              setTimeout(function () {
                self.quota.retrying = 0;
                retry();

              }, self.quota.retrying);

            }
            if (masheryCode === "ERR_403_DEVELOPER_OVER_RATE") {
              error.RATE = true;
              callback.call(this, error, response, body);
            }

          } else {

            if (
              response &&
              //Checking for failed auth responses
              //(ノಠ益ಠ)ノ彡┻━┻
              self.options.onInvalidToken &&
              (
                response.statusCode === 401 ||
                (
                  response.statusCode === 403 &&
                  masheryCode === "ERR_403_DEVELOPER_INACTIVE"
                )
              )
            ) {
              self.auth.dropToken();
              self.options.onInvalidToken();
            }
            if (self.timerEnd) {
              self.timerEnd(timer);
            }
            callback.call(this, error, response, body);
          }
        };
      }

      enginePrototypeMethods.retrieveStreamFromRequest = function (opts) {
        var defer = promises.defer();
        var self = this;
        var requestFunction = function () {

          try {
            var req = self.sendRequest(opts);
            defer.resolve(req);
          } catch (error) {
            defer.reject(errorify({
              error: error
            }));
          }
        }

        if (!this.options.handleQuota) {
          requestFunction();
        } else {
          //add to queue
          this.queue.push(requestFunction);
          //stop previous queue processing if any
          clearTimeout(this.quota.to);
          //start queue processing
          this.queueHandler();
        }
        return defer.promise;
      }

      enginePrototypeMethods.promiseRequest = function (opts, requestHandler, forceNoAuth) {
        var defer = promises.defer();
        var self = this;
        var requestFunction = function () {
          try {
            var req = self.sendRequest(opts, function (error, response, body) {
              if (error) {
                defer.reject(errorify({
                  error: error,
                  response: response,
                  body: body
                }));
              } else {
                defer.resolve({
                  response: response,
                  body: body
                });
              }
            }, forceNoAuth);
            requestHandler && requestHandler(req);
          } catch (error) {
            defer.reject(errorify({
              error: error
            }));
          }
        }
        if (!this.options.handleQuota) {
          requestFunction();
        } else {
          //add to queue
          this.queue.push(requestFunction);
          //stop previous queue processing if any
          clearTimeout(this.quota.to);
          //start queue processing
          this.queueHandler();
        }
        return defer.promise;
      }

      enginePrototypeMethods.setupTiming = function (getTimer, timeEnd) {
        this.timerStart = getTimer;
        this.timerEnd = timeEnd;
      }

      //gets bound to this in the constructor and saved as this.queueHandler
      function _rollQueue() {
        if (this.queue.length) {
          var currentWait = _quotaWaitTime(this.quota, this.options.QPS);
          if (currentWait === 0) {
            var requestFunction = this.queue.shift();
            requestFunction();
            this.quota.calls++;
          }
          this.quota.to = setTimeout(this.queueHandler, currentWait);
        }

      }

      function _quotaWaitTime(quota, QPS) {
        var now = +new Date();
        var diff = now - quota.startOfTheSecond;
        //in the middle of retrying a denied call
        if (quota.retrying) {
          quota.startOfTheSecond = now + quota.retrying;
          return quota.retrying + 1;
        }
        //last call was over a second ago, can start
        if (diff > 1002) {
          quota.startOfTheSecond = now;
          quota.calls = 0;
          return 0;
        }
        //calls limit not reached
        if (quota.calls < QPS) {
          return 0;
        }
        //calls limit reached, delay to the next second
        return 1003 - diff;
      }


      Engine.prototype = enginePrototypeMethods;

      module.exports = Engine;
    }, { "17": 17, "3": 3, "43": 43, "45": 45, "47": 47, "48": 48 }], 24: [function (require, module, exports) {
      var helpers = require(47);

      function makeId(isFolder, theId) {
        return (isFolder ? "/ids/folder/" : "/ids/file/") + theId;
      }

      function createMethod(implementation, self, pathOrId) {
        if (Function.prototype.bind) {
          return implementation.bind(self, pathOrId);
        } else {
          return function (something, somethingElse) {
            //no need to handle more than path+2 arguments, ever.
            return implementation.call(self, pathOrId, something, somethingElse);
          }
        }
      }

      function wrap(self, pathOrId, APIPrototype) {
        var api = {};
        helpers.each(APIPrototype, function (implementation, name) {
          api[name] = createMethod(implementation, self, pathOrId)

        })
        return api;
      }


      module.exports = function (APIPrototype, opts) {
        return {
          fileId: function (groupId) {
            return wrap(this, makeId(false, groupId), APIPrototype)
          },
          folderId: function (folderId) {
            return wrap(this, makeId(true, folderId), APIPrototype)
          },
          path: function (path) {
            if (opts && opts.pathPrefix) {
              path = opts.pathPrefix + path;
            }
            return wrap(this, path, APIPrototype)
          },
          internals: APIPrototype
        }

      }
    }, { "47": 47 }], 25: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var decorators = require(16);

      var ENDPOINTS_search = require(31).search;


      function Search(requestEngine) {
        this.requestEngine = requestEngine;
        this.count = 10;
        decorators.install(this);
      }


      var searchProto = {};
      searchProto.itemsPerPage = function (count) {
        this.count = count || 10;
      }
      searchProto.query = function (query, page) {
        var self = this;
        var requestEngine = self.requestEngine;
        var decorate = self.getDecorator();
        return promises(true).then(function () {
          var qs = [
            "query=" + encodeURIComponent(query),
            "offset=" + (~~(page) * self.count),
            "count=" + self.count,
          ];
          var querystring = "?" + qs.join("&");
          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS_search + querystring
          };
          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return result.body;
        });

      };

      searchProto.getResults = function (query) {
        var self = this;
        return self.query(query)
          .then(function (body) {
            return {
              page: function (number) {
                return self.query(query, number)
                  .then(function (body) {
                    return body.results;
                  });
              },
              totalPages: Math.round(body.total_count / self.count),
              sample: body.results,
              totalCount: body.total_count
            }
          });
      };


      Search.prototype = searchProto;

      module.exports = Search;

    }, { "16": 16, "31": 31, "43": 43, "47": 47 }], 26: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var decorators = require(16);
      var notes = require(21);
      var lock = require(20);
      var chunkedUpload = require(15);
      var resourceIdentifier = require(24);

      var ENDPOINTS = require(31);


      function Storage(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);
      }

      var storageProto = {};
      storageProto.exists = function (pathFromRoot) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot),
          };

          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          if (result.response.statusCode == 200) {
            return true;
          } else {
            return false;
          }
        }, function (result) { //result.error result.response, result.body
          if (result.response && result.response.statusCode == 404) {
            return false;
          } else {
            throw result;
          }
        });
      }

      storageProto.get = function (pathFromRoot, versionEntryId) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot),
          };

          if (versionEntryId) {
            opts.params = {
              "entry_id": versionEntryId
            };
          }

          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return result.body;
        });
      }

      //undocumented, will bo official in v3
      storageProto.parents = function (pathFromRoot) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot) + "/parents",
          };

          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return result.body;
        });
      }

      storageProto.download = function (pathFromRoot, versionEntryId, isBinary) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);

          var opts = {
            method: "GET",
            url: requestEngine.getEndpoint() + ENDPOINTS.fscontent + helpers.encodeURIPath(pathFromRoot),
          }
          if (versionEntryId) {
            opts.params = {
              "entry_id": versionEntryId
            };
          }

          if (isBinary) {
            opts.responseType = "arraybuffer";
          }

          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return result.response;
        });
      }

      storageProto.createFolder = function (pathFromRoot) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          var opts = {
            method: "POST",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot),
            json: {
              "action": "add_folder"
            }
          };
          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          if (result.response.statusCode == 201) {
            return {
              path: pathFromRoot
            };
          }
        });
      }

      storageProto.move = storageProto.rename = function (pathFromRoot, newPath) {
        return transfer(this.requestEngine, this.getDecorator(), pathFromRoot, newPath, "move");
      }

      storageProto.copy = function (pathFromRoot, newPath) {
        return transfer(this.requestEngine, this.getDecorator(), pathFromRoot, newPath, "copy");
      }

      function transfer(requestEngine, decorate, pathFromRoot, newPath, action) {
        return promises(true).then(function () {
          if (!newPath) {
            throw new Error("Cannot move to empty path");
          }
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot);
          newPath = helpers.encodeNameSafe(newPath);
          var opts = {
            method: "POST",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot),
            json: {
              "action": action,
              "destination": newPath,
            }
          };
          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          if (result.response.statusCode == 200) {
            return {
              oldPath: pathFromRoot,
              path: newPath
            };
          }
        });
      }

      storageProto.storeFile = function (pathFromRoot, fileOrBlob, mimeType /* optional */) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          var file = fileOrBlob;
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";

          var opts = {
            method: "POST",
            url: requestEngine.getEndpoint() + ENDPOINTS.fscontent + helpers.encodeURIPath(pathFromRoot),
            body: file,
          }

          opts.headers = {};
          if (mimeType) {
            opts.headers["Content-Type"] = mimeType;
          }

          return requestEngine.promiseRequest(decorate(opts));
        }).then(function (result) { //result.response result.body
          return ({
            id: result.response.headers["etag"],
            group_id: result.body.group_id,
            path: pathFromRoot
          });
        });
      }

      //currently not supported by back - end
      //
      //function storeFileMultipart(pathFromRoot, fileOrBlob) {
      //    return promises(true).then(function () {
      //        if (!window.FormData) {
      //            throw new Error("Unsupported browser");
      //        }
      //        var file = fileOrBlob;
      //        var formData = new window.FormData();
      //        formData.append('file', file);
      //        pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";
      //        var opts = {
      //            method: "POST",
      //            url: api.getEndpoint() + fscontent + helpers.encodeURIPath(pathFromRoot),
      //            body: formData,
      //        };
      //        return api.promiseRequest(decorate(opts));
      //    }).then(function (result) { //result.response result.body
      //        return ({
      //            id: result.response.getResponseHeader("etag"),
      //            path: pathFromRoot
      //        });
      //    });
      //}


      //private
      function remove(requestEngine, decorate, pathFromRoot, versionEntryId) {
        return promises(true).then(function () {
          pathFromRoot = helpers.encodeNameSafe(pathFromRoot) || "";
          var opts = {
            method: "DELETE",
            url: requestEngine.getEndpoint() + ENDPOINTS.fsmeta + helpers.encodeURIPath(pathFromRoot),
          };
          if (versionEntryId) {
            opts.params = {
              "entry_id": versionEntryId
            };
          }
          return requestEngine.promiseRequest(decorate(opts));

        }).then(function (result) { //result.response result.body
          return result.response.statusCode;
        });
      }

      storageProto.removeFileVersion = function (pathFromRoot, versionEntryId) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();
        return promises(true).then(function () {
          if (!versionEntryId) {
            throw new Error("Version ID (second argument) is missing");
          }
          return remove(requestEngine, decorate, pathFromRoot, versionEntryId)
        });
      }


      storageProto.remove = function (pathFromRoot, versionEntryId) {
        var decorate = this.getDecorator();
        return remove(this.requestEngine, decorate, pathFromRoot, versionEntryId);
      }

      storageProto = helpers.extend(storageProto, lock);
      storageProto = helpers.extend(storageProto, chunkedUpload);

      Storage.prototype = resourceIdentifier(storageProto);

      module.exports = Storage;

    }, { "15": 15, "16": 16, "20": 20, "21": 21, "24": 24, "31": 31, "43": 43, "47": 47 }], 27: [function (require, module, exports) {
      var promises = require(43);
      var decorators = require(16);

      var ENDPOINTS_users = require(31).users;

      function User(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);
      }

      var userProto = {};

      userProto.getById = function (userId) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();

        return promises(true)
          .then(function () {
            var opts = {
              method: "GET",
              url: requestEngine.getEndpoint() + ENDPOINTS_users + userId
            };
            return requestEngine.promiseRequest(decorate(opts));
          }).then(function (result) { //result.response result.body
            return result.body;
          });
      };
      userProto.getByName = function (username) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();

        return promises(true)
          .then(function () {
            var opts = {
              method: "GET",
              url: requestEngine.getEndpoint() + ENDPOINTS_users,
              params: {
                filter: "userName eq \"" + username + "\""
              }
            };
            return requestEngine.promiseRequest(decorate(opts));
          }).then(function (result) { //result.response result.body
            if (result.body.resources && result.body.resources[0]) {
              return result.body.resources[0];
            } else {
              var err = Error("User not found")
              err.statusCode = 404;
              throw err;
            }
          });
      };

      User.prototype = userProto;

      module.exports = User;

    }, { "16": 16, "31": 31, "43": 43 }], 28: [function (require, module, exports) {
      var promises = require(43);
      var decorators = require(16);

      var ENDPOINTS_perms = require(31).perms;

      function UserPerms(requestEngine) {
        this.requestEngine = requestEngine;
        decorators.install(this);

        this.addDecorator("path", pointFolder("folder"));
        this.addDecorator("folderId", pointFolder("folder_id"));

      }

      function pointFolder(what) {
        return function (opts, data) {
          opts.params || (opts.params = {});
          opts.params[what] = data;
          return opts;
        };
      }

      var userPermsProto = {};

      userPermsProto.get = function (user) {
        var requestEngine = this.requestEngine;
        var decorate = this.getDecorator();

        return promises(true)
          .then(function () {
            var opts = {
              method: "GET",
              url: requestEngine.getEndpoint() + ENDPOINTS_perms + "/user" + (user ? "/" + user : "")
            };
            return requestEngine.promiseRequest(decorate(opts));
          }).then(function (result) { //result.response result.body
            return result.body;
          });
      };

      UserPerms.prototype = userPermsProto;

      module.exports = UserPerms;

    }, { "16": 16, "31": 31, "43": 43 }], 29: [function (require, module, exports) {
      var helpers = require(47);
      var dom = require(45);
      var messages = require(48);

      function serializablifyXHR(res) {
        var resClone = {};
        for (var key in res) {
          //purposefully getting items from prototype too
          if (typeof res[key] !== "function" && key !== "headers") {
            resClone[key] = res[key];
          }
        };
        return resClone;
      }

      function init(options, api) {

        var channel;

        channel = {
          marker: options.channelMarker,
          sourceOrigin: options.egnyteDomainURL
        };

        function actionsHandler(message) {
          if (message.action && message.action === "call") {
            var data = message.data;
            if (api[data.ns] && api[data.ns][data.name]) {
              api.auth.setToken(data.token);
              api[data.ns][data.name].apply(api[data.ns], data.args).then(function (res) {
                if (res instanceof XMLHttpRequest) {
                  res = serializablifyXHR(res);
                }
                messages.sendMessage(window.parent, channel, "result", {
                  status: true,
                  resolution: res,
                  uid: data.uid
                });
              }, function (res) {
                messages.sendMessage(window.parent, channel, "result", {
                  status: false,
                  resolution: res,
                  uid: data.uid
                });
              })

            } else {
              //send something to clean up the caller
              messages.sendMessage(window.parent, channel, "nomethod", {
                uid: data.uid
              });
            }
          }
        }

        channel.handler = messages.createMessageHandler(null, channel.marker, actionsHandler);
        channel._evListener = dom.addListener(window, "message", channel.handler);

      }

      module.exports = init;
    }, { "45": 45, "47": 47, "48": 48 }], 30: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var dom = require(45);
      var messages = require(48);



      var pending = {};
      var origin = "";


      function actionsHandler(message) {
        var data = message.data;
        if (message.action && message.data && pending[data.uid]) {
          if (message.action === "result") {
            pending[data.uid](data.status, data.resolution);
            pending[data.uid] = null;
          }
          if (message.action === "nomethod") {
            pending[data.uid] = null;
          }
        }
      }

      function guid() {
        return ("" + ~~(Math.random() * 9999999) + ~~(Math.random() * 9999999))
      }


      function remoteCall(channel, namespaceName, methodName, token, args, callback) {
        var uid = guid();
        pending[uid] = callback;
        messages.sendMessage(channel.iframe.contentWindow, channel, "call", {
          ns: namespaceName,
          name: methodName,
          args: args,
          token: token,
          uid: uid
        }, origin);

      }

      function forwardMethod(namespaceName, methodName, channel, getToken) {
        return function () {
          var args = Array.prototype.slice.call(arguments, 0);
          var defer = promises.defer();
          channel.ready.promise.then(function () {
            remoteCall(channel, namespaceName, methodName, getToken(), args, function (status, resolution) {
              if (status) {
                defer.resolve(resolution);
              } else {
                defer.reject(resolution);
              }

            });
          });
          return defer.promise;
        }

      }

      function setupForwarding(api, channel) {

        var mkForwarder = function (namespaceName, method) {
          api[namespaceName][method] = forwardMethod(namespaceName, method, channel, function () {
            return api.auth.getToken()
          });
        }

        //forwarding setup
        helpers.each(api, function (apiNamespace, namespaceName) {
          if (namespaceName !== "auth") {
            for (var method in apiNamespace) {
              mkForwarder(namespaceName, method);
            }
          }
        });
        //manual forwarder, leave other auth methods be
        mkForwarder("auth", "getUserInfo");

        var parentDestroy = api.destroy;
        api.destroy = function () {
          channel._evListener.destroy();
          channel.iframe.parentNode.removeChild(channel.iframe);
          if (parentDestroy) {
            return parentDestroy.apply(api, arguments)
          }
        }

        return api;
      }


      function init(options, api) {
        origin = options.egnyteDomainURL;
        //comm setup
        var iframe;
        var channel;

        channel = {
          marker: options.channelMarker,
          sourceOrigin: options.egnyteDomainURL,
          ready: promises.defer()
        };

        channel.handler = messages.createMessageHandler(channel.sourceOrigin, channel.marker, actionsHandler);
        channel._evListener = dom.addListener(window, "message", channel.handler);

        iframe = dom.createFrame(options.egnyteDomainURL + "/" + options.forwarderAddress);
        iframe.style.display = "none";

        //give IE time to get the iframe going
        var onIframeLoad = function () {
          setTimeout(function () {
            channel.ready.resolve();
          }, 50);
        }

        if (iframe.addEventListener) {
          iframe.addEventListener('load', onIframeLoad, false);
        } else if (iframe.attachEvent) {
          iframe.attachEvent('onload', onIframeLoad);
        }
        var body = document.body || document.getElementsByTagName("body")[0];
        body.appendChild(iframe);

        channel.iframe = iframe;

        return setupForwarding(api, channel);

      }

      module.exports = init;
    }, { "43": 43, "45": 45, "47": 47, "48": 48 }], 31: [function (require, module, exports) {
      module.exports = {
        "fsmeta": "/v1/fs",
        "fscontent": "/v1/fs-content",
        "fschunked": "/v1/fs-content-chunked",
        "notes": "/v1/notes",
        "links": "/v1/links",
        "perms": "/v1/perms",
        "userinfo": "/v1/userinfo",
        "users": "/v2/users/",
        "events": "/v1/events",
        "search": "/v1/search",
        "eventscursor": "/v1/events/cursor",
        "tokenauth": "/puboauth/token"
      }

    }, {}], 32: [function (require, module, exports) {
      var helpers = require(47);

      module.exports = function (opts, model) {
        var page = 1;
        var totalPages = 1;
        var rawItems;
        var rawItemSelf;
        var currentPath;


        function fetchImplementation(path) {
          if (path) {
            currentPath = path;
          }
          return opts.API.storage.path(currentPath).get().then(function (m) {
            return setData(m);
          });
        }

        function canJump(offset) {
          var newPage = page + offset;
          return (newPage <= totalPages && newPage > 0)
        }

        function switchPage(offset) {
          if (canJump(offset)) {
            page += offset;
          }
          return opts.API.manual.promise(buildDataObj());
        }

        function buildDataObj() {
          var pageArr = rawItems.slice((page - 1) * opts.pageSize, page * opts.pageSize);

          return {
            canJump: canJump,
            switchPage: switchPage,
            page: page,
            totalPages: totalPages,
            items: pageArr,
            itemSelf: rawItemSelf
          };
        }

        function setData(m) {
          page = 1;
          rawItems = [];
          rawItemSelf = null;
          if (m) {
            currentPath = m.path;

            helpers.each(m.folders, function (f) {
              rawItems.push(f);
            });
            //ignore files if they're not selectable
            if (opts.filesOn) {
              helpers.each(m.files, function (f) {
                if (!opts.fileFilter || opts.fileFilter(f)) {
                  rawItems.push(f);
                }
              });
            }
            rawItemSelf = m
            delete rawItemSelf.files
            delete rawItemSelf.folders
          }

          totalPages = ~~(rawItems.length / opts.pageSize) + 1;

          return buildDataObj();


        }

        model.fetch = function (path) {
          var self = this;
          if (!self.processing) {
            self.processing = true;
            if (path) {
              self.path = path;
            }
            this.viewState.searchOn = false;
            self.onloading();
            fetchImplementation(self.path).then(function (data) {
              self._itemsUpdated(data)
              model.opts.handlers.navigation({
                path: model.path,
                folder_id: model.itemSelf.folder_id,
                forbidSelection: model.forbidSelection
              });
            }).fail(function (e) {
              self._itemsUpdated()
              self.onerror(e);
            });
          }
        }


        model.goUp = function () {
          var path = this.path.replace(/\/[^\/]+\/?$/i, "") || "/";

          if (path !== this.path) {
            this.fetch(path);
          }
        }





      }

    }, { "47": 47 }], 33: [function (require, module, exports) {
      var helpers = require(47);

      module.exports = function (opts, model) {
        var currentQuery;
        var previousQuery;
        var currentResult;
        var page;

        opts.API.search.itemsPerPage(opts.pageSize)


        function searchImplementation(query) {
          currentQuery = query;
          return opts.API.search.getResults(query).then(function (response) {
            //if no query was started in the meantime
            if (currentQuery === query) {
              currentResult = response;
              page = 0;
              return buildDataObj(response.sample);
            }
          });
        }

        function canJump(offset) {
          var newPage = page + offset;
          return (newPage <= currentResult.totalPages && newPage > 0)
        }

        function switchPage(offset) {
          if (canJump(offset)) {
            page += offset;
          }
          return currentResult.page(page).then(buildDataObj);
        }

        function buildDataObj(items) {
          if (opts.fileFilter) {
            helpers.each(items, function (item) {
              if (!opts.fileFilter(item)) {
                item.disabled = true;
              }
            });
          }
          return {
            canJump: canJump,
            switchPage: switchPage,
            page: page + 1,
            totalPages: currentResult.totalPages,
            items: items
          };
        }

        model.cancelSearch = function () {
          currentQuery = null;
          previousQuery = null;
          this.processing = false;
          //hide search results by reloading current folder
          this.fetch();
        }
        model.search = function (query) {
          var self = this;
          if (previousQuery !== query) {
            self.processing = true;
            this.viewState.searchOn = true;
            self.onloading();
            previousQuery = query;
            searchImplementation(query).then(function (data) {
              if (data) {
                self._itemsUpdated(data)
              }
            }).fail(function (e) {
              self._itemsUpdated()
              self.onerror(e);
            });
          }
        }


      }

    }, { "47": 47 }], 34: [function (require, module, exports) {
      module.exports = {
        "404": "This item doesn't exist (404)",
        "403": "Access denied (403)",
        "409": "Forbidden location (409)",
        "596": "Path contains an unexpected character (596)",
        "4XX": "Incorrect API request",
        "5XX": "API server error, try again later",
        "R": "API use limit reached",
        "0": "Browser error, try again",
        "?": "Unknown error"
      }

    }, {}], 35: [function (require, module, exports) {
      var helpers = require(47);
      var mapping = {};
      helpers.each({
        "audio": ["mp3", "wav", "wma", "aiff", "mid", "midi", "mp2"],
        "video": ["wmv", "avi", "mpg", "mpeg", "mp4", "webm", "ogv", "flv", "mov"],
        "pdf": ["pdf"],
        "word_processing": ["doc", "dot", "docx", "dotx", "docm", "dotm", "odt ", "ott", "oth", "odm", "sxw", "stw", "sxg", "sdw", "sgl", "rtf", "hwp", "uot", "wpd", "wps"],
        "spreadsheet": ["123", "xls", "xlt", "xla", "xlsx", "xltx", "xlsm", "xltm", "xlam", "xlsb", "ods", "fods", "ots", "sxc", "stc", "sdc", "csv", "uos"],
        "presentation": ["ppt", "pot", "pps", "ppa", "pptx", "potx", "ppsx", "ppam", "pptm", "potm", "ppsm", "odp", "fodp", "otp", "sxi", "sti", "sdd", "sdp"],
        "cad": ["dwg", "dwf", "dxf", "sldprt", "sldasm", "slddrw"],
        "text": ["txt", "log"],
        "image": ["odg", "otg", "odi", "sxd", "std", "sda", "svm", "jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "psd", "eps", "tga", "wmf", "ai", "cgm", "fodg", "jfif", "pbm", "pcd", "pct", "pcx", "pgm", "ppm", "ras", "sgf", "svg"],
        "code": ["html", "htm", "sql", "xml", "java", "cpp", "c", "perl", "py", "rb", "php", "js", "css", "applescript", "as3", "as", "bash", "shell", "sh", "cfm", "cfml", "cs", "pas", "dcu", "diff", "patch", "ez", "erl", "groovy", "gvy", "gy", "gsh", "javafx", "jfx", "pl", "pm", "ps1", "ruby", "sass", "scss", "scala", "vb", "vbscript", "xhtml", "xslt"],
        "archive": ["zip", "rar", "tar", "gz", "7z", "bz2", "z", "xz", "ace", "sit", "sitx", "tgz", "apk"],
        "goog": ["gdoc", "gsheet", "gslides", "gdraw"]
        //    "email": ["msg", "olk14message", "pst", "emlx", "olk14event", "eml", "olk14msgattach", "olk14msgsource"],
      }, function (list, mime) {
        helpers.each(list, function (ex) {
          mapping[ex] = mime;
        });
      });


      function _mime(ext) {
        return mapping[ext] || "unknown";
      }

      function getExt(name) {
        var splitted = name.split(".");
        if (splitted.length > 1) {
          return splitted[splitted.length - 1];
        } else {
          return "";
        }
      }

      function getMime(name) {
        return _mime(getExt(name));
      }


      function getExtensionFilter(filter) {
        return function (file) {
          var ext = getExt(file.name);
          return filter(ext, _mime(ext));
        }
      }

      module.exports = {
        getMime: getMime,
        getExt: getExt,
        getExtensionFilter: getExtensionFilter
      }
    }, { "47": 47 }], 36: [function (require, module, exports) {
      var helpers = require(47);
      var dom = require(45);
      var View = require(41);
      var Model = require(37);

      function noGoog(ext, mime) {
        return mime !== "goog";
      }

      function init(API) {
        var filePicker;

        filePicker = function (node, setup) {
          if (!setup) {
            throw new Error("Setup required as a second argument");
          }
          var close, openPath, fpView, fpModel,
            defaults = {
              folder: true,
              file: true,
              multiple: true,
              forbidden: []
            };
          var selectOpts = helpers.extend(defaults, setup.select);

          close = function () {
            fpView.destroy();
            fpView = null;
            fpModel = null;
          };

          openPath = function (path) {
            fpModel.fetch(path || "/");
          }

          fpModel = new Model(API, {
            select: selectOpts,
            filterExtensions: (typeof setup.filterExtensions === "undefined") ? noGoog : setup.filterExtensions,
            handlers: {
              navigation: function (currentFolder) {
                setup.navigation && setup.navigation(currentFolder);
              }
            }
          });

          fpView = new View({
            el: node,
            model: fpModel,
            barAlign: setup.barAlign,
            handlers: {
              ready: setup.ready,
              selection: function (items) {
                close();
                setup.selection && setup.selection(items);
              },
              close: function (e) {
                close();
                setup.cancel && setup.cancel(e);
              },
              error: setup.error
            },
            keys: setup.keys
          }, setup.texts);

          openPath(setup.path || "/");

          return {
            getCurrentFolder: function () {
              return {
                path: fpModel.path,
                folder_id: fpModel.itemSelf.folder_id,
                forbidSelection: fpModel.forbidSelection
              };
            },
            openPath: openPath,
            close: close,
          };
        };

        return filePicker;

      }

      module.exports = init;
    }, { "37": 37, "41": 41, "45": 45, "47": 47 }], 37: [function (require, module, exports) {
      var helpers = require(47);
      var exts = require(35);
      var Item = require(38);
      var folderFetchProvider = require(32);
      var searchProvider = require(33);

      //Collection
      function Model(API, opts) {
        this.opts = opts;
        this.page = 1;
        this.isMultiselectable = (this.opts.select.multiple);
        this.viewState = {}

        var dataProviderSettings = {
          API: API,
          pageSize: 100,
          filesOn: opts.select.filesRemainVisible || opts.select.file,
          fileFilter: opts.filterExtensions && exts.getExtensionFilter(opts.filterExtensions)
        };
        //creates this.fetch and this.goUp
        folderFetchProvider(dataProviderSettings, this);
        //creates this.search
        searchProvider(dataProviderSettings, this);

      }


      Model.prototype.onloading = helpers.noop;
      Model.prototype.onupdate = helpers.noop;
      Model.prototype.onerror = helpers.noop;



      Model.prototype._itemsUpdated = function (data) {
        var self = this;
        self.processing = false;
        self.dataSrc = data;
        this.currentItem = -1;
        var pathArray = helpers.normalizePath(this.path).split("/");
        pathArray.pop();
        this.parentForbidsSelection = pathArray.length > 0 ? helpers.contains(this.opts.select.forbidden, pathArray.join("/") || "/") : false;
        if (data) {
          //force disabled selection on root or other path
          this.forbidSelection = helpers.contains(this.opts.select.forbidden, helpers.normalizePath(this.path));
          this.items = [];
          helpers.each(data.items, function (item) {
            self.items.push(new Item(item, self));
          });
          this.isEmpty = data.items.length === 0;
          this.hasPages = data.totalPages > 1;
          this.totalPages = data.totalPages;
          this.page = data.page;
          this.itemSelf = data.itemSelf;
        } else {
          this.items = [];
          this.isEmpty = true;
          this.hasPages = false;
        }

        this.onupdate();
        this.onchange();

      }

      Model.prototype.switchPage = function (offset) {
        var self = this;
        if (!self.processing && self.dataSrc.canJump(offset)) {
          self.processing = true;
          self.dataSrc.switchPage(offset).then(function (data) {
            self._itemsUpdated(data)
          }, function (e) {
            self._itemsUpdated()
            self.onerror(e);
          }).fail(self.onerror);
        }
      }



      Model.prototype.getSelected = function () {
        var selected = [];
        helpers.each(this.items, function (item) {
          if (item.selected) {
            selected.push(item.data);
          }
        });
        return selected;
      }

      Model.prototype.deselect = function () {
        helpers.each(this.items, function (item) {
          if (item.selected) {
            item.selected = false;
            item.onchange();
          }
        });
      }
      Model.prototype.setAllSelection = function (selected) {
        helpers.each(this.items, function (item) {
          item.selected = selected;
          item.onchange();
        });
        this.onchange();
      }

      Model.prototype.mvCurrent = function (offset) {
        if (this.currentItem + offset < this.items.length && this.currentItem + offset >= 0) {
          if (this.items[this.currentItem]) {
            this.items[this.currentItem].isCurrent = false;
            this.items[this.currentItem].onchange();
          }
          this.currentItem += offset;
          this.items[this.currentItem].isCurrent = true;
          this.items[this.currentItem].onchange();
        }
      }

      Model.prototype.getCurrent = function () {
        return this.items[this.currentItem];
      }

      module.exports = Model;

    }, { "32": 32, "33": 33, "35": 35, "38": 38, "47": 47 }], 38: [function (require, module, exports) {
      var exts = require(35);

      //Item model
      function Item(data, parent) {
        this.data = data;
        if (!this.data.is_folder) {
          this.ext = exts.getExt(data.name).substr(0, 3);
          this.mime = exts.getMime(data.name);
        } else {
          this.ext = "";
          this.mime = "folder";
        }
        this.isSelectable = (!data.disabled) && ((parent.opts.select.folder && data.is_folder) || (parent.opts.select.file && !data.is_folder)) && !parent.forbidSelection;
        this.parent = parent;
        this.isCurrent = false;
      }

      Item.prototype.defaultAction = function () {
        if (this.data.is_folder) {
          this.parent.fetch(this.data.path);
        } else {
          this.toggleSelect();
        }
      };

      Item.prototype.toggleSelect = function () {
        if (this.isSelectable) {
          if (!this.parent.opts.select.multiple) {
            this.parent.deselect();
          }
          this.selected = !this.selected;
          this.onchange();
          this.parent.onchange();
        }
      };

      module.exports = Item;

    }, { "35": 35 }], 39: [function (require, module, exports) {
      var helpers = require(47);
      var jungle = require(52);

      function beradcrumbView(parent) {
        var self = this;
        var myElements = this.els = {};
        self.model = parent.model;

        myElements.selectAll = jungle.node(
          ["input[type=checkbox]", {
            title: parent.txt("Select all")
          }]
        );
        myElements.back = jungle.node(["a.eg-picker-back.eg-btn[title=back]"]);
        myElements.crumb = jungle.node(["span.eg-picker-path"]);


        parent.handleClick(myElements.selectAll, function (e) {
          parent.model.setAllSelection(!!e.target.checked);
        });
        parent.handleClick(myElements.back, parent.goUp);
        parent.handleClick(myElements.crumb, function (e) {
          var path = e.target.getAttribute("data-path");
          if (path) {
            self.model.fetch(path);
          }
        });


      }
      beradcrumbView.prototype.getTree = function () {
        var myElements = this.els;
        var topbar = ["div.eg-bar.eg-top"];
        if (this.model.isMultiselectable) {
          myElements.selectAll.checked = false;
          topbar.push(myElements.selectAll);
        }
        topbar.push(myElements.back);
        topbar.push(myElements.crumb);

        topbar = jungle.node(topbar);

        return topbar;
      }


      beradcrumbView.prototype.render = function () {
        var currentPath = "/";
        var path = this.model.path || currentPath; //in case path was not provided, go for root

        var list = path.split("/");
        var crumbItems = [];
        var maxSpace = ~~(100 / list.length); //assigns maximum space for text
        helpers.each(list, function (folder, num) {
          if (folder) {
            currentPath += folder + "/";
            num > 1 && (crumbItems.push(["span", "/"]));
            crumbItems.push(["a", {
              "data-path": currentPath,
              "title": folder,
              "style": "max-width:" + maxSpace + "%"
            },
              folder
            ]);

          } else {
            if (num === 0) {
              crumbItems.push(["a", {
                "data-path": currentPath
              }, "/"]);
            }
          }
        });
        this.els.crumb.innerHTML = "";
        this.els.crumb.appendChild(jungle.tree([crumbItems]));

      }

      module.exports = beradcrumbView;

    }, { "47": 47, "52": 52 }], 40: [function (require, module, exports) {
      var helpers = require(47);
      var dom = require(45);
      var jungle = require(52);

      var airaExpanded = "aria-expanded";

      function searchView(parent) {
        var self = this;
        var myElements = this.els = {};
        self.evs = [];
        self.model = parent.model;

        self.action = helpers.bindThis(self, actionImplementation);

        myElements.close = jungle.node(["a.eg-search-x.eg-btn", "+"]);
        myElements.ico = jungle.node(["a.eg-btn.eg-search-ico[tabindex=2]"]);
        myElements.input = jungle.node(["input[placeholder=" + parent.txt("Search in files") + "][tabindex=1]"]);
        myElements.field = jungle.node(["div.eg-search-inpt", myElements.input]);

        parent.handleClick(myElements.close, function () {
          self.model.viewState.searchOn = false;
          self.model.cancelSearch();

          self.el.removeAttribute(airaExpanded);
        });

        function invoke() {
          if (self.model.viewState.searchOn) {
            self.action();
          } else {
            self.model.viewState.searchOn = true;
            self.el.setAttribute(airaExpanded, true);
            myElements.input.focus();
          }
        }

        parent.handleClick(myElements.ico, invoke);
        parent.evs.push(dom.onKeys(myElements.ico, {
          "<space>": invoke
        }, true));
        parent.evs.push(dom.onKeys(myElements.input, {
          "<enter>": self.action,
          "other": helpers.debounce(self.action, 800)
        }, true));

      }
      searchView.prototype.getTree = function () {
        var myElements = this.els;
        var searchBarDefinition = "div.eg-search.eg-bar"
        if (this.model.viewState.searchOn) {
          searchBarDefinition += "[" + airaExpanded + "=true]";
        }
        var el = [searchBarDefinition];

        el.push(myElements.close);
        el.push(myElements.field);
        el.push(myElements.ico);

        el = jungle.node(el);
        this.el = el;

        return el;
      }
      searchView.prototype.render = function () {
        if (this.model.viewState.searchOn) {
          setTimeout(this.els.input.focus(), 0);
        }
      }

      function actionImplementation() {
        var myElements = this.els;
        if (myElements.input.value && myElements.input.value.length > 2) {
          this.model.search(myElements.input.value)
        }
      }


      module.exports = searchView;

    }, { "45": 45, "47": 47, "52": 52 }], 41: [function (require, module, exports) {
      "use strict";

      //template engine based upon JsonML
      var dom = require(45);
      var helpers = require(47);
      var texts = require(49);
      var jungle = require(52);
      var SubvBread = require(39);
      var SubvSearch = require(40);

      require(50);

      var fontLoaded = false;

      var currentGlobalKeyboadrFocus = "no";

      function View(opts, txtOverride) {
        var self = this;
        this.uid = Math.random();
        currentGlobalKeyboadrFocus = this.uid;
        this.el = opts.el;
        this.evs = [];
        var myElements = this.els = {};

        if (!opts.noFont) {
          renderFont();
        }

        this.txt = texts(txtOverride);

        this.bottomBarClass = (opts.barAlign === "left") ? "" : ".eg-bar-right";

        this.handlers = helpers.extend({
          selection: helpers.noop,
          events: helpers.noop,
          close: helpers.noop,
          error: null
        }, opts.handlers);

        //action handlers
        //this.selection = helpers.extend(this.selection, opts.selection);
        this.model = opts.model;

        //bind to model events
        this.model.onloading = helpers.bindThis(self, self.renderLoading);
        this.model.onupdate = function () {
          self.handlers.events("beforeRender", self.model);
          self.render();
          self.handlers.events("render", self.model);
          if (self.handlers.ready) {
            var runReady = self.handlers.ready;
            self.handlers.ready = null;
            setTimeout(runReady, 0);
          }
        }
        this.model.onerror = helpers.bindThis(this, this.errorHandler);
        self.kbNav_up = helpers.bindThis(self, self.kbNav_up);
        self.kbNav_down = helpers.bindThis(self, self.kbNav_down);
        self.kbNav_select = helpers.bindThis(self, self.kbNav_select);
        self.kbNav_explore = helpers.bindThis(self, self.kbNav_explore);
        self.model.goUp = helpers.bindThis(self.model, self.model.goUp);
        self.confirmSelection = helpers.bindThis(self, self.confirmSelection);
        self.handlers.close = helpers.bindThis(self, self.handlers.close);

        this.model.onchange = function () {
          if (self.model.getSelected().length > 0 || (self.model.opts.select.folder && !(self.model.forbidSelection || self.model.parentForbidsSelection))) {
            self.els.ok.removeAttribute("disabled");
          } else {
            self.els.ok.setAttribute("disabled", "");
          }
        }

        //create reusable view elements
        myElements.container = jungle.node(["div.eg-in"]);
        myElements.close = jungle.node(["a.eg-picker-close.eg-btn", this.txt("Cancel")]);
        myElements.ok = jungle.node(["span.eg-picker-ok.eg-btn.eg-btn-prim[tabindex=0][role=button]", this.txt("OK")]);
        myElements.pgup = jungle.node(["span.eg-picker-pgup.eg-btn", ">"]);
        myElements.pgdown = jungle.node(["span.eg-picker-pgup.eg-btn", "<"]);


        //bind events and store references to unbind later
        this.handleClick(this.el, self.focused); //maintains focus when multiple instances exist

        this.handleClick(myElements.close, function () {
          self.handlers.close();
        });
        this.handleClick(myElements.ok, self.confirmSelection);
        this.evs.push(dom.onKeys(myElements.ok, {
          "<space>": self.confirmSelection
        }, true));

        this.handleClick(myElements.pgup, function (e) {
          self.model.switchPage(1);
        });
        this.handleClick(myElements.pgdown, function (e) {
          self.model.switchPage(-1);
        });

        if (opts.keys !== false) {
          var keybinding = helpers.extend({
            "up": "<up>",
            "down": "<down>",
            "select": "<space>",
            "explore": "<right>",
            "back": "<left>",
            "confirm": "none",
            "close": "<escape>"
          }, opts.keys);
          var keys = {};
          keys[keybinding["up"]] = self.kbNav_up;
          keys[keybinding["down"]] = self.kbNav_down;
          keys[keybinding["select"]] = self.kbNav_select;
          keys[keybinding["explore"]] = self.kbNav_explore;
          keys[keybinding["back"]] = self.model.goUp;
          keys[keybinding["confirm"]] = self.confirmSelection;
          keys[keybinding["close"]] = self.handlers.close;

          document.activeElement && document.activeElement.blur && document.activeElement.blur();
          this.evs.push(dom.onKeys(document, keys, helpers.bindThis(self, self.hasFocus)));
        }

        //initialize subviews
        self.subviews = {
          breadcrumb: new SubvBread(this),
          search: new SubvSearch(this)
        }

        this.buildLayout();
      }

      var viewPrototypeMethods = {};

      viewPrototypeMethods.destroy = function () {
        helpers.each(this.evs, function (ev) {
          ev.destroy();
        });
        this.evs = null;
        this.el.innerHTML = "";
        this.el = null;
        this.els = null;
        this.model = null;
        this.handlers = null;
      }

      viewPrototypeMethods.handleClick = function (el, method) {
        this.evs.push(dom.addListener(el, "click", helpers.bindThis(this, method)));
      }

      viewPrototypeMethods.errorHandler = function (e) {
        if (this.handlers.error) {
          var message = this.handlers.error(e);
          if (typeof message === "string") {
            this.renderProblem("*", message);
            return;
          } else {
            if (message === false) {
              return;
            }
          }
        }
        this.renderProblem((e.RATE) ? "R" : e.statusCode, e.message);
      }


      //=================================================================
      // rendering
      //=================================================================

      //all this mess is because IE8 dies on @include in css
      function renderFont() {
        if (!fontLoaded) {
          (document.getElementsByTagName("head")[0]).appendChild(jungle.tree([
            ["link", {
              href: "https://fonts.googleapis.com/css?family=Open+Sans:400,600",
              type: "text/css",
              rel: "stylesheet"
            }]
          ]));
          fontLoaded = true;
        }
      }

      viewPrototypeMethods.buildLayout = function () {
        var self = this;
        var myElements = this.els;

        var search = self.subviews.search.getTree();

        var layoutFragm = jungle.tree([
          ["div.eg-theme.eg-picker.eg-widget", search,
            myElements.container
          ]
        ]);

        this.el.innerHTML = "";
        this.el.appendChild(layoutFragm);

      }

      viewPrototypeMethods.render = function () {
        var self = this;
        var myElements = this.els;

        myElements.list = document.createElement("ul");

        var topbar = self.subviews.breadcrumb.getTree();

        var layoutFragm = jungle.tree([

          topbar,
          myElements.list, ["div.eg-bar" + this.bottomBarClass, ["a.eg-brand", {
            title: "egnyte.com"
          }],
          myElements.ok,
          myElements.close, ["div.eg-picker-pager" + (this.model.hasPages ? "" : ".eg-not"),
          myElements.pgdown, ["span", this.model.page + "/" + this.model.totalPages],
          myElements.pgup
          ]
          ]

        ]);

        myElements.container.innerHTML = "";
        myElements.container.appendChild(layoutFragm);
        //couldn't CSS it. blame old browsers
        myElements.list.style.height = (this.el.offsetHeight - 2 * topbar.offsetHeight) + "px";

        self.subviews.breadcrumb.render();

        if (this.model.isEmpty) {
          this.renderEmpty();
        } else {
          helpers.each(this.model.items, function (item) {
            self.renderItem(item);
          });
        }


      }


      viewPrototypeMethods.renderItem = function (itemModel) {
        var self = this;

        var itemName = jungle.node(["a.eg-picker-name" + (itemModel.data.is_folder ? ".eg-folder" : ".eg-file"), {
          "title": itemModel.data.name,
        },
        ["span.eg-ico.eg-mime-" + itemModel.mime, {
          "data-ext": itemModel.ext
        },
        ["span", itemModel.ext]
        ], itemModel.data.name
        ]);

        var checkboxSetup = "input[type=checkbox]";
        if (!itemModel.isSelectable) {
          checkboxSetup += (itemModel.data.is_folder ? ".eg-not" : "[disabled=disabled][title=" +
            this.txt("This file cannot be selected") +
            "]");
        }

        var itemCheckbox = jungle.node([checkboxSetup]);
        itemCheckbox.checked = itemModel.selected;

        var itemNode = jungle.node(["li.eg-picker-item" +
          (itemModel.isSelectable ? "" : ".eg-disabled") +
          (itemModel.selected ? ".eg-selected" : ""),
        (self.model.opts.select.multiple === false) ? [] : itemCheckbox,
          itemName
        ]);

        dom.addListener(itemName, "click", function (e) {
          if (e.stopPropagation) {
            e.stopPropagation();
          }
          itemModel.defaultAction();
          return false;
        });

        dom.addListener(itemNode, "click", function () {
          itemModel.toggleSelect();
        });

        itemModel.onchange = function () {
          self.handlers.events("itemChange", itemModel);
          itemCheckbox.checked = itemModel.selected;
          itemNode.setAttribute("class", "eg-picker-item" +
            (itemModel.selected ? " eg-selected" : "") +
            (itemModel.isSelectable ? "" : " eg-disabled")
          );
          itemNode.setAttribute("aria-selected", itemModel.isCurrent);
          if (itemModel.isCurrent) {
            try { //IE8 dies on this randomly :/
              self.els.list.scrollTop = itemNode.offsetTop - self.els.list.offsetHeight
            } catch (e) { }
            //itemNode.scrollIntoView(false);
          }
        };

        this.els.list.appendChild(itemNode);
      }




      viewPrototypeMethods.renderLoading = function () {
        if (this.els.list) {
          this.els.list.innerHTML = "";
          this.els.list.appendChild(jungle.tree([
            ["div.eg-placeholder", ["div.eg-spinner"], this.txt("Loading")]
          ]));
        }
      }


      var msgs = require(34);

      viewPrototypeMethods.renderProblem = function (code, message) {
        message = msgs["" + code] || msgs[~(code / 100) + "XX"] || message || msgs["?"];
        if (this.els.list) {
          this.els.list.innerHTML = "";
          this.els.list.appendChild(jungle.tree([
            ["div.eg-placeholder", ["div.eg-picker-error"], message]
          ]));
        } else {
          this.handlers.close({
            message: message
          });
        }
      }
      viewPrototypeMethods.renderEmpty = function () {
        if (this.els.list) {
          this.els.list.innerHTML = "";
          if (this.model.viewState.searchOn) {
            this.els.list.appendChild(jungle.tree([
              ["div.eg-search-no", ["p", this.txt("No search results found")]]
            ]));
          } else {

            this.els.list.appendChild(jungle.tree([
              ["div.eg-placeholder.eg-folder", ["div.eg-ico"], this.txt("This folder is empty")]
            ]));
          }
        }
      }

      //=================================================================
      // focus
      //=================================================================

      viewPrototypeMethods.hasFocus = function () {
        return currentGlobalKeyboadrFocus === this.uid;
      }
      viewPrototypeMethods.focused = function () {
        currentGlobalKeyboadrFocus = this.uid;
      }

      //=================================================================
      // navigation
      //=================================================================

      viewPrototypeMethods.goUp = function () {
        this.model.goUp();
      }
      viewPrototypeMethods.confirmSelection = function () {
        var selected = this.model.getSelected();
        if (selected && selected.length) {
          this.handlers.selection.call(this, this.model.getSelected());
        } else if (this.model.opts.select.folder && !(this.model.forbidSelection || this.model.parentForbidsSelection)) {
          this.handlers.selection.call(this, [this.model.itemSelf])
        }
      }

      viewPrototypeMethods.kbNav_up = function () {
        this.model.mvCurrent(-1);
      }

      viewPrototypeMethods.kbNav_down = function () {
        this.model.mvCurrent(1);
      }
      viewPrototypeMethods.kbNav_select = viewPrototypeMethods.kbNav_confirm = function () {
        var item = this.model.getCurrent();
        if (item) {
          item.toggleSelect();
        }
      }


      viewPrototypeMethods.kbNav_explore = function () {
        var item = this.model.getCurrent();
        if (item && item.data.is_folder) {
          item.defaultAction();
        }
      }

      View.prototype = viewPrototypeMethods;

      module.exports = View;

    }, { "34": 34, "39": 39, "40": 40, "45": 45, "47": 47, "49": 49, "50": 50, "52": 52 }], 42: [function (require, module, exports) {
      var promises = require(43);
      var helpers = require(47);
      var dom = require(45);
      var messages = require(48);
      var decorators = require(16);
      var ENDPOINTS = require(31);

      var plugins = {};
      module.exports = {
        define: function (name, pluginClosure) {
          if (plugins[name]) {
            throw new Error("Plugin conflict. " + name + " already exists");
          } else {
            plugins[name] = pluginClosure;
          }
        },
        install: function (root) {
          helpers.each(plugins, function (pluginClosure, name) {
            pluginClosure(root, {
              API: root.API,
              ENDPOINTS: ENDPOINTS,
              promises: promises,
              decorators: decorators,
              reusables: {
                helpers: helpers,
                dom: dom,
                messages: messages
              }
            });
          });
        }
      };
    }, { "16": 16, "31": 31, "43": 43, "45": 45, "47": 47, "48": 48 }], 43: [function (require, module, exports) {
      //wrapper for any promises library
      var pinkySwear = require(1);
      var helpers = require(47);

      //for pinkyswear starting versions above 2.10
      var createErrorAlias = function (promObj) {
        promObj.fail = function (func) {
          return promObj.then(0, func);
        };
        return promObj;
      }

      var Promises = function (value) {
        var promise = pinkySwear(createErrorAlias);
        promise(true, [value]);
        return promise;
      }

      Promises.defer = function () {
        var promise = pinkySwear(createErrorAlias);
        return {
          promise: promise,
          resolve: function (a) {
            promise(true, [a]);
          },
          reject: function (a) {
            promise(false, [a]);
          }
        };
      }

      function settler(array, resolver) {

        helpers.each(array, function (promise, num) {
          promise.then(function (result) {
            resolver(num, {
              state: "fulfilled",
              value: result
            });
          }, function (err) {
            resolver(num, {
              state: "rejected",
              reason: err
            });
          })
        });
      }

      Promises.all = function (array) {
        var collectiveDefere = Promises.defer();
        var results = [];
        var counter = array.length;

        settler(array, function (num, item) {
          if (counter) {
            if (item.state === "rejected") {
              counter = 0;
              collectiveDefere.reject(item.reason);
            } else {
              results[num] = item;
              if (--counter === 0) {
                collectiveDefere.resolve(results);
              }
            }
          }
        })
        return collectiveDefere.promise;
      }

      Promises.allSettled = function (array) {
        var collectiveDefere = Promises.defer();
        var results = [];
        var counter = array.length;

        settler(array, function (num, item) {
          results[num] = item;
          if (--counter === 0) {
            collectiveDefere.resolve(results);
          }
        })

        return collectiveDefere.promise;
      }

      module.exports = Promises;

    }, { "1": 1, "47": 47 }], 44: [function (require, module, exports) {
      var helpers = require(47);
      var dom = require(45);
      var jungle = require(53);
      var texts = require(49);

      require(50);

      function openPrompt(node, setup) {
        if (!setup) {
          throw new Error("Setup required as a second argument");
        }
        var render, cleanup, ev;

        var txt = texts(setup.texts);

        cleanup = function () {
          ev.destroy();
          node.innerHTML = "";
        };


        var btOk = jungle([["span.eg-prompt-ok.eg-btn.eg-btn-prim", txt("Ok")]]).childNodes[0];
        var input = jungle([["input[type=text]"]]).childNodes[0];

        ev = (dom.addListener(btOk, "click", function () {
          var val = input.value;
          cleanup();
          setup.result(input.value);
        }));

        var bottomBarClass = (setup.barAlign === "left") ? "" : ".eg-bar-right";

        var layoutFragm = jungle([["div.eg-theme.eg-widget.eg-prompt",
          ["div.eg-ctlgrp",
            ["label.eg-prompt-ask", txt("question")],
            input
          ],
          ["div.eg-bar" + bottomBarClass,
          [
            btOk
          ]
          ]
        ]]);

        node.innerHTML = "";
        node.appendChild(layoutFragm);
        input.focus();

        return {
          close: cleanup,
        };
      };

      module.exports = openPrompt;
    }, { "45": 45, "47": 47, "49": 49, "50": 50, "53": 53 }], 45: [function (require, module, exports) {
      var vkey = require(2);


      function addListener(elem, type, callback) {
        var handler;
        if (elem.addEventListener) {
          handler = callback;
          elem.addEventListener(type, callback, false);

        } else {
          handler = function (e) {
            e = e || window.event; // get window.event if argument is falsy (in IE)
            e.target || (e.target = e.srcElement);
            var res = callback.call(this, e);
            if (res === false) {
              e.cancelBubble = true;
            }
            return res;
          };
          elem.attachEvent("on" + type, handler);
        }

        return {
          destroy: function () {
            removeListener(elem, type, handler);
          }
        }
      }

      function removeListener(elem, type, handler) {
        if (elem.removeEventListener) {
          elem.removeEventListener(type, handler, false);
        } else if (elem.detachEvent) {
          elem.detachEvent(type, handler);
        }
      }



      module.exports = {

        addListener: addListener,

        onKeys: function (elem, actions, hasFocus) {
          return addListener(elem, "keyup", function (ev) {
            if (ev.target.tagName && ev.target.tagName.toLowerCase() !== "input") {
              ev.preventDefault && ev.preventDefault();
            }
            ev.stopPropagation && ev.stopPropagation();
            if (hasFocus === true || hasFocus()) {
              if (actions[vkey[ev.keyCode]]) {
                actions[vkey[ev.keyCode]]();
              } else {
                actions["other"] && actions["other"]();
              }
            }
            return false;
          });
        },

        createFrame: function (url, scrolling) {
          var iframe = document.createElement("iframe");
          if (!scrolling) {
            iframe.setAttribute("scrolling", "no");
          }
          iframe.style.width = "100%";
          iframe.style.height = "100%";
          iframe.style.minWidth = "400px";
          iframe.style.minHeight = "400px";
          iframe.style.border = "1px solid #dbdbdb";
          iframe.src = url;
          return iframe;
        }

      }

    }, { "2": 2 }], 46: [function (require, module, exports) {
      var promises = require(43);
      module.exports = function (interval, func, errorHandler) {
        var pointer, stopped = false,
          repeat = function () {
            clearTimeout(pointer);
            pointer = setTimeout(runner, 1);
          },
          runner = function () {
            var currentPointer = pointer;
            promises({
              interval: interval,
              repeat: repeat
            }).then(func).fail(function (e) {
              if (errorHandler) {
                return errorHandler(e);
              } else {
                console && console.error("Error in scheduled function", e);
              }
            }).then(function () {
              //pointer changes only if repeat was called and there's no need to schedule next run this time
              if (!stopped && currentPointer === pointer) {
                pointer = setTimeout(runner, interval);
              }
            });
          };

        runner();

        return {
          stop: function () {
            stopped = true;
            clearTimeout(pointer);
          },
          forceRun: function () {
            stopped = false;
            return repeat();
          }
        };
      };
    }, { "43": 43 }], 47: [function (require, module, exports) {
      function each(collection, fun) {
        if (collection) {
          if (collection.length === +collection.length) {
            for (var i = 0; i < collection.length; i++) {
              fun.call(null, collection[i], i, collection);
            }
          } else {
            for (var i in collection) {
              if (collection.hasOwnProperty(i)) {
                fun.call(null, collection[i], i, collection);
              }
            }
          }
        }
      }

      function contains(arr, val) {
        var found = false;
        each(arr, function (v) {
          if (v === val) {
            found = true;
          }
        })
        return found;
      }
      var disallowedChars = /[":<>|?*\\]/;

      function normalizeURL(url) {
        return (url).replace(/\/*$/, "");
      };

      function normalizePath(path) {
        return (path).replace(/\/*$/, "") || "/";
      };

      function debounce(func, time) {
        var timer;
        return function () {
          clearTimeout(timer);
          timer = setTimeout(func, time);
        }

      }

      module.exports = {
        //simple extend function
        extend: function extend(target) {
          var i, k;
          for (i = 1; i < arguments.length; i++) {
            if (arguments[i]) {
              for (k in arguments[i]) {
                if (arguments[i].hasOwnProperty(k) && (typeof arguments[i][k] !== "undefined")) {
                  target[k] = arguments[i][k];
                }
              }
            }
          }
          return target;
        },
        noop: function () { },
        id: function (a) {
          return a
        },
        bindThis: function (that, func) {
          return function () {
            return func.apply(that, arguments);
          }
        },
        debounce: debounce,
        contains: contains,
        each: each,
        normalizeURL: normalizeURL,
        normalizePath: normalizePath,
        httpsURL: function (url) {
          return "https://" + (normalizeURL(url).replace(/^https?:\/\//, ""));
        },
        encodeNameSafe: function (name) {
          if (!name) {
            throw new Error("No name given");
          }
          if (disallowedChars.test(name)) {
            throw new Error("Disallowed characters in path");
          }

          name = name.replace(/^\/\//, "/");

          return (name);
        },
        encodeURIPath: function (text) {
          return encodeURI(text).replace(/#/g, "%23");
        }
      };

    }, {}], 48: [function (require, module, exports) {
      var helpers = require(47);


      //returns postMessage specific handler
      function createMessageHandler(sourceOrigin, marker, callback) {
        return function (event) {
          if (!sourceOrigin || helpers.normalizeURL(event.origin) === helpers.normalizeURL(sourceOrigin)) {
            var message = event.data;
            if (message.substr(0, marker.length) === marker) {
              try {
                message = JSON.parse(message.substring(marker.length));

              } catch (e) {
                //broken? ignore
              }
              if (message) {
                callback(message);
              }
            }
          }
        };
      }

      function sendMessage(targetWindow, channel, action, data, originOverride) {
        var targetOrigin = "*",
          pkg;

        if (typeof action !== "string") {
          throw new TypeError("only string is acceptable as action");
        }

        if (originOverride) {
          targetOrigin = originOverride;
        } else {
          try {
            //the if is needed as some browsers will return undefined when accessing location is forbidden
            if (targetWindow.location.origin || targetWindow.location.protocol) {
              targetOrigin = targetWindow.location.origin || targetWindow.location.protocol + "//" + targetWindow.location.hostname + (targetWindow.location.port ? ":" + targetWindow.location.port : "");
            }
          } catch (E) { }
        }
        pkg = JSON.stringify({
          action: action,
          data: data
        });
        pkg = pkg.replace(/(\r\n|\n|\r)/gm, "");
        targetWindow.postMessage(channel.marker + pkg, targetOrigin);
      }

      module.exports = {
        sendMessage: sendMessage,
        createMessageHandler: createMessageHandler
      }
    }, { "47": 47 }], 49: [function (require, module, exports) {
      module.exports = function (overrides) {
        return function (txt) {
          if (overrides) {
            if (overrides[txt]) {
              return overrides[txt];
            } else if (overrides[txt.toLowerCase()]) {
              return overrides[txt.toLowerCase()];
            }
          }
          return txt;
        };
      };

    }, {}], 50: [function (require, module, exports) {
      (function () { var head = document.getElementsByTagName('head')[0]; var style = document.createElement('style'); style.type = 'text/css'; var css = ".eg-picker-back{padding:4px 10px;position:relative;color:#777}.eg-picker-back:hover{color:#4e4e4f}.eg-picker-back:before{content:\"\";display:block;left:4px;border-style:solid;border-width:0 0 3px 3px;transform:rotate(45deg);-ms-transform:rotate(45deg);-moz-transform:rotate(45deg);-webkit-transform:rotate(45deg);width:7px;height:7px;padding:0;position:absolute;bottom:10px}.eg-btn.eg-search-x{margin:1px;text-decoration:none !important;position:absolute;color:#777;font-size:36px;line-height:16px;transform:rotate(45deg);-ms-transform:rotate(45deg);-moz-transform:rotate(45deg);-webkit-transform:rotate(45deg);border-style:solid}.eg-btn.eg-search-x:hover{color:#4e4e4f}.eg-search-ico:before{display:block;content:\"\";width:7.2px;height:7.2px;border-width:3px;border-style:solid;background:transparent;-webkit-border-radius:50%;-moz-border-radius:50%;border-radius:50%}.eg-search-ico:after{content:\"\";position:absolute;top:14.4px;left:14.4px;border-left-width:3px;border-left-style:solid;height:5.76px;margin:0;-webkit-transform:rotate(-45deg);-moz-transform:rotate(-45deg);-ms-transform:rotate(-45deg);-o-transform:rotate(-45deg);transform:rotate(-45deg)}@-webkit-keyframes egspin{to{-webkit-transform:rotate(360deg);transform:rotate(360deg)}}@keyframes egspin{to{transform:rotate(360deg)}}.eg-placeholder{position:absolute;width:100%;top:50%;bottom:50%;margin-top:-34px;text-align:center;color:#777}.eg-placeholder>div{margin:0 auto 5px}.eg-placeholder>.eg-spinner{content:\"\";-webkit-animation:egspin 1s infinite linear;animation:egspin 1s infinite linear;width:30px;height:30px;border:solid 7px;border-radius:50%;border-color:transparent transparent #dbdbdb}.eg-picker-error:before{content:\"?!\";font-size:32px;border:2px solid #5e5f60;padding:0 10px}.eg-ico{margin-right:10px;position:relative;top:-2px}.eg-mime-audio{background:#94cbff}.eg-mime-video{background:#8f6bd1}.eg-mime-pdf{background:#e64e40}.eg-mime-word_processing{background:#4ca0e6}.eg-mime-spreadsheet{background:#6bd17f}.eg-mime-presentation{background:#fa8639}.eg-mime-cad{background:#f2d725}.eg-mime-text{background:#9e9e9e}.eg-mime-image{background:#d16bd0}.eg-mime-code{background:#a5d16b}.eg-mime-archive{background:#d19b6b}.eg-mime-goog{background:#0266c8}.eg-mime-unknown{background:#dbdbdb}.eg-file .eg-ico{width:40px;height:40px;text-align:right}.eg-file .eg-ico>span{text-align:center;font-size:13.33333333px;line-height:18px;font-weight:300;margin:10px 0;height:20px;width:32px;background:rgba(0,0,0,0.15);color:#fff}.eg-folder .eg-ico{background:#fee999;border-top:4.8px #f1dc8e solid;margin-top:8.8px;height:24.6px;overflow:visible;border-radius:2px;width:38px}.eg-folder .eg-ico:before{display:block;position:absolute;top:-7.2px;border-radius:3px;background:#f1dc8e;content:\" \";width:16px;height:6px}.eg-folder .eg-ico>span{display:none}.eg-btn{display:inline-block;line-height:20px;height:20px;text-align:center;cursor:pointer;margin:0 8px}span.eg-btn{padding:4px 15px;background:#fafafa;border:1px solid #ccc;border-radius:2px}span.eg-btn:hover{-webkit-box-shadow:inset 0 -20px 50px -60px #000;box-shadow:inset 0 -20px 50px -60px #000}span.eg-btn:active{-webkit-box-shadow:inset 0 1px 5px -4px #000;box-shadow:inset 0 1px 5px -4px #000}span.eg-btn[disabled]{opacity:.3}a.eg-btn{font-weight:600;padding:4px;border:1px solid transparent;text-decoration:underline}.eg-btn.eg-btn-prim{background:#3191f2;border-color:#2b82d9;color:#fff}.eg-box,.eg-widget,.eg-bar{-moz-box-sizing:border-box;-webkit-box-sizing:border-box;box-sizing:border-box;position:relative;overflow:hidden}.eg-in *{-webkit-user-select:none;-moz-user-select:none;-ms-user-select:none;user-select:none;vertical-align:middle}.eg-widget{background:#fff;border:1px solid #dbdbdb;padding:0;color:#5e5f60;font-size:12px;font-family:'Open Sans',sans-serif}.eg-widget input{padding:0}.eg-widget a{cursor:pointer}.eg-widget a:hover{text-decoration:underline}.eg-widget .eg-brand{background:url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgBAMAAACBVGfHAAAAD1BMVEVLmJRkpqN9trS51tP6/fqnSbSVAAAAfklEQVQoka2OwRGAIAwEiTYQZyiABwX4oAHw+q9JEsgojr7kPnA7Se6cmyfiB/D5H6CjgWSHI7IAj9L8AiAQ62MTqEsJNqH/7JV2rVDtt1DxQ5N0X+hJYWwKDLYIiJePEHDVWGtABd5ySQLkRgJ3wA1QB44thb5jX8C2uXk6AXu0F4Px6fa6AAAAAElFTkSuQmCC') no-repeat center;width:50px;height:32px;margin:0;float:left}.eg-bar{z-index:1;height:50px;padding:10px 10px 0;background:#f1f1f1;border:0 solid #dbdbdb;border-width:1px 0 0 0}.eg-bar.eg-top{box-shadow:0 1px 3px 0 #f1f1f1;border-width:0 0 1px 0;padding-left:0;background:#fff}.eg-bar>*{display:block;float:left}.eg-bar-right>*{float:right}.eg-ctlgrp{padding:20px}.eg-ctlgrp>*{width:99%;margin:10px 0}.eg-not{visibility:hidden}.eg-prompt{padding-top:20px}.eg-picker{height:100%;min-height:300px}.eg-picker input[type=checkbox]{margin:10px 20px}.eg-picker a.eg-file:hover{text-decoration:none}.eg-picker ul{padding:0;margin:0;min-height:200px;overflow-y:scroll}.eg-picker-pager{float:right}.eg-bar-right>.eg-picker-pager{float:left}.eg-picker-path{min-width:60%;width:calc( 100% - 110px );line-height:30px;color:#777;font-size:14px}.eg-picker-path>a{margin:0 2px;white-space:nowrap;display:inline-block;overflow:hidden;text-overflow:ellipsis}.eg-picker-path>a:last-child{color:#5e5f60}.eg-picker-item{line-height:40px;list-style:none;padding:4px 0;border-bottom:1px solid #f2f3f3}.eg-picker-item.eg-selected{background:#eef5fd;outline:1px solid #dbdbdb}.eg-picker-item[aria-selected=\"true\"]{background:#d4e7fe}.eg-picker-item *{display:inline-block}.eg-picker-item>a{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:300px;max-width:calc(100% - 88px)}.eg-picker-item [disabled]~a,.eg-picker-item.eg-disabled>a{opacity:.6}.eg-picker-item.eg-disabled>a.eg-file{cursor:default}.eg-picker-name{margin-left:4px}.eg-search{background:#fff;width:50px;position:absolute;top:-1px;right:0;z-index:3;transition:width .5s ease;-webkit-transition:width .5s ease;-moz-transition:width .5s ease}.eg-search *{visibility:hidden}.eg-search[aria-expanded]{width:100%}.eg-search[aria-expanded] *{visibility:visible}.eg-search-inpt{width:calc(100% - 55px);padding-left:40px}.eg-search-inpt input{font-family:'Open Sans',sans-serif;width:100%;padding:5px;border-radius:5px;border:1px solid #2b82d9;outline:none}.eg-btn.eg-search-ico{visibility:visible;position:absolute;right:10px}.eg-btn.eg-search-ico:hover{color:#4e4e4f}.eg-search-no{padding:20px}"; if (style.styleSheet) { style.styleSheet.cssText = css; } else { style.appendChild(document.createTextNode(css)); } head.appendChild(style); }())
    }, {}], 51: [function (require, module, exports) {
      var helpers = require(47);
      var plugins = require(42);
      var defaults = require(10);

      module.exports = {
        init: function init(egnyteDomainURL, opts) {
          var options = helpers.extend({}, defaults, opts);
          options.egnyteDomainURL = egnyteDomainURL ? helpers.normalizeURL(egnyteDomainURL) : null;

          var exporting = {
            domain: options.egnyteDomainURL,
            setDomain: function (d) {
              this.domain = options.egnyteDomainURL = helpers.normalizeURL(d);
            },
            API: require(12)(options)
          }
          plugins.install(exporting);

          return exporting;

        },
        plugin: plugins.define

      }

    }, { "10": 10, "12": 12, "42": 42, "47": 47 }], 52: [function (require, module, exports) {
      var jungle = require(53);
      module.exports = {
        tree: jungle,
        node: function (i) {
          return jungle([i]).childNodes[0];
        }
      }

    }, { "53": 53 }], 53: [function (require, module, exports) {
      /**
       * zenjungle - HTML via JSON with elements of Zen Coding
       *
       * https://github.com/radmen/zenjungle
       * Copyright (c) 2012 Radoslaw Mejer <radmen@gmail.com>
       */

      var zenjungle = (function () {
        // helpers
        var is_object = function (object) {
          return (!!object && '[object Object]' == Object.prototype.toString.call(object) && !object.nodeType);
        },
          is_array = function (object) {
            return '[object Array]' == Object.prototype.toString.call(object);
          },
          each = function (object, callback) {
            var key;
            if (object) {
              if (object.length) {
                for (key = 0; key < object.length; key++) {
                  callback(object[key], key);
                }
              } else {
                for (key in object) {
                  object.hasOwnProperty(key) && callback(object[key], key);
                }
              }
            }
          },
          merge = function () {
            var merged = {}

            each(arguments, function (arg) {
              each(arg, function (value, key) {
                merged[key] = value;
              })
            });

            return merged;
          }

        // converts some patterns to properties
        var zen = function (string) {
          var replace = {
            '\\[([a-z\\-]+)=([^\\]]+)\\]': function (match) {
              var prop = {};
              prop[match[1]] = match[2].replace(/^["']/, '').replace(/["']$/, '');

              return prop;
            },
            '#([a-zA-Z][a-zA-Z0-9\\-_]*)': function (match) {
              return {
                'id': match[1]
              };
            },
            '(\\.[a-zA-Z][a-zA-Z0-9\\-_]*)+': function (match) {
              return {
                'class': match[0].substr(1).split(".").join(" ")
              };
            }
          },
            props = {};

          each(replace, function (parser, regex) {
            var match;

            regex = new RegExp(regex);

            while (regex.test(string)) {
              match = regex.exec(string);
              string = string.replace(match[0], '');

              props = merge(props, parser(match));
            }
          });

          return [string, props];
        }

        var monkeys = function (what, where) {
          where = where || document.createDocumentFragment();

          each(what, function (element) {
            var zenned,
              props,
              new_el;

            if (is_array(element)) {

              if ('string' === typeof element[0]) {
                zenned = zen(element.shift());
                props = is_object(element[0]) ? element.shift() : {};
                new_el = document.createElement(zenned[0]);

                each(merge(zenned[1], props), function (value, key) {
                  new_el.setAttribute(key, value);
                });

                where.appendChild(new_el);
                monkeys(element, new_el);
              } else {
                monkeys(element, where);
              }
            } else if (element.nodeType) {
              where.appendChild(element);
            } else if ('string' === typeof (element) || 'number' === typeof (element)) {
              where.appendChild(document.createTextNode(element));
            }
          });
          return where;
        }
        return monkeys;
      })();

      if (typeof module !== "undefined") {
        module.exports = zenjungle;
      }
    }, {}]
  }, {}, [11])(11)
});