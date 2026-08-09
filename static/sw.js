// Novara — minimal service worker.
// Its only job right now is to make the site an installable PWA
// (required for Android/Play Store packaging via a Trusted Web Activity).
// It does NOT cache chat data or attachments, so nothing here goes stale
// or shows outdated info while you're using Novara.

const CACHE_NAME = "novara-shell-v1";
const SHELL_ASSETS = [
  "/static/style.css",
  "/static/icon-192.png",
  "/static/icon-512.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS)).catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Network-first for everything — Novara is a live chat app, so we never
// want a cached/stale response for pages or API calls. Only a handful of
// static shell assets fall back to cache if the network request fails.
self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;

  const url = new URL(event.request.url);
  const isShellAsset = SHELL_ASSETS.some((path) => url.pathname === path);

  if (isShellAsset) {
    event.respondWith(
      fetch(event.request)
        .then((res) => {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
          return res;
        })
        .catch(() => caches.match(event.request))
    );
  }
});
