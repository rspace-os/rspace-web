/*! Blob.js v1.1.1
 *
 * native Blob interface support (polyfill)
 *
 * By Travis Clarke, https://travismclarke.com
 * By Eli Grey, http://eligrey.com
 * By Devin Samarin, https://github.com/dsamarin
 *
 * License: MIT (https://github.com/clarketm/Blob.js/blob/master/LICENSE.md)
 *
 */

;(function (root, factory) {
    if (typeof exports === 'object' && typeof exports.nodeName !== 'string') {
        module.exports = root.document ? factory(root, true) : function (w) {
            if (!w.document) {
                throw new Error("blobjs requires a window with a document");
            }
            return factory(w);
        };
    } else {
        factory(root);
    }
}(window || this, function (window, noGlobal) {
        "use strict";

        var is_safari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);

        window.URL = window.URL || window.webkitURL;

        if (window.Blob && window.URL && !is_safari) {
            try {
                new window.Blob;
                if (typeof define === "function" && define.amd) {
                    define("blobjs", [], function () {
                        return window.Blob;
                    });
                }
                return window.Blob;
            } catch (e) {
            }
        }

        // Internally we use a BlobBuilder implementation to base Blob off of
        // in order to support older browsers that only have BlobBuilder
        var BlobBuilder = window.BlobBuilder || window.WebKitBlobBuilder || window.MozBlobBuilder || (function (window) {
                var
                    get_class = function (object) {
                        return Object.prototype.toString.call(object).match(/^\[object\s(.*)\]$/)[1];
                    }
                    , FakeBlobBuilder = function BlobBuilder() {
                        this.data = [];
                    }
                    , FakeBlob = function Blob(data, type, encoding) {
                        this.data = data;
                        this.size = data.length;
                        this.type = type;
                        this.encoding = encoding;
                    }
                    , FBB_proto = FakeBlobBuilder.prototype
                    , FB_proto = FakeBlob.prototype
                    , FileReaderSync = window.FileReaderSync
                    , FileException = function (type) {
                        this.code = this[this.name = type];
                    }
                    , file_ex_codes = (
                        "NOT_FOUND_ERR SECURITY_ERR ABORT_ERR NOT_READABLE_ERR ENCODING_ERR "
                        + "NO_MODIFICATION_ALLOWED_ERR INVALID_STATE_ERR SYNTAX_ERR"
                    ).split(" ")
                    , file_ex_code = file_ex_codes.length
                    , real_URL = window.URL || window.webkitURL || window
                    , real_create_object_URL = real_URL.createObjectURL
                    , real_revoke_object_URL = real_URL.revokeObjectURL
                    , URL = real_URL
                    , btoa = window.btoa
                    , atob = window.atob

                    , ArrayBuffer = window.ArrayBuffer
                    , Uint8Array = window.Uint8Array

                    , origin = /^[\w-]+:\/*\[?[\w\.:-]+\]?(?::[0-9]+)?/
                    ;
                FakeBlob.fake = FB_proto.fake = true;
                while (file_ex_code--) {
                    FileException.prototype[file_ex_codes[file_ex_code]] = file_ex_code + 1;
                }
                // Polyfill URL
                if (!real_URL.createObjectURL) {
                    URL = window.URL = function (uri) {
                        var
                            uri_info = document.createElementNS("http://www.w3.org/1999/xhtml", "a")
                            , uri_origin
                            ;
                        uri_info.href = uri;
                        if (!("origin" in uri_info)) {
                            if (uri_info.protocol.toLowerCase() === "data:") {
                                uri_info.origin = null;
                            } else {
                                uri_origin = uri.match(origin);
                                uri_info.origin = uri_origin && uri_origin[1];
                            }
                        }
                        return uri_info;
                    };
                }
                URL.createObjectURL = function (blob) {
                    var
                        type = blob.type
                        , data_URI_header
                        ;
                    if (type === null) {
                        type = "application/octet-stream";
                    }
                    if (blob instanceof FakeBlob) {
                        data_URI_header = "data:" + type;
                        if (blob.encoding === "base64") {
                            return data_URI_header + ";base64," + blob.data;
                        } else if (blob.encoding === "URI") {
                            return data_URI_header + "," + decodeURIComponent(blob.data);
                        }
                        if (btoa) {
                            return data_URI_header + ";base64," + btoa(blob.data);
                        } else {
                            return data_URI_header + "," + encodeURIComponent(blob.data);
                        }
                    } else if (real_create_object_URL) {
                        return real_create_object_URL.call(real_URL, blob);
                    }
                };
                URL.revokeObjectURL = function (object_URL) {
                    if (object_URL.substring(0, 5) !== "data:" && real_revoke_object_URL) {
                        real_revoke_object_URL.call(real_URL, object_URL);
                    }
                };
                FBB_proto.append = function (data/*, endings*/) {
                    var bb = this.data;
                    // decode data to a binary string
                    if (Uint8Array && (data instanceof ArrayBuffer || data instanceof Uint8Array)) {
                        var
                            str = ""
                            , buf = new Uint8Array(data)
                            , i = 0
                            , buf_len = buf.length
                            ;
                        for (; i < buf_len; i++) {
                            str += String.fromCharCode(buf[i]);
                        }
                        bb.push(str);
                    } else if (get_class(data) === "Blob" || get_class(data) === "File") {
                        if (FileReaderSync) {
                            var fr = new FileReaderSync;
                            bb.push(fr.readAsBinaryString(data));
                        } else {
                            // async FileReader won't work as BlobBuilder is sync
                            throw new FileException("NOT_READABLE_ERR");
                        }
                    } else if (data instanceof FakeBlob) {
                        if (data.encoding === "base64" && atob) {
                            bb.push(atob(data.data));
                        } else if (data.encoding === "URI") {
                            bb.push(decodeURIComponent(data.data));
                        } else if (data.encoding === "raw") {
                            bb.push(data.data);
                        }
                    } else {
                        if (typeof data !== "string") {
                            data += ""; // convert unsupported types to strings
                        }
                        // decode UTF-16 to binary string
                        bb.push(unescape(encodeURIComponent(data)));
                    }
                };
                FBB_proto.getBlob = function (type) {
                    if (!arguments.length) {
                        type = null;
                    }
                    return new FakeBlob(this.data.join(""), type, "raw");
                };
                FBB_proto.toString = function () {
                    return "[object BlobBuilder]";
                };
                FB_proto.slice = function (start, end, type) {
                    var args = arguments.length;
                    if (args < 3) {
                        type = null;
                    }
                    return new FakeBlob(
                        this.data.slice(start, args > 1 ? end : this.data.length)
                        , type
                        , this.encoding
                    );
                };
                FB_proto.toString = function () {
                    return "[object Blob]";
                };
                FB_proto.close = function () {
                    this.size = 0;
                    delete this.data;
                };
                return FakeBlobBuilder;
            }(window));

        var Blob = function (blobParts, options) {
            var type = options ? (options.type || "") : "";
            var builder = new BlobBuilder();
            if (blobParts) {
                for (var i = 0, len = blobParts.length; i < len; i++) {
                    if (Uint8Array && blobParts[i] instanceof Uint8Array) {
                        builder.append(blobParts[i].buffer);
                    }
                    else {
                        builder.append(blobParts[i]);
                    }
                }
            }
            var blob = builder.getBlob(type);
            if (!blob.slice && blob.webkitSlice) {
                blob.slice = blob.webkitSlice;
            }
            return blob;
        };

        var getPrototypeOf = Object.getPrototypeOf || function (object) {
                return object.__proto__;
            };

        Blob.prototype = getPrototypeOf(new window.Blob());

        if (typeof define === "function" && define.amd) {
            define("blobjs", [], function () {
                return Blob;
            });
        }
        if (typeof noGlobal === 'undefined') {
            window.Blob = Blob;
        }

        return Blob;
    }
));