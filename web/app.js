/* BusLive web – live Israeli buses in the browser. Data: Open Bus Stride API (Hasadna). No tracking. */
'use strict';

const API = 'https://open-bus-stride-api.hasadna.org.il';
const STYLE_LIGHT = 'https://tiles.openfreemap.org/styles/liberty';
const STYLE_DARK = 'https://tiles.openfreemap.org/styles/dark';
const WINDOW_MIN = 6, FADE_S = 180, DROP_S = 420, REFRESH_MS = 15000, MIN_BUS_ZOOM = 11, MIN_STOP_ZOOM = 13;

// ---------------------------------------------------------------- i18n
const T = {
  he: {
    searchHint: 'חיפוש קו, תחנה, קוד או מקום', getApp: '📲 אפליקציה', allBuses: 'כל האוטובוסים', offline: 'אין חיבור לאינטרנט',
    dlTitle: 'BusLive לאנדרואיד', dlText: 'חינם, בלי פרסומות ובלי מעקב. מפה חיה, זמני הגעה, תכנון נסיעה, ניווט חי עם התראת ירידה, ווידג׳ט.',
    dlMain: 'הורדת APK (רוב הטלפונים)', dlUniversal: 'APK אוניברסלי (כל טלפון)', dlHow: 'פתחו את הקובץ בטלפון ואשרו "התקנת אפליקציות לא מוכרות".',
    close: 'סגירה', legendTitle: 'מה רואים במפה',
    legendLive: 'סמן צבעוני = מיקום GPS חי של אוטובוס (מספר קו, החץ = כיוון). שקוף = ישן מ-3 דקות.',
    legendEst: 'חי · הערכה = זמן הגעה מוערך לפי אוטובוס חי בדרך. שעה רגילה = לוח זמנים.',
    legendSrc: 'נתונים: Open Bus Stride (הסדנא לידע ציבורי) מתוך SIRI/GTFS של משרד התחבורה. הנתונים החיים מאחרים ב-1–4 דקות. מפה © OpenFreeMap, OpenMapTiles, תורמי OpenStreetMap.',
    lines: 'קווים', stops: 'תחנות', places: 'מקומות', noResults: 'אין תוצאות', liveCount: n => `אוטובוסים בזמן אמת: ${n}`,
    updated: s => `עודכן ${s}`, ago: s => s < 60 ? `לפני ${s} שנ׳` : `לפני ${Math.floor(s / 60)} דק׳`, gps: s => `GPS ${s}`,
    zoomIn: 'התקרבו כדי לראות אוטובוסים', loading: 'טוען…', error: 'שגיאה בטעינה – הקישו לנסות שוב',
    to: d => `אל ${d}`, now: 'עכשיו', min: m => `${m} דק׳`, live: 'חי', estimate: 'חי · הערכה', planned: t => `מתוכנן ${t}`,
    then: t => `אחר כך ${t}`, noArrivals: 'אין אוטובוסים ב-3 השעות הקרובות', code: c => `קוד ${c}`,
    follow: 'מעקב', following: 'במעקב ✓', showLine: 'הצגת הקו', speed: k => `${k} קמ״ש`, stale: 'מיקום ישן',
    nearMe: 'תחנות סביבך', walk: m => `${m} דק׳ הליכה`, noGeo: 'אי אפשר לקבל מיקום. אשרו מיקום בדפדפן.',
    lineBuses: n => `${n} אוטובוסים חיים בקו`, clear: 'ניקוי', directions: 'פתיחה באפליקציה', vehicle: v => `אוטובוס ${v}`,
  },
  en: {
    searchHint: 'Search line, stop, code or place', getApp: '📲 App', allBuses: 'All buses', offline: 'No internet connection',
    dlTitle: 'BusLive for Android', dlText: 'Free, no ads, no tracking. Live map, arrivals, trip planner, live directions with get-off alerts, widget.',
    dlMain: 'Download APK (most phones)', dlUniversal: 'Universal APK (any phone)', dlHow: 'Open the file on your phone and allow "Install unknown apps".',
    close: 'Close', legendTitle: 'What the map shows',
    legendLive: 'Colored marker = live bus GPS (line number, arrow = heading). Faded = older than 3 min.',
    legendEst: 'LIVE · estimate = arrival estimated from a live bus on the way. Plain time = timetable.',
    legendSrc: 'Data: Open Bus Stride (Hasadna) from the Ministry of Transport SIRI/GTFS. Live data is ~1–4 min behind. Map © OpenFreeMap, OpenMapTiles, OpenStreetMap contributors.',
    lines: 'Lines', stops: 'Stops', places: 'Places', noResults: 'No results', liveCount: n => `Live buses: ${n}`,
    updated: s => `updated ${s}`, ago: s => s < 60 ? `${s} sec ago` : `${Math.floor(s / 60)} min ago`, gps: s => `GPS ${s}`,
    zoomIn: 'Zoom in to see live buses', loading: 'Loading…', error: 'Could not load – tap to retry',
    to: d => `To ${d}`, now: 'now', min: m => `${m} min`, live: 'LIVE', estimate: 'LIVE · estimate', planned: t => `Planned ${t}`,
    then: t => `Then ${t}`, noArrivals: 'No buses in the next 3 hours', code: c => `Code ${c}`,
    follow: 'Follow', following: 'Following ✓', showLine: 'Show line', speed: k => `${k} km/h`, stale: 'old position',
    nearMe: 'Stops around you', walk: m => `${m} min walk`, noGeo: 'Can\'t get your location. Allow location in the browser.',
    lineBuses: n => `${n} live buses on this line`, clear: 'Clear', directions: 'Open in app', vehicle: v => `Bus ${v}`,
  },
};
let lang = (() => { try { return localStorage.getItem('lang') } catch { return null } })() ||
  ((navigator.language || 'he').startsWith('he') || (navigator.language || '').startsWith('iw') ? 'he' : 'en');
