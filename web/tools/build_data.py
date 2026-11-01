"""Builds web/data/stops.json and web/data/lines.json from the Open Bus Stride API (run daily in CI).

stops.json: [[code, name, city, lat, lon], ...]   (deduped by public stop code)
lines.json: {lineRef: [shortName, operatorRef, destination, agency, mkt, direction, gtfsRouteId, routeType]}
"""
import datetime, json, os, re, sys, time, urllib.parse, urllib.request
from zoneinfo import ZoneInfo

BASE = "https://open-bus-stride-api.hasadna.org.il"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data")


def get(path, **q):
    url = BASE + path + "?" + urllib.parse.urlencode(q)
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "BusLive-web-build (+https://github.com/markovo1234/buslive)"})
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.load(r)
        except Exception as e:  # noqa: BLE001
            print("retry", attempt, url, e, file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    raise SystemExit("API failed: " + url)


def destination(long_name):
    parts = long_name.split("<->")
    raw = re.sub(r"-[0-9]+[#0-9A-Za-z]*$", "", parts[1] if len(parts) > 1 else long_name)
    i = raw.rfind("-")
    if i <= 0:
        return raw.strip()
    stop, city = raw[:i].strip(), raw[i + 1:].strip()
    return stop if city in stop else f"{stop}, {city}"


def main():
    tz = ZoneInfo("Asia/Jerusalem")
    today = datetime.datetime.now(tz).date()
    day = today.isoformat()
    if get("/gtfs_stops/list", date_from=day, date_to=day, get_count="true") == 0:
        day = (today - datetime.timedelta(days=1)).isoformat()
    stops, seen, offset = [], set(), 0
    while True:
        rows = get("/gtfs_stops/list", date_from=day, date_to=day, limit=10000, offset=offset)
        for s in rows:
            if s.get("lat") and s["code"] not in seen:
                seen.add(s["code"])
                stops.append([s["code"], (s.get("name") or "").strip(), (s.get("city") or "").strip(),
                              round(s["lat"], 6), round(s["lon"], 6)])
        if len(rows) < 10000:
            break
        offset += 10000
    routes = get("/gtfs_routes/list", date_from=day, date_to=day, limit=10000)
    lines = {
        str(r["line_ref"]): [r.get("route_short_name") or "", r["operator_ref"], destination(r.get("route_long_name") or ""),
                             r.get("agency_name") or "", r.get("route_mkt") or "", r.get("route_direction") or "",
                             r["id"], r.get("route_type") or ""]
        for r in routes
    }
    if len(stops) < 1000 or len(lines) < 500:
        raise SystemExit(f"too little data: {len(stops)} stops, {len(lines)} lines")
    os.makedirs(OUT, exist_ok=True)
    json.dump(stops, open(os.path.join(OUT, "stops.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump(lines, open(os.path.join(OUT, "lines.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    json.dump({"date": day, "built": datetime.datetime.now(tz).isoformat(timespec="minutes")},
              open(os.path.join(OUT, "meta.json"), "w"))
    print(f"{day}: {len(stops)} stops, {len(lines)} lines")


if __name__ == "__main__":
    main()
