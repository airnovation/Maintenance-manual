const CACHE_NAME = "technician-helper-v11";
const APP_SHELL = [
  "/Maintenance-manual",
  "/Maintenance-manual/.nojekyll",
  "/Maintenance-manual/404.html",
  "/Maintenance-manual/__next.__PAGE__.txt",
  "/Maintenance-manual/__next._full.txt",
  "/Maintenance-manual/__next._head.txt",
  "/Maintenance-manual/__next._index.txt",
  "/Maintenance-manual/__next._tree.txt",
  "/Maintenance-manual/_next/static/9inKhbeJwYlgDoF3eXgl8/_buildManifest.js",
  "/Maintenance-manual/_next/static/9inKhbeJwYlgDoF3eXgl8/_clientMiddlewareManifest.js",
  "/Maintenance-manual/_next/static/9inKhbeJwYlgDoF3eXgl8/_ssgManifest.js",
  "/Maintenance-manual/_next/static/chunks/0cz1d0mv5g_q7.js",
  "/Maintenance-manual/_next/static/chunks/0uc75hcbp5mon.js",
  "/Maintenance-manual/_next/static/chunks/158myu8e_yme3.js",
  "/Maintenance-manual/_next/static/chunks/1jq4o6yq14o4c.js",
  "/Maintenance-manual/_next/static/chunks/2sh8l83givr4f.js",
  "/Maintenance-manual/_next/static/chunks/2tnrpl-82oru9.css",
  "/Maintenance-manual/_next/static/chunks/3rxl-jt3pdxgx.js",
  "/Maintenance-manual/_next/static/chunks/turbopack-0172wb27ie8xp.js",
  "/Maintenance-manual/_not-found.html",
  "/Maintenance-manual/_not-found.txt",
  "/Maintenance-manual/_not-found/__next._full.txt",
  "/Maintenance-manual/_not-found/__next._head.txt",
  "/Maintenance-manual/_not-found/__next._index.txt",
  "/Maintenance-manual/_not-found/__next._not-found.txt",
  "/Maintenance-manual/_not-found/__next._not-found/__PAGE__.txt",
  "/Maintenance-manual/_not-found/__next._tree.txt",
  "/Maintenance-manual/data/README.md",
  "/Maintenance-manual/data/bacharach_h10_pro_instrument_knowledge_pack_v1_0.json",
  "/Maintenance-manual/data/cnd1_meter_test_guide_v1_0.json",
  "/Maintenance-manual/data/cnd1_sectional_door_knowledge_pack_v1_0.json",
  "/Maintenance-manual/data/fault_code_combinations.json",
  "/Maintenance-manual/data/knowledge_pack.json",
  "/Maintenance-manual/data/systems_index.json",
  "/Maintenance-manual/data/ui_copy_zh.json",
  "/Maintenance-manual/icons/icon.svg",
  "/Maintenance-manual/index.html",
  "/Maintenance-manual/index.txt",
  "/Maintenance-manual/manifest.webmanifest",
  "/Maintenance-manual/symbols/cnd1/cnd1_24_12v_voltage_error.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_door_runs_down.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_door_runs_up.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_dw_self_test.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_e07_not_applicable.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_e09_encoder_position_no_change.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_eeprom_e20.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_eeprom_startup.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_end_position_closed.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_end_position_half_open.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_end_position_open.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_external_closed_active.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_external_open_active.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_ga_impulse_active.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_maintenance_counter.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_nothing_active.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_photo_cell_1_interrupted.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_photo_cell_2_interrupted.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_photo_cell_error.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_power_limit.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_run_time.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_safety_contact_edge_error.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_safety_edge_not_fitted.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_safety_edge_status.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_second_safety_edge_wicket.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_ser_door_runs_without_command.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_slack_wicket_switch.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_speed_monitoring.png",
  "/Maintenance-manual/symbols/cnd1/cnd1_stop_sign.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE_NAME)
      .then((cache) => cache.addAll(APP_SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  const networkFirst = request.mode === "navigate" || url.pathname.includes("/data/");

  if (request.method !== "GET" || url.origin !== self.location.origin) {
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached && !networkFirst) return cached;

      return fetch(request, networkFirst ? { cache: "no-store" } : undefined)
        .then((response) => {
          if (!response || response.status !== 200) return response;
          const responseToCache = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, responseToCache));
          return response;
        })
        .catch(() => {
          if (request.mode === "navigate") return cached || caches.match("/");
          return cached || Response.error();
        });
    })
  );
});