const t = (k, ...a) => { const v = T[lang][k]; return typeof v === 'function' ? v(...a) : v; };
function applyLang() {
  document.documentElement.lang = lang;
  document.documentElement.dir = lang === 'he' ? 'rtl' : 'ltr';
  document.querySelectorAll('[data-i18n]').forEach(e => { e.textContent = t(e.dataset.i18n); });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(e => { e.placeholder = t(e.dataset.i18nPlaceholder); });
  document.getElementById('langBtn').textContent = lang === 'he' ? 'EN' : 'עב';
}

// ---------------------------------------------------------------- utils
const $ = id => document.getElementById(id);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const apiTime = d => new Date(d).toISOString().replace(/\.\d+Z$/, 'Z');
const hhmm = d => new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Jerusalem', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(d));
const secsAgo = d => Math.max(0, Math.round((Date.now() - new Date(d)) / 1000));
function minsText(d) { const m = Math.floor((new Date(d) - Date.now() + 30000) / 60000); return m <= 0 ? t('now') : m < 60 ? t('min', m) : `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`; }
function dist(a, b, c, d) { const R = 6371000, p1 = a * Math.PI / 180, p2 = c * Math.PI / 180, dp = (c - a) * Math.PI / 180, dl = (d - b) * Math.PI / 180;
  const h = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2; return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)); }
async function api(path, q) {
  const url = API + path + '?' + new URLSearchParams(Object.entries(q).filter(([, v]) => v !== undefined && v !== null));
  const r = await fetch(url);
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return r.json();
}
const OP_COLORS = { 2: '#37474F', 3: '#2E7D32', 4: '#6A1B9A', 5: '#1565C0', 6: '#8D6E63', 7: '#5D4037', 8: '#00838F', 14: '#AD1457', 15: '#0277BD',
  16: '#C62828', 18: '#EF6C00', 20: '#4E342E', 21: '#558B2F', 22: '#00695C', 23: '#0097A7', 24: '#827717', 25: '#7B1FA2', 31: '#283593', 32: '#3949AB',
  33: '#00796B', 34: '#9E9D24', 35: '#D84315', 37: '#F9A825', 38: '#F57F17', 135: '#1B5E20' };
const opColor = ref => OP_COLORS[ref] || '#455A64';
const badge = (n, op) => `<span class="badge" style="background:${opColor(op)}">${esc(n)}</span>`;

// ---------------------------------------------------------------- static data (built daily by CI)
let STOPS = [], STOP = new Map(), LINES = {};
async function loadStatic() {
  const [s, l] = await Promise.all([fetch('data/stops.json').then(r => r.json()), fetch('data/lines.json').then(r => r.json())]);
  STOPS = s.map(([code, name, city, lat, lon]) => ({ code, name, city, lat, lon }));
  STOPS.forEach(x => STOP.set(x.code, x));
  LINES = l; // lineRef → [short, op, dest, agency, mkt, dir, routeId, type]
}
const line = ref => { const v = LINES[ref]; return v && { ref: Number(ref), short: v[0], op: v[1], dest: v[2], agency: v[3], mkt: v[4], dir: v[5], routeId: v[6] }; };
const siblings = l => Object.keys(LINES).map(line).filter(x => x.op === l.op && x.mkt === l.mkt && (x.mkt || x.short === l.short));

