# Open Bus Stride API — verified notes

Base URL: `https://open-bus-stride-api.hasadna.org.il` (no API key). Spec: `/openapi.json`, UI: `/docs`.
All findings below come from real calls made on 2026-10-31 (Saturday evening, Israel time).
Trimmed sample responses live in [`api-samples/`](api-samples/).

## General rules

| Rule | Detail |
|---|---|
| Default limit | 100 rows if `limit` not set |
| Max limit | **15000** per request (`500 "due to abuse, maximum limit per request is 15000 items"`) |
| Unknown params | **Silently ignored** (e.g. `siri_ride_ids` on `/siri_rides/list` returned unrelated rows). Only use params listed in the spec. |
| Date-times | Must include a zone: `2026-10-31T17:29:00+00:00` or `...Z`. A naive time returns `tzinfo is required`. Responses are UTC (`+00:00`). App converts to `Asia/Jerusalem`. |
| Compression | No gzip from server. Keep `limit` small and filter by time/bbox. |
| GTFS dates | GTFS tables are **per date**. Only today and past dates exist (`2026-11-01` count = 0). Early in the morning today's GTFS may not be loaded yet → fall back to yesterday. |

## Live data — `/siri_vehicle_locations/list`

Filters used: `recorded_at_time_from`, `recorded_at_time_to`, `lat__greater_or_equal`, `lat__lower_or_equal`,
`lon__greater_or_equal`, `lon__lower_or_equal`, `siri_routes__line_ref`, `limit`, `order_by`.

Fields used: `recorded_at_time, lat, lon, bearing, velocity (km/h), distance_from_journey_start (m),
siri_route__line_ref, siri_route__operator_ref, siri_ride__id, siri_ride__scheduled_start_time, siri_ride__vehicle_ref`.

Findings:
* SIRI is sampled **once per minute** (`/siri_snapshots/list` shows one snapshot per minute, ~4,700 vehicles each).
* **Lag is ~2–4 minutes** (recorded_at_time vs. wall clock). A 3-minute window often misses buses → the app asks for the **last 6 minutes** and keeps only the newest point per vehicle. Markers older than 3 min fade, older than 6 min are dropped.
* **Bad rows with year 2038 exist.** Always send `recorded_at_time_to = now + 1 min`, otherwise `order_by=recorded_at_time desc` returns garbage first.
* Bounding-box filter works and is fast (Tel Aviv center, 3 min window: 432 rows, 300 KB, 1.4 s).
* `siri_ride__gtfs_ride_id` is **null for live rides** (filled later by ETL). Linking live bus → planned trip is done by
  `line_ref` → today's `gtfs_route.id` → `/gtfs_rides/list?gtfs_route_id=` and matching `start_time == siri_ride__scheduled_start_time`.
  (`journey_ref_prefix` also works but took **34 s** — not usable.)
* `siri_route__line_ref` == `gtfs_routes.line_ref` (stable MOT line id) → used to get line number / operator from the cached routes table.

## Static data (cached in Room, refreshed daily)

### `/gtfs_stops/list?date_from=D&date_to=D`
* 26,351 rows for one day, but **stop `code` repeats** (same stop duplicated with different `id`s, e.g. central stations). App dedupes by `code`.
* Fetched in pages of 10,000 (`limit` + `offset`). 10k rows ≈ 1.4 MB / 3.4 s. `order_by` is ignored here (always id desc), but the order is stable, so offset paging returns all 26,351 rows with no duplicates (checked).

### `/gtfs_routes/list?date_from=D&date_to=D`
* ~3,000–5,200 rows/day, `line_ref` unique per day. One call with `limit=6000`.
* `route_long_name` format: `Origin stop-Origin city<->Destination stop-Destination city-<direction><alternative>`.
* `route_type`: `3` bus, `2` rail, `0` light rail, `715` demand-responsive, `5` cable car.

### `/gtfs_agencies/list` — operator id → Hebrew name (31 agencies). Bundled into the app as a static table with colors.

## Planned trips

### `/gtfs_rides/list?gtfs_route_id=R`
* Returns all rides of that route on that route's date (route ids are per date). Fast (~1 s).
* ⚠️ `start_time_to` actually filters on **end_time** (a ride 17:30–18:19 is returned for `start_time_to=18:25` but not `17:31`). App only uses `start_time_from` or filters locally.

### `/gtfs_ride_stops/list`
* Needs either one `gtfs_ride_ids` value **or** `arrival_time_from` + `arrival_time_to`.
* By ride: `gtfs_ride_ids=<id>&order_by=stop_sequence asc` → full stop list with times + stop lat/lon/name/code + `shape_dist_traveled` (m). ~0.9 s.
* By stop: `gtfs_stop__code=<code>&arrival_time_from=now&arrival_time_to=now+3h` → next departures at a stop for all lines. ~1.4 s. **This is the arrivals query.**
* Several ride ids + time range works but took **17 s** → app does one ride per request, max 4 in parallel.
* Several stop ids in one call: 7.5 s for 22 stops → app does one stop per request instead.

### Not used
* `/stop_arrivals/list`, `/route_timetable/list` — "currently only planned time"; `gtfs_ride_stops` gives the same with more fields.
* No GTFS **shapes** endpoint → route lines on the map are drawn through the stops in order.

## Live estimate (honest + cheap)

For a stop S and a live bus on ride R (matched by line_ref + scheduled start):
`remaining_m = shape_dist_traveled(S) - distance_from_journey_start(bus)`;
`planned_speed = shape_dist_traveled(S) / (planned_arrival(S) - ride_start)`;
`eta = recorded_at_time + remaining_m / planned_speed` (bus velocity used only when moving and plausible).
Shown with a "LIVE · estimate" tag. If `remaining_m < 0` the bus already passed.

## Data saver

`/siri_snapshots/list?limit=3&order_by=id desc` (~1.4 KB) shows the newest SIRI snapshot and its `etl_status`.
The map polls this every 15 s and downloads positions only when a new snapshot is `loaded` or the view/filter changed.

## Nominatim (place search)

`https://nominatim.openstreetmap.org/search?q=..&format=jsonv2&countrycodes=il&limit=5&accept-language=he|en`
— verified. App sends a real `User-Agent`, debounces 600 ms, min 3 chars, max 1 req/s, caches results.

## Official service alerts?

Not found in Stride. The Ministry of Transport SIRI/GTFS-RT feeds need a registration key, so they are **not** used.
