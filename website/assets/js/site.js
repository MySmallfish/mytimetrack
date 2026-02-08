(function () {
  function $(sel, root) {
    return (root || document).querySelector(sel);
  }

  function $all(sel, root) {
    return Array.from((root || document).querySelectorAll(sel));
  }

  function applyConfig() {
    var cfg = window.MYTIMETRACK_CONFIG || {};

    // Text tokens
    $all("[data-app-name]").forEach(function (el) {
      el.textContent = cfg.appName || "MyTimetrack";
    });
    $all("[data-tagline]").forEach(function (el) {
      el.textContent = cfg.tagline || "";
    });
    $all("[data-price-usd]").forEach(function (el) {
      el.textContent = cfg.priceUsd ? "$" + cfg.priceUsd : "$9.99";
    });
    $all("[data-support-email]").forEach(function (el) {
      el.textContent = cfg.supportEmail || "support@mytimetrack.app";
      if (el.tagName.toLowerCase() === "a") {
        el.href = "mailto:" + (cfg.supportEmail || "support@mytimetrack.app");
      }
    });

    // Store links
    var stores = {
      ios: cfg.iosUrl || "",
      android: cfg.androidUrl || "",
    };

    $all("[data-store-link]").forEach(function (a) {
      var store = a.getAttribute("data-store-link");
      var url = stores[store] || "";
      if (!url) {
        a.classList.add("is-disabled");
        a.setAttribute("aria-disabled", "true");
        a.setAttribute("tabindex", "-1");

        var badge = a.querySelector(".btn-badge");
        if (badge) badge.textContent = "Coming soon";
      } else {
        a.href = url;
        a.classList.remove("is-disabled");
        a.removeAttribute("aria-disabled");
        a.removeAttribute("tabindex");
      }
    });
  }

  function setupNav() {
    var toggle = $(".nav-toggle");
    var panel = $("#nav-panel");
    if (!toggle || !panel) return;

    toggle.addEventListener("click", function () {
      var expanded = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", expanded ? "false" : "true");
      panel.classList.toggle("is-open", !expanded);
    });
  }

  function setupReveal() {
    var els = $all(".reveal");
    if (!els.length) return;

    if (!("IntersectionObserver" in window)) {
      els.forEach(function (el) {
        el.classList.add("is-visible");
      });
      return;
    }

    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-visible");
            io.unobserve(entry.target);
          }
        });
      },
      { rootMargin: "0px 0px -10% 0px", threshold: 0.1 },
    );

    els.forEach(function (el) {
      io.observe(el);
    });
  }

  function setupYear() {
    var y = $("#year");
    if (y) y.textContent = String(new Date().getFullYear());
  }

  document.addEventListener("DOMContentLoaded", function () {
    applyConfig();
    setupNav();
    setupReveal();
    setupYear();
  });
})();