// ---------------------------------------------------------------- live data
function toBuses(rows) {
  const by = new Map();
  for (const r of rows) {
    if (!r.lat || !r.lon || !r.recorded_at_time) continue;
    const id = r.siri_ride__vehicle_ref || 'r' + r.siri_ride__id;
    const prev = by.get(id);
    if (!prev || r.recorded_at_time > prev.recorded_at_time) by.set(id, r);
  }
  // Hide buses that stopped reporting, measured from the newest loaded snapshot (the feed itself can lag).
  const cut = Math.min(Date.now(), snapTime || Date.now()) - DROP_S * 1000;
  return [...by.values()].filter(r => new Date(r.recorded_at_time) >= cut).map(r => ({
    id: r.siri_ride__vehicle_ref || 'r' + r.siri_ride__id, lineRef: r.siri_route__line_ref, op: r.siri_route__operator_ref,
    lat: r.lat, lon: r.lon, bearing: r.bearing || 0, speed: r.velocity || 0, at: r.recorded_at_time,
    start: r.siri_ride__scheduled_start_time, distFromStart: r.distance_from_journey_start,
    // "2026-11-01-40514797" → "40514797" (same number as GTFS journey_ref "40514797_011126")
    trip: (r.siri_ride__journey_ref || '').split('-').pop() || null,
  }));
}
// The Stride pipeline sometimes lags several minutes: anchor the window to the newest loaded snapshot.
let snapTime = null;
const window6 = () => {
  const ref = Math.min(Date.now(), snapTime || Date.now());
  return { recorded_at_time_from: apiTime(ref - WINDOW_MIN * 60000), recorded_at_time_to: apiTime(Date.now() + 60000) };
};
const busesInBox = b => api('/siri_vehicle_locations/list', { ...window6(), lat__greater_or_equal: b.s, lat__lower_or_equal: b.n, lon__greater_or_equal: b.w, lon__lower_or_equal: b.e, limit: 1500 }).then(toBuses);
const busesForLine = ref => api('/siri_vehicle_locations/list', { ...window6(), siri_routes__line_ref: ref, limit: 500 }).then(toBuses);
const latestSnapshot = () => api('/siri_snapshots/list', { limit: 5, order_by: 'id desc' }).then(a => {
  const s = a.find(x => x.etl_status === 'loaded');
  if (!s) return null;
  const [y, mo, d, h, mi] = s.snapshot_id.split('/').map(Number);
  snapTime = Date.UTC(y, mo - 1, d, h, mi) + 60000;
  return s.id;
}).catch(() => null);

/** Live ETA: remaining distance along the route / (planned speed mixed with reported speed). */
function estimate(a, bus) {
  const stopDist = a.shape_dist_traveled ?? (a.stop_sequence === 1 ? 0 : null);
  if (stopDist == null || bus.distFromStart == null) return null;
  const remaining = stopDist - bus.distFromStart;
  if (remaining < -150) return null;
  const planned = new Date(a.arrival_time), rec = new Date(bus.at);
  // Ride start is sometimes missing in the API; fall back to the bus's scheduled start, then to a city-bus speed.
  const startS = a.gtfs_ride__start_time || bus.start;
  const start = startS ? new Date(startS) : null;
  const travel = start ? (planned - start) / 1000 : 0;
  let eta;
  if (!start) {
    const rep = bus.speed / 3.6, speed = Math.min(25, Math.max(2, bus.speed >= 8 && bus.speed <= 90 ? 0.6 * 5 + 0.4 * rep : 5));
    eta = new Date(+rec + Math.max(0, remaining) / speed * 1000);
  } else if (stopDist <= 0 || travel <= 0 || bus.distFromStart < 30) eta = new Date(Math.max(start, rec) + Math.max(0, travel) * 1000);
  else {
    const planSpeed = stopDist / travel, rep = bus.speed / 3.6;
    const speed = Math.min(25, Math.max(2, bus.speed >= 8 && bus.speed <= 90 ? 0.7 * planSpeed + 0.3 * rep : planSpeed));
    eta = new Date(+rec + Math.max(0, remaining) / speed * 1000);
  }
  return { eta: new Date(Math.max(eta, Date.now())), m: Math.max(0, remaining) };
}

// ---------------------------------------------------------------- map
const dark = matchMedia('(prefers-color-scheme: dark)').matches;
const saved = (() => { try { return JSON.parse(localStorage.getItem('cam')) } catch { return null } })();
const map = new maplibregl.Map({
  container: 'map', style: dark ? STYLE_DARK : STYLE_LIGHT,
  center: saved ? [saved.lon, saved.lat] : [34.7806, 32.0809], zoom: saved ? saved.z : 14, attributionControl: { compact: true },
});
map.addControl(new maplibregl.NavigationControl({ showZoom: false }), 'top-right');
map.dragRotate.disable(); map.touchZoomRotate.disableRotation();

function busImage() {
  const s = 88, c = document.createElement('canvas'); c.width = c.height = s;
  const g = c.getContext('2d'); g.fillStyle = '#fff';
  g.beginPath(); g.arc(s / 2, s / 2, 28, 0, Math.PI * 2); g.fill();
  g.beginPath(); g.moveTo(s / 2, 4); g.lineTo(s / 2 - 16, s / 2 - 16); g.lineTo(s / 2 + 16, s / 2 - 16); g.closePath(); g.fill();
  return { width: s, height: s, data: new Uint8Array(g.getImageData(0, 0, s, s).data.buffer) };
}
const fc = features => ({ type: 'FeatureCollection', features });
const pt = (lon, lat, p) => ({ type: 'Feature', geometry: { type: 'Point', coordinates: [lon, lat] }, properties: p || {} });

