"""Natural Earth の陸地データから、GLOBE 用の正距円筒ビットマップ（既定 128×64）を作る。

使い方（開発時に一度だけ。アプリは通信しない）:
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_land.geojson
    curl -LO https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_lakes.geojson
    python make_world_map.py ne_110m_land.geojson ne_110m_lakes.geojson 128 > WorldMapData.kt

1マスを 4×4 点で調べ、半分以上が陸（湖を除く）なら陸とする。
Natural Earth はパブリックドメイン（https://www.naturalearthdata.com/about/terms-of-use/）。
"""
import json
import sys


def rings_of(path):
    """GeoJSON の (Multi)Polygon から、外周と穴の組のリストを返す。"""
    polys = []
    for f in json.load(open(path, encoding="utf-8"))["features"]:
        g = f["geometry"]
        if g["type"] == "Polygon":
            polys.append(g["coordinates"])
        elif g["type"] == "MultiPolygon":
            polys.extend(g["coordinates"])
    return polys


def bbox(poly):
    xs = [p[0] for p in poly[0]]
    ys = [p[1] for p in poly[0]]
    return min(xs), min(ys), max(xs), max(ys)


def in_ring(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def in_polys(x, y, polys):
    for poly, (x0, y0, x1, y1) in polys:
        if x < x0 or x > x1 or y < y0 or y > y1:
            continue
        if in_ring(x, y, poly[0]) and not any(in_ring(x, y, h) for h in poly[1:]):
            return True
    return False


def main():
    land = [(p, bbox(p)) for p in rings_of(sys.argv[1])]
    lakes = [(p, bbox(p)) for p in rings_of(sys.argv[2])]
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 128
    h = w // 2
    ss = 4
    rows = []
    for r in range(h):
        row = []
        for c in range(w):
            hit = 0
            for sy in range(ss):
                for sx in range(ss):
                    lon = -180 + (c + (sx + 0.5) / ss) * 360 / w
                    lat = 90 - (r + (sy + 0.5) / ss) * 180 / h
                    # 南極大陸はデータの端（-90°）まであるので、最下段も陸になる
                    if in_polys(lon, lat, land) and not in_polys(lon, lat, lakes):
                        hit += 1
            row.append("#" if hit * 2 >= ss * ss else ".")
        rows.append("".join(row))

    print("package com.dotpopart.engine")
    print()
    print("// tools/world-map/make_world_map.py で生成（Natural Earth 1:110m、パブリックドメイン）。手で編集しない。")
    print(f"internal val WORLD_MAP_WIDTH = {w}")
    print(f"internal val WORLD_MAP_HEIGHT = {h}")
    print("internal val WORLD_MAP_ROWS = arrayOf(")
    for row in rows:
        print(f'    "{row}",')
    print(")")


if __name__ == "__main__":
    main()
