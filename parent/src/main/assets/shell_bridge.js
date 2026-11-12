/*
 * Native glue for the TV's Parent Center page. The page itself stays untouched:
 * this script only supplies what a browser would give it for free.
 *
 * Injected from MainActivity after every page load. It is idempotent.
 */
(function () {
  "use strict";

  if (window.__pytvParentShell) return;
  window.__pytvParentShell = true;

  var bridge = window.ParentShell;
  if (!bridge) return;

  // The page exports JSON with a Blob URL. A WebView cannot download a blob:
  // URL natively, so keep the Blob at creation time and forward its bytes to
  // the activity, which saves it through the system file picker.
  var blobs = new Map();
  var createObjectURL = URL.createObjectURL ? URL.createObjectURL.bind(URL) : null;
  if (createObjectURL) {
    URL.createObjectURL = function (blob) {
      var url = createObjectURL(blob);
      try { blobs.set(url, blob); } catch (_) { /* private mode */ }
      return url;
    };
  }

  function fileNameOf(anchor) {
    var name = (anchor.getAttribute("download") || "").trim();
    name = name.split("/").pop().split("\\").pop().trim();
    return name || "download";
  }

  function isBlobOrData(url) {
    return /^(blob:|data:)/i.test(url || "");
  }

  function send(blob, name) {
    var reader = new FileReader();
    reader.onload = function () {
      var result = String(reader.result || "");
      var comma = result.indexOf(",");
      if (comma < 0) return;
      try {
        bridge.saveDownload(name, blob.type || "application/octet-stream", result.slice(comma + 1));
      } catch (_) { /* the activity is gone */ }
    };
    reader.readAsDataURL(blob);
  }

  function handleDownload(anchor) {
    if (!anchor) return false;
    var href = anchor.href || "";
    if (!isBlobOrData(href)) return false;
    var blob = blobs.get(href);
    if (blob) {
      send(blob, fileNameOf(anchor));
      return true;
    }
    // A blob created before this script ran, or a data: URL.
    fetch(href)
      .then(function (response) { return response.blob(); })
      .then(function (value) { send(value, fileNameOf(anchor)); })
      .catch(function () {});
    return true;
  }

  // Programmatic clicks on a detached anchor do not reach the document in
  // every WebView, so hook the prototype as well.
  var nativeAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handleDownload(this)) return;
    return nativeAnchorClick.apply(this, arguments);
  };

  function sameOrigin(url) {
    try { return new URL(url).origin === location.origin; } catch (_) { return false; }
  }

  document.addEventListener("click", function (event) {
    var target = event.target;
    var anchor = target && target.closest ? target.closest("a[href]") : null;
    if (!anchor) return;
    var targetAttr = (anchor.getAttribute("target") || "").toLowerCase();
    if (targetAttr === "_blank" && /^https?:/i.test(anchor.href) && !sameOrigin(anchor.href)) {
      event.preventDefault();
      try { bridge.openExternal(anchor.href); } catch (_) {}
      return;
    }
    if (anchor.hasAttribute("download") && isBlobOrData(anchor.href)) {
      event.preventDefault();
      handleDownload(anchor);
    }
  }, true);

  // The page opens provider consoles with target="_blank"; keep those in the
  // system browser and never create a second WebView.
  var nativeOpen = window.open;
  window.open = function (url) {
    if (typeof url === "string" && /^https?:/i.test(url) && !sameOrigin(url)) {
      try { bridge.openExternal(url); } catch (_) {}
      return null;
    }
    return nativeOpen ? nativeOpen.apply(window, arguments) : null;
  };

  // navigator.clipboard needs a secure context, and the TV page is plain HTTP.
  // Supply the same method backed by the Android clipboard; the page's own
  // textarea + execCommand fallback remains as the last resort.
  try {
    var clipboard = navigator.clipboard;
    if (!clipboard || typeof clipboard.writeText !== "function") {
      Object.defineProperty(navigator, "clipboard", {
        configurable: true,
        get: function () {
          return {
            writeText: function (text) {
              return new Promise(function (resolve, reject) {
                try {
                  bridge.copyText(String(text));
                  resolve();
                } catch (error) {
                  reject(error);
                }
              });
            }
          };
        }
      });
    } else {
      var nativeWrite = clipboard.writeText.bind(clipboard);
      clipboard.writeText = function (text) {
        try { bridge.copyText(String(text)); } catch (_) {}
        return nativeWrite(text).catch(function () {});
      };
    }
  } catch (_) { /* the page's textarea fallback still works */ }
})();