map.once('load', () => {
  map.addImage('buslive-bus', busImage(), { sdf: true, pixelRatio: 2 });
  const src = (id, o = {}) => map.addSource(id, { type: 'geojson', data: fc([]), ...o });
  src('route'); src('routeStops'); src('user'); src('pin'); src('buses');
  src('stops', { cluster: true, clusterMaxZoom: 15, clusterRadius: 42 });
  map.addLayer({ id: 'route', type: 'line', source: 'route', paint: { 'line-color': ['get', 'color'], 'line-width': 6, 'line-opacity': .85 }, layout: { 'line-cap': 'round', 'line-join': 'round' } });
  map.addLayer({ id: 'routeStops', type: 'circle', source: 'routeStops', paint: { 'circle-radius': 5, 'circle-color': dark ? '#1b2121' : '#fff', 'circle-stroke-width': 2.5, 'circle-stroke-color': ['get', 'color'] } });
  map.addLayer({ id: 'clusters', type: 'circle', source: 'stops', minzoom: MIN_STOP_ZOOM, filter: ['has', 'point_count'],
    paint: { 'circle-color': dark ? '#324B4D' : '#CCE8E9', 'circle-radius': ['step', ['get', 'point_count'], 11, 10, 14, 40, 18], 'circle-stroke-width': 1.5, 'circle-stroke-color': dark ? '#80D4DB' : '#00696F' } });
  map.addLayer({ id: 'clusterCount', type: 'symbol', source: 'stops', minzoom: MIN_STOP_ZOOM, filter: ['has', 'point_count'],
    layout: { 'text-field': ['to-string', ['get', 'point_count']], 'text-font': ['Noto Sans Bold'], 'text-size': 11, 'text-allow-overlap': true } });
  map.addLayer({ id: 'stops', type: 'circle', source: 'stops', minzoom: MIN_STOP_ZOOM, filter: ['!', ['has', 'point_count']],
    paint: { 'circle-radius': ['interpolate', ['linear'], ['zoom'], 13, 3.5, 17, 7], 'circle-color': dark ? '#1b2121' : '#fff', 'circle-stroke-width': 2, 'circle-stroke-color': dark ? '#80D4DB' : '#00696F' } });
  map.addLayer({ id: 'stopNames', type: 'symbol', source: 'stops', minzoom: 16.5, filter: ['!', ['has', 'point_count']],
    layout: { 'text-field': ['get', 'name'], 'text-font': ['Noto Sans Regular'], 'text-size': 11, 'text-offset': [0, 1.2], 'text-anchor': 'top' },
    paint: { 'text-color': dark ? '#e0e3e3' : '#191c1c', 'text-halo-color': dark ? '#0f1415' : '#fff', 'text-halo-width': 1.5 } });
  map.addLayer({ id: 'user', type: 'circle', source: 'user', paint: { 'circle-radius': 7, 'circle-color': '#1A73E8', 'circle-stroke-width': 2.5, 'circle-stroke-color': '#fff' } });
  map.addLayer({ id: 'pin', type: 'circle', source: 'pin', paint: { 'circle-radius': 9, 'circle-color': '#D32F2F', 'circle-stroke-width': 3, 'circle-stroke-color': '#fff' } });
  map.addLayer({ id: 'busSel', type: 'circle', source: 'buses', filter: ['==', ['get', 'id'], ''], paint: { 'circle-radius': 22, 'circle-color': 'rgba(0,0,0,0)', 'circle-stroke-width': 3.5, 'circle-stroke-color': '#E65100' } });
  map.addLayer({ id: 'buses', type: 'symbol', source: 'buses',
    layout: { 'icon-image': 'buslive-bus', 'icon-rotate': ['get', 'bearing'], 'icon-rotation-alignment': 'map', 'icon-allow-overlap': true, 'icon-ignore-placement': true,
      'icon-size': ['interpolate', ['linear'], ['zoom'], 9, .5, 13, .8, 16, 1], 'text-field': ['get', 'line'], 'text-font': ['Noto Sans Bold'],
      'text-size': ['interpolate', ['linear'], ['zoom'], 9, 8, 13, 11, 16, 13], 'text-allow-overlap': true, 'text-ignore-placement': true, 'text-rotation-alignment': 'viewport' },
    paint: { 'icon-color': ['to-color', ['get', 'color']], 'icon-opacity': ['get', 'op'], 'text-color': '#fff', 'text-opacity': ['get', 'op'] } });

  map.on('click', 'buses', e => { e.preventDefault(); openBus(e.features[0].properties.id); });
  map.on('click', 'stops', e => { if (e.defaultPrevented) return; e.preventDefault(); openStop(e.features[0].properties.code); });
  map.on('click', 'routeStops', e => { if (e.defaultPrevented) return; e.preventDefault(); openStop(e.features[0].properties.code); });
  map.on('click', 'clusters', e => { if (e.defaultPrevented) return; e.preventDefault(); map.easeTo({ center: e.lngLat, zoom: map.getZoom() + 2 }); });
  for (const l of ['buses', 'stops', 'clusters', 'routeStops']) {
    map.on('mouseenter', l, () => map.getCanvas().style.cursor = 'pointer');
    map.on('mouseleave', l, () => map.getCanvas().style.cursor = '');
  }
  map.on('click', e => { if (!e.defaultPrevented) closePanel(); });
  map.on('dragstart', () => { follow = null; });
  map.on('moveend', () => {
    const c = map.getCenter();
    try { localStorage.setItem('cam', JSON.stringify({ lat: c.lat, lon: c.lng, z: map.getZoom() })); } catch { }
    requestRefresh();
  });
  loadStatic().then(() => {
    map.getSource('stops').setData(fc(STOPS.map(s => pt(s.lon, s.lat, { code: s.code, name: s.name }))));
    ready = true;
    refresh(true);
  });
  if (!saved) locate(false);
});

