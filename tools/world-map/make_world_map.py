"""Natural Earth の陸地データから、GLOBE 用の正距円筒の地図（既定 256×128）を作る。

使い方（開発時に一度だけ。アプリは通信しない）:
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_lakes.geojson
    python make_world_map.py ne_110m_land.geojson ne_110m_lakes.geojson 256 > WorldMapData.kt

1マスごとに、陸（湖を除く）が占める割合を 10 段階で書く（'.' は海、'1'〜'8' は海岸、'#' は全部が陸）。
割合は、1マスを横に 8 本の線で切り、線ごとに陸の区間の長さを正確に足して出す。
小さな島も面積どおりに残るので、GLOBE が回っても島の形がぶれにくい。
Natural Earth はパブリックドメイン（https://www.naturalearthdata.com/about/terms-of-use/）。
"""
import json
import sys

LINES = 8
LEVELS = 9


def rings_of(path):
    """GeoJSON の (Multi)Polygon の、外周と穴をすべて並べたリストを返す。"""
    rings = []
    for f in json.load(open(path, encoding="utf-8"))["features"]:
        g = f["geometry"]
        polys = [g["coordinates"]] if g["type"] == "Polygon" else g["coordinates"]
        for poly in polys:
            rings.extend(poly)
    return rings


def crossings(lat, rings):
    """緯度 lat の横線が輪郭と交わる経度を、小さい順に返す（偶数番目から奇数番目までが内側）。"""
    xs = []
    for ring in rings:
        j = len(ring) - 1
        for i in range(len(ring)):
            (xi, yi), (xj, yj) = ring[i][:2], ring[j][:2]
            if (yi > lat) != (yj > lat):
                xs.append(xi + (lat - yi) * (xj - xi) / (yj - yi))
            j = i
    return sorted(xs)


def spans(xs):
    return [(xs[k], xs[k + 1]) for k in range(0, len(xs) - 1, 2)]


def subtract(a, b):
    """区間の集まり a から b を引く。"""
    out = []
    for lo, hi in a:
        cur = [(lo, hi)]
        for blo, bhi in b:
            nxt = []
            for clo, chi in cur:
                if bhi <= clo or blo >= chi:
                    nxt.append((clo, chi))
                    continue
                if blo > clo:
                    nxt.append((clo, blo))
                if bhi < chi:
                    nxt.append((bhi, chi))
            cur = nxt
        out.extend(cur)
    return out


def main():
    land = rings_of(sys.argv[1])
    lakes = rings_of(sys.argv[2])
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 256
    h = w // 2
    cell = 360 / w
    rows = []
    for r in range(h):
        cover = [0.0] * w
        for s in range(LINES):
            lat = 90 - (r + (s + 0.5) / LINES) * 180 / h
            # 南極大陸はデータの端（-90°）まであり、横線と交わらない区間があるので、最下段の線は全部を陸にする
            if lat < -89.5:
                inside = [(-180.0, 180.0)]
            else:
                inside = subtract(spans(crossings(lat, land)), spans(crossings(lat, lakes)))
            for lo, hi in inside:
                c0 = max(0, int((lo + 180) // cell))
                c1 = min(w - 1, int((hi + 180) // cell))
                for c in range(c0, c1 + 1):
                    a = -180 + c * cell
                    cover[c] += max(0.0, min(hi, a + cell) - max(lo, a)) / cell
        row = []
        for c in range(w):
            k = round(cover[c] / LINES * LEVELS)
            row.append("." if k <= 0 else "#" if k >= LEVELS else str(k))
        rows.append("".join(row))

    # Windows でリダイレクトしても、ほかのソースと同じ UTF-8・LF で書く（既定だと CP932 になる）
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    print("package com.dotpopart.engine")
    print()
    print("// tools/world-map/make_world_map.py で生成（Natural Earth 1:110m、パブリックドメイン）。手で編集しない。")
    print("// 1マスの陸の割合を 10 段階で書く（'.' は海、'1'〜'8' は海岸、'#' は全部が陸）。")
    print(f"internal val WORLD_MAP_WIDTH = {w}")
    print(f"internal val WORLD_MAP_HEIGHT = {h}")
    print(f"internal const val WORLD_MAP_LEVELS = {LEVELS}")
    print("internal val WORLD_MAP_ROWS = arrayOf(")
    for row in rows:
        print(f'    "{row}",')
    print(")")


if __name__ == "__main__":
    main()