// ---------------------------------------------------------------- buses on the map (smooth)
let buses = [], shown = new Map(), anim = null, follow = null, selectedBus = null, lineFilter = null;
let ready = false;
let lastUpdated = null, lastSnap = null, lastKey = null, fetchedBox = null, loadingBuses = false, busError = false, zoomTooLow = false;

function render(pos) {
  map.getSource('buses')?.setData(fc(buses.map(b => {
    const p = pos.get(b.id); if (!p) return null;
    const l = line(b.lineRef);
    // Fade buses that stopped reporting compared with the rest of the feed (the feed itself may lag).
    const ref = Math.min(Date.now(), snapTime || Date.now());
    return pt(p.lon, p.lat, { id: b.id, line: l ? l.short : '?', bearing: p.bearing, color: opColor(b.op), op: (ref - new Date(b.at)) / 1000 > FADE_S ? .55 : 1 });
  }).filter(Boolean)));
}
function setBuses(list) {
  buses = list;
  const target = new Map(list.map(b => [b.id, { lat: b.lat, lon: b.lon, bearing: b.bearing }]));
  const from = new Map(shown);
  cancelAnimationFrame(anim);
  const t0 = performance.now(), D = 1200;
  const step = now => {
    const k = Math.min(1, (now - t0) / D), e = k < .5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
    const frame = new Map();
    for (const [id, tg] of target) {
      const s = from.get(id);
      if (!s) { frame.set(id, tg); continue; }
      let d = ((tg.bearing - s.bearing) % 360 + 540) % 360 - 180;
      frame.set(id, { lat: s.lat + (tg.lat - s.lat) * e, lon: s.lon + (tg.lon - s.lon) * e, bearing: (s.bearing + d * e + 360) % 360 });
    }
    shown = frame; render(frame);
    if (k < 1) anim = requestAnimationFrame(step);
  };
  anim = requestAnimationFrame(step);
  if (follow) { const b = list.find(x => x.id === follow); if (b) map.easeTo({ center: [b.lon, b.lat], duration: D }); }
  if (selectedBus && $('panel').dataset.kind === 'bus') renderBus(selectedBus);
}

let timer = null, pending = false;
function requestRefresh() { if (!pending) { pending = true; setTimeout(() => { pending = false; refresh(); }, 400); } }
async function refresh(force = false) {
  clearTimeout(timer);
  timer = setTimeout(() => refresh(), REFRESH_MS);
  if (document.hidden || !ready) return;
  const z = map.getZoom(), b = map.getBounds();
  const view = { s: b.getSouth(), w: b.getWest(), n: b.getNorth(), e: b.getEast() };
  const bboxMode = !lineFilter && z >= MIN_BUS_ZOOM;
  zoomTooLow = !lineFilter && z < MIN_BUS_ZOOM;
  if (zoomTooLow) { setBuses([]); status(); return; }
  const covered = !bboxMode || (fetchedBox && view.s >= fetchedBox.s && view.n <= fetchedBox.n && view.w >= fetchedBox.w && view.e <= fetchedBox.e);
  const key = lineFilter ? lineFilter.join(',') : 'box';
  const snap = await latestSnapshot();
  // Data saver: SIRI changes once a minute; skip the big download if nothing changed.
  if (!force && covered && key === lastKey && snap && snap === lastSnap && !busError) { lastUpdated = Date.now(); status(); return; }
  loadingBuses = true; status();
  try {
    let list;
    if (lineFilter) list = (await Promise.all(lineFilter.slice(0, 4).map(busesForLine))).flat();
    else {
      const dx = (view.e - view.w) * .15, dy = (view.n - view.s) * .15;
      fetchedBox = { s: view.s - dy, n: view.n + dy, w: view.w - dx, e: view.e + dx };
      list = await busesInBox(fetchedBox);
    }
    lastSnap = snap; lastKey = key; busError = false; lastUpdated = Date.now();
    setBuses(list);
  } catch { busError = true; }
  loadingBuses = false; status();
}
function status() {
  const el = $('status');
  if (busError) el.textContent = t('error');
  else if (zoomTooLow) el.textContent = t('zoomIn');
  else if (!lastUpdated) el.textContent = t('loading');
  else {
    const newest = buses.reduce((m, b) => b.at > m ? b.at : m, '');
    el.textContent = `${loadingBuses ? '⟳ ' : ''}${t('liveCount', buses.length)} · ${t('updated', t('ago', secsAgo(lastUpdated)))}` + (newest ? ` · ${t('gps', t('ago', secsAgo(newest)))}` : '');
  }
}
setInterval(status, 1000);
$('status').onclick = () => refresh(true);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });

// ---------------------------------------------------------------- panel
function openPanel(kind, html) { const p = $('panel'); p.dataset.kind = kind; $('panelBody').innerHTML = html; p.hidden = false; }
function closePanel() {
  $('panel').hidden = true; selectedBus = null; follow = null;
  if (map.getLayer('busSel')) map.setFilter('busSel', ['==', ['get', 'id'], '']);
}
$('panelClose').onclick = closePanel;
document.addEventListener('keydown', e => { if (e.key === 'Escape') closePanel(); });

// ---------------------------------------------------------------- bus panel
function openBus(id) {
  selectedBus = id;
  map.setFilter('busSel', ['==', ['get', 'id'], id]);
  renderBus(id);
}
function renderBus(id) {
  const b = buses.find(x => x.id === id);
  if (!b) return openPanel('bus', `<div class="ph"><h3>—</h3></div>`);
  const l = line(b.lineRef), age = secsAgo(b.at);
  openPanel('bus', `
    <div class="ph"><div style="display:flex;gap:10px;align-items:center">${badge(l ? l.short : '?', b.op)}
      <div><h3>${esc(l ? t('to', l.dest) : '')}</h3><div class="sub">${esc(l ? l.agency : '')} · ${esc(t('vehicle', b.id))}</div></div></div>
      <div class="sub" style="margin-top:6px;${age > FADE_S ? 'color:var(--warn)' : ''}">${t('gps', t('ago', age))} · ${t('speed', b.speed)}${age > FADE_S ? ' · ' + t('stale') : ''}</div></div>
    <div class="actions">
      <button class="btn primary" id="followBtn">${follow === id ? t('following') : t('follow')}</button>
      ${l ? `<button class="btn" id="busLineBtn">${t('showLine')}</button>` : ''}
    </div>`);
  $('followBtn').onclick = () => { follow = follow === id ? null : id; if (follow) map.easeTo({ center: [b.lon, b.lat], zoom: Math.max(map.getZoom(), 15) }); renderBus(id); };
  if (l) $('busLineBtn').onclick = () => showLine(b.lineRef);
}

// ---------------------------------------------------------------- stop panel
const arrivalsCache = new Map();
async function openStop(code) {
  code = Number(code);
  const s = STOP.get(code);
  if (s) map.easeTo({ center: [s.lon, s.lat], zoom: Math.max(map.getZoom(), 16) });
  const head = `<div class="ph"><h3>${esc(s ? s.name : '#' + code)}</h3><div class="sub">${esc(s ? s.city + ' · ' : '')}${t('code', code)}</div>
    <div class="sub" id="stopUpdated"></div></div>`;
  openPanel('stop', head + `<div class="loading">${t('loading')}</div>`);
  try {
    const now = Date.now();
    const rows = (await api('/gtfs_ride_stops/list', { gtfs_stop__code: code, arrival_time_from: apiTime(now - 120000), arrival_time_to: apiTime(now + 3 * 3600000), limit: 400, order_by: 'arrival_time asc' }))
      .filter(r => r.pickup_type !== 1);
    if (!snapTime) await latestSnapshot();
    const soon = [...new Set(rows.filter(r => new Date(r.arrival_time) < now + 3600000).map(r => r.gtfs_route__line_ref))].slice(0, 8);
    const live = (await Promise.all(soon.map(ref => busesForLine(ref).catch(() => [])))).flat();
    const byTrip = new Map(live.filter(b => b.trip).map(b => [b.trip, b]));
    const byStart = new Map(live.filter(b => b.start).map(b => [b.lineRef + '|' + new Date(b.start).getTime(), b]));
    const groups = new Map();
    for (const r of rows) {
      const trip = (r.gtfs_ride__journey_ref || '').split('_')[0];
      const bus = (trip && byTrip.get(trip)) || (r.gtfs_ride__start_time && byStart.get(r.gtfs_route__line_ref + '|' + new Date(r.gtfs_ride__start_time).getTime()));
      const est = bus ? estimate(r, bus) : null;
      const best = est ? est.eta : new Date(r.arrival_time);
      if (best < now - 60000) continue;
      const g = groups.get(r.gtfs_route__line_ref) || [];
      g.push({ r, est, best }); groups.set(r.gtfs_route__line_ref, g);
    }
    const sorted = [...groups.entries()].map(([ref, g]) => [ref, g.sort((a, b) => a.best - b.best)]).sort((a, b) => a[1][0].best - b[1][0].best);
    if ($('panel').dataset.kind !== 'stop') return;
    const html = sorted.length ? sorted.map(([ref, g]) => {
      const f = g[0], l = line(ref);
      return `<div class="row" data-line="${ref}">${badge(f.r.gtfs_route__route_short_name, f.r.gtfs_route__operator_ref)}
        <div class="grow"><div class="t">${esc(t('to', l ? l.dest : ''))}</div><div class="sub">${esc(f.r.gtfs_route__agency_name || '')}${g.length > 1 ? ' · ' + t('then', g.slice(1, 4).map(x => hhmm(x.best) + (x.est ? '•' : '')).join(', ')) : ''}</div></div>
        <div><div class="mins ${f.est ? 'live' : ''}">${minsText(f.best)}</div>${f.est ? `<span class="tag">${t('estimate')}</span>` : `<div class="sub">${t('planned', hhmm(f.r.arrival_time))}</div>`}</div></div>`;
    }).join('') : `<div class="loading">${t('noArrivals')}</div>`;
    $('panelBody').innerHTML = head + html;
    $('stopUpdated').textContent = t('updated', t('ago', 0)) + (live.length ? ' · ' + t('liveCount', live.length) : '');
    $('panelBody').querySelectorAll('[data-line]').forEach(el => el.onclick = () => showLine(Number(el.dataset.line)));
  } catch {
    $('panelBody').innerHTML = head + `<div class="loading" id="retry">${t('error')}</div>`;
    $('retry').onclick = () => openStop(code);
  }
}

// ---------------------------------------------------------------- line view
async function showLine(ref) {
  const l = line(ref);
  if (!l) return;
  const sibs = siblings(l);
  lineFilter = (sibs.length ? sibs : [l]).map(x => x.ref);
  const chip = $('lineChip');
  chip.hidden = false; chip.classList.add('active');
  chip.innerHTML = `${esc(l.short)} → ${esc(l.dest)} ✕`;
  document.querySelector('[data-filter=all]').classList.remove('active');
  refresh(true);
  try {
    const rides = await api('/gtfs_rides/list', { gtfs_route_id: l.routeId, limit: 600, order_by: 'start_time asc' });
    const now = Date.now();
    const ride = rides.find(r => new Date(r.end_time || r.start_time) > now) || rides[rides.length - 1];
    if (!ride) return;
    const stops = await api('/gtfs_ride_stops/list', { gtfs_ride_ids: ride.id, limit: 400, order_by: 'stop_sequence asc' });
    const color = opColor(l.op);
    const coords = stops.filter(s => s.gtfs_stop__lat).map(s => [s.gtfs_stop__lon, s.gtfs_stop__lat]);
    map.getSource('route').setData(fc(coords.length > 1 ? [{ type: 'Feature', geometry: { type: 'LineString', coordinates: coords }, properties: { color } }] : []));
    map.getSource('routeStops').setData(fc(stops.filter(s => s.gtfs_stop__lat).map(s => pt(s.gtfs_stop__lon, s.gtfs_stop__lat, { code: s.gtfs_stop__code, color }))));
    if (coords.length > 1) {
      const bb = coords.reduce((b, c) => b.extend(c), new maplibregl.LngLatBounds(coords[0], coords[0]));
      map.fitBounds(bb, { padding: 60, maxZoom: 15 });
    }
  } catch { /* route drawing is best effort */ }
}
function clearLine() {
  lineFilter = null;
  $('lineChip').hidden = true;
  document.querySelector('[data-filter=all]').classList.add('active');
  map.getSource('route').setData(fc([])); map.getSource('routeStops').setData(fc([]));
  refresh(true);
}
$('lineChip').onclick = clearLine;
document.querySelector('[data-filter=all]').onclick = clearLine;

// ---------------------------------------------------------------- search
let searchTimer = null, placeTimer = null, lastPlaceQ = '', lastPlaceAt = 0;
$('search').addEventListener('input', e => {
  clearTimeout(searchTimer); clearTimeout(placeTimer);
  const q = e.target.value.trim();
  if (!q) { $('results').hidden = true; return; }
  searchTimer = setTimeout(() => localSearch(q), 150);
  if (q.length >= 3 && !/^\d+$/.test(q)) placeTimer = setTimeout(() => placeSearch(q), 600);
});
let localHtml = '', placeHtml = '';
function drawResults() {
  const r = $('results');
  r.innerHTML = (localHtml + placeHtml) || `<div class="loading">${t('noResults')}</div>`;
  r.hidden = false;
  r.querySelectorAll('[data-line]').forEach(el => el.onclick = () => { r.hidden = true; showLine(Number(el.dataset.line)); });
  r.querySelectorAll('[data-stop]').forEach(el => el.onclick = () => { r.hidden = true; openStop(Number(el.dataset.stop)); });
  r.querySelectorAll('[data-lat]').forEach(el => el.onclick = () => {
    r.hidden = true; const lat = +el.dataset.lat, lon = +el.dataset.lon;
    map.getSource('pin').setData(fc([pt(lon, lat)])); map.flyTo({ center: [lon, lat], zoom: 16 });
  });
}
function localSearch(q) {
  const ql = q.toLowerCase();
  const groups = new Map();
  for (const ref of Object.keys(LINES)) {
    const l = line(ref);
    if (l.short === q || l.short.startsWith(q)) {
      const k = l.op + ':' + (l.mkt || l.short);
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k).push(l);
    }
  }
  const lines = [...groups.values()].sort((a, b) => (a[0].short !== q) - (b[0].short !== q) || a[0].short.length - b[0].short.length).slice(0, 10);
  const isNum = /^\d+$/.test(q);
  const stops = STOPS.filter(s => (isNum && String(s.code).startsWith(q)) || (q.length >= 2 && (s.name.toLowerCase().includes(ql) || s.city.startsWith(q))))
    .sort((a, b) => (!a.name.startsWith(q)) - (!b.name.startsWith(q)) || a.name.length - b.name.length).slice(0, 15);
  localHtml = (lines.length ? `<h4>${t('lines')}</h4>` + lines.map(g => `<div class="row" data-line="${g[0].ref}">${badge(g[0].short, g[0].op)}
      <div class="grow"><div class="t">${esc([...new Set(g.map(x => x.dest))].slice(0, 2).join(' ⇄ '))}</div><div class="sub">${esc(g[0].agency)}</div></div></div>`).join('') : '') +
    (stops.length ? `<h4>${t('stops')}</h4>` + stops.map(s => `<div class="row" data-stop="${s.code}"><span>🚏</span>
      <div class="grow"><div class="t">${esc(s.name)}</div><div class="sub">${esc(s.city)} · ${t('code', s.code)}</div></div></div>`).join('') : '');
  if (q !== lastPlaceQ) placeHtml = '';
  drawResults();
}
async function placeSearch(q) {
  // Nominatim policy: max 1 request per second, debounced, cached by the browser.
  const wait = 1100 - (Date.now() - lastPlaceAt);
  if (wait > 0) await new Promise(r => setTimeout(r, wait));
  lastPlaceAt = Date.now(); lastPlaceQ = q;
  try {
    const res = await fetch('https://nominatim.openstreetmap.org/search?' + new URLSearchParams({ q, format: 'jsonv2', countrycodes: 'il', limit: 6, 'accept-language': lang === 'he' ? 'he,en' : 'en,he' })).then(r => r.json());
    if ($('search').value.trim() !== q) return;
    placeHtml = res.length ? `<h4>${t('places')}</h4>` + res.map(p => `<div class="row" data-lat="${p.lat}" data-lon="${p.lon}"><span>📍</span>
      <div class="grow"><div class="t">${esc(p.name || p.display_name.split(',')[0])}</div><div class="sub">${esc(p.display_name.split(',').slice(1, 4).join(','))}</div></div></div>`).join('') : '';
    drawResults();
  } catch { /* places are optional */ }
}
document.addEventListener('click', e => { if (!$('top').contains(e.target)) $('results').hidden = true; });

// ---------------------------------------------------------------- near me
function locate(showList) {
  if (!navigator.geolocation) return showList && openPanel('near', `<div class="loading">${t('noGeo')}</div>`);
  navigator.geolocation.getCurrentPosition(pos => {
    const { latitude: lat, longitude: lon } = pos.coords;
    map.getSource('user')?.setData(fc([pt(lon, lat)]));
    map.flyTo({ center: [lon, lat], zoom: 16 });
    if (showList) nearMe(lat, lon);
  }, () => showList && openPanel('near', `<div class="loading">${t('noGeo')}</div>`), { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}
async function nearMe(lat, lon) {
  const near = STOPS.map(s => [s, dist(lat, lon, s.lat, s.lon)]).sort((a, b) => a[1] - b[1]).slice(0, 8).filter(x => x[1] < 2000);
  const rowsHtml = near.map(([s, d]) => `<div class="row" data-stop="${s.code}"><span>🚏</span><div class="grow"><div class="t">${esc(s.name)}</div>
    <div class="sub" id="near-${s.code}">${t('loading')}</div></div><div class="sub">${d < 1000 ? Math.round(d / 10) * 10 + ' m' : (d / 1000).toFixed(1) + ' km'}<br>${t('walk', Math.ceil(d * 1.3 / 1.25 / 60))}</div></div>`).join('');
  openPanel('near', `<div class="ph"><h3>${t('nearMe')}</h3></div>` + rowsHtml);
  $('panelBody').querySelectorAll('[data-stop]').forEach(el => el.onclick = () => openStop(Number(el.dataset.stop)));
  for (const [s] of near.slice(0, 6)) {
    const now = Date.now();
    api('/gtfs_ride_stops/list', { gtfs_stop__code: s.code, arrival_time_from: apiTime(now - 60000), arrival_time_to: apiTime(now + 5400000), limit: 200, order_by: 'arrival_time asc' })
      .then(rows => {
        const first = new Map();
        rows.filter(r => r.pickup_type !== 1).forEach(r => { if (!first.has(r.gtfs_route__line_ref)) first.set(r.gtfs_route__line_ref, r); });
        const el = $('near-' + s.code);
        if (el) el.innerHTML = [...first.values()].slice(0, 6).map(r => `${badge(r.gtfs_route__route_short_name, r.gtfs_route__operator_ref)} ${minsText(r.arrival_time)}`).join(' &nbsp; ') || t('noArrivals');
      }).catch(() => { const el = $('near-' + s.code); if (el) el.textContent = t('error'); });
  }
}
$('nearBtn').onclick = () => locate(true);

// ---------------------------------------------------------------- misc
$('legendBtn').onclick = () => $('legend').showModal();
$('appBtn').onclick = e => { e.preventDefault(); $('download').showModal(); };
$('langBtn').onclick = () => { lang = lang === 'he' ? 'en' : 'he'; try { localStorage.setItem('lang', lang); } catch { } applyLang(); status(); };
const online = () => { $('offline').hidden = navigator.onLine; };
addEventListener('online', online); addEventListener('offline', online);
applyLang(); online();
if (location.hash === '#download') $('download').showModal();
