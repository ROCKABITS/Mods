#!/usr/bin/env python3
"""Original Realm Titles pixel art and Java Edition geometry.

The Royal Seal Desk is a real 1×2×1 structure, with each block model clipped to
its own 0..16 volume. All artwork is drawn at final pixel resolution using Pillow.
Run `python3 tools/generate_assets.py` to reproduce every generated resource.
"""
from copy import deepcopy
from pathlib import Path
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/realmtitles"
TEX = ASSETS / "textures/block"
TEX.mkdir(parents=True, exist_ok=True)
RNG = random.Random(8217601)
GOLD = (180, 138, 66, 255)
LIGHT = (234, 211, 148, 255)
DARK_GOLD = (104, 72, 33, 255)
GREEN = (28, 66, 49, 255)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def color(base, delta=0):
    return tuple(max(0, min(255, component + delta)) for component in base[:3]) + (255,)


def noise(size, base, amount=3):
    image = Image.new("RGBA", size)
    for y in range(size[1]):
        for x in range(size[0]):
            image.putpixel((x, y), color(base, RNG.randint(-amount, amount)))
    return image


def save(name, image):
    image.save(TEX / (name + ".png"))


def crown(draw, x, y, scale=1, gold=GOLD, bright=LIGHT):
    # Hand-drawn stepped royal crown, shared by the book, banner and icon.
    def rect(coords, fill):
        draw.rectangle(tuple(round((value * scale) + (x if i % 2 == 0 else y))
                             for i, value in enumerate(coords)), fill=fill)
    for coords in ((0, 2, 2, 6), (2, 4, 4, 8), (4, 0, 6, 8),
                   (6, 4, 8, 8), (8, 2, 10, 6), (1, 7, 9, 9)):
        rect(coords, gold)
    rect((2, 7, 8, 7), bright)
    rect((4, 1, 5, 2), bright)
    rect((2, 9, 8, 10), DARK_GOLD)


# Walnut is deliberately quieter than the gilt. Long grain aligns along rails;
# dark knots and narrow joints keep the material readable on small native faces.
for name, base in (("walnut", (83, 55, 37)), ("walnut_dark", (47, 31, 24)),
                   ("walnut_top", (103, 72, 45))):
    image = noise((32, 32), base, 4)
    d = ImageDraw.Draw(image)
    for y in (0, 15, 31):
        d.line((0, y, 31, y), fill=color(base, -22))
        if y < 31:
            d.line((0, y + 1, 31, y + 1), fill=color(base, 11))
    for _ in range(45):
        x, y = RNG.randrange(32), RNG.randrange(2, 30)
        d.line((x, y, min(31, x + RNG.randrange(2, 9)), y), fill=color(base, RNG.choice((-15, 10))))
    d.arc((17, 6, 27, 11), 0, 300, fill=color(base, -19))
    d.line((20, 8, 24, 8), fill=color(base, -24))
    d.line((4, 22, 11, 22), fill=color(base, -14))
    save(name, image)

image = noise((32, 32), (175, 130, 57), 4)
d = ImageDraw.Draw(image)
for y in (0, 8, 16, 24):
    d.line((0, y, 31, y), fill=(221, 184, 102, 255))
    d.line((0, y + 6, 31, y + 6), fill=(132, 92, 38, 255))
for x, y in ((4, 3), (20, 11), (12, 19), (27, 27)):
    d.point((x, y), fill=LIGHT)
save("gilt", image)

image = noise((32, 32), (24, 58, 43), 2)
d = ImageDraw.Draw(image)
for y in range(0, 32, 2):
    for x in range(y % 4, 32, 4):
        d.point((x, y), fill=(32, 70, 51, 255))
d.rectangle((0, 0, 31, 31), outline=DARK_GOLD)
d.rectangle((1, 1, 30, 30), outline=GOLD)
d.rectangle((3, 3, 28, 28), outline=(53, 83, 53, 255))
for x, y in ((5, 5), (26, 5), (5, 26), (26, 26)):
    d.line((x - 2, y, x + 2, y), fill=GOLD)
    d.line((x, y - 2, x, y + 2), fill=GOLD)
save("velvet", image)

image = noise((32, 32), (60, 43, 30), 2)
d = ImageDraw.Draw(image)
d.rectangle((0, 0, 31, 31), outline=(31, 24, 20, 255))
d.rectangle((2, 2, 29, 29), outline=GOLD)
d.line((3, 3, 28, 3), fill=LIGHT)
for x in (5, 26):
    for y in (5, 26):
        d.rectangle((x - 1, y - 1, x + 1, y + 1), fill=DARK_GOLD)
        d.point((x, y), fill=LIGHT)
for side in (0, 1):
    for i in range(5):
        x = 5 + i * 3 if side == 0 else 26 - i * 3
        y = 15 - i if side == 0 else 15 + i
        d.line((x, y, x + (1 if side == 0 else -1), y - 2), fill=GOLD)
d.line((8, 15, 23, 15), fill=GOLD)
save("carved_panel", image)

image = noise((32, 32), (228, 209, 162), 2)
d = ImageDraw.Draw(image)
for y in range(1, 32, 3):
    d.line((0, y, 31, y), fill=(173, 146, 98, 255))
    d.line((0, y + 1, 31, y + 1), fill=(244, 224, 176, 255))
save("page_edge", image)

for page in ("left", "right"):
    image = noise((64, 64), (236, 218, 175), 2)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 63, 63), outline=(154, 116, 66, 255))
    d.rectangle((2, 2, 61, 61), outline=(201, 169, 108, 255))
    d.line((6, 6, 6, 57), fill=(163, 125, 64, 255))
    d.line((57, 6, 57, 57), fill=(163, 125, 64, 255))
    d.rectangle((10, 7, 52, 10), fill=(39, 77, 52, 255))
    d.line((17, 13, 46, 13), fill=GOLD)
    if page == "left":
        # Ornamental uncial initial surrounded by tiny acanthus leaves.
        d.rectangle((10, 18, 23, 33), fill=(38, 75, 50, 255), outline=GOLD)
        d.line((14, 21, 14, 30, 20, 30), fill=LIGHT, width=2)
        d.line((15, 21, 20, 21, 20, 25, 15, 25), fill=LIGHT)
        for x, y in ((11, 17), (24, 21), (24, 31), (11, 34)):
            d.line((x, y, x + 2, y - 2), fill=GOLD)
        for y, end in ((19, 51), (24, 49), (29, 52)):
            d.line((28, y, end, y), fill=(91, 86, 57, 255))
    else:
        crown(d, 23, 19, 1.5)
        d.line((17, 38, 46, 38), fill=GOLD)
    for i, y in enumerate(range(39 if page == "left" else 43, 56, 5)):
        end = (51, 48, 53, 47)[i % 4]
        d.line((11, y, end, y), fill=(104, 96, 66, 255))
        for gap in (19 + i, 35 - i):
            d.point((gap, y), fill=(236, 218, 175, 255))
    d.line((15, 58, 24, 56, 28, 58, 34, 55, 45, 57), fill=(70, 88, 57, 255))
    save("register_" + page, image)

# Banner: gold stitched borders, a crown above an ermine-lined pointed shield,
# and two oak branches. This is original pixel heraldry, not a borrowed emblem.
image = noise((64, 64), (24, 60, 45), 2)
d = ImageDraw.Draw(image)
for x in range(64):
    delta = round(5 * math.sin(x / 4.1))
    for y in range(64):
        c = image.getpixel((x, y))
        image.putpixel((x, y), color(c, delta))
d = ImageDraw.Draw(image)
d.rectangle((1, 1, 62, 62), outline=DARK_GOLD, width=2)
d.rectangle((3, 3, 60, 60), outline=GOLD)
d.rectangle((5, 5, 58, 58), outline=(64, 91, 55, 255))
for x in (9, 54):
    for y in (10, 22, 34, 46, 55):
        d.polygon([(x, y - 2), (x + 2, y), (x, y + 2), (x - 2, y)], fill=GOLD)
crown(d, 22, 9, 1.8)
d.polygon([(18, 30), (45, 30), (44, 43), (39, 50), (32, 55), (24, 50), (19, 43)], fill=DARK_GOLD)
d.polygon([(20, 31), (43, 31), (42, 43), (37, 49), (32, 52), (26, 48), (22, 42)], fill=LIGHT)
d.polygon([(23, 34), (40, 34), (39, 42), (35, 47), (32, 49), (28, 46), (25, 41)], fill=(35, 78, 54, 255))
# A gold tower and three stars distinguish the Realm Titles mark.
d.rectangle((29, 38, 34, 46), fill=GOLD)
d.rectangle((27, 36, 36, 39), fill=GOLD)
for x in (27, 31, 35):
    d.rectangle((x, 34, x + 1, 37), fill=LIGHT)
d.rectangle((31, 42, 32, 46), fill=(23, 50, 37, 255))
for x, y in ((25, 31), (38, 31), (32, 29)):
    d.point((x, y), fill=LIGHT)
for side in (-1, 1):
    for step in range(5):
        x, y = 32 + side * (18 - step), 49 - step * 4
        d.line((x, y, x - side * 2, y - 4), fill=GOLD)
        d.line((x, y - 2, x + side * 3, y - 3), fill=GOLD)
save("royal_standard", image)

image = noise((16, 16), (26, 43, 37), 2)
d = ImageDraw.Draw(image)
d.line((2, 2, 2, 12), fill=(96, 126, 101, 255))
d.line((3, 2, 9, 2), fill=(143, 160, 115, 255))
d.line((13, 5, 13, 13), fill=(13, 28, 27, 255))
save("ink_glass", image)
save("ink", noise((16, 16), (13, 24, 27), 1))

image = noise((32, 32), (144, 102, 41), 3)
d = ImageDraw.Draw(image)
d.ellipse((1, 1, 30, 30), fill=(192, 148, 70, 255), outline=DARK_GOLD, width=2)
d.ellipse((5, 5, 26, 26), outline=LIGHT)
crown(d, 10, 10, 1, gold=DARK_GOLD, bright=(244, 218, 151, 255))
save("royal_seal", image)

image = noise((16, 16), (88, 38, 31), 2)
d = ImageDraw.Draw(image)
d.rectangle((1, 1, 14, 14), outline=(157, 75, 48, 255))
crown(d, 3, 3, .8, gold=(190, 111, 62, 255), bright=(222, 146, 85, 255))
save("wax", image)

image = Image.new("RGBA", (32, 64))
d = ImageDraw.Draw(image)
feather = [(16, 0), (22, 7), (25, 19), (24, 30), (21, 41), (16, 52),
           (9, 44), (5, 33), (6, 22), (10, 10)]
d.polygon(feather, fill=(225, 220, 191, 255))
d.line((16, 1, 15, 62), fill=(164, 145, 95, 255), width=2)
d.line((17, 2, 16, 55), fill=(251, 244, 216, 255))
for y in range(12, 46, 5):
    d.line((7, y - 4, 14, y + 3), fill=(188, 184, 148, 255))
    d.line((18, y + 3, 24, y - 5), fill=(199, 193, 157, 255))
save("quill", image)

TEXTURES = {path.stem: "realmtitles:block/" + path.stem for path in sorted(TEX.glob("*.png"))}
TEXTURES["particle"] = "realmtitles:block/walnut"
ELEMENTS = []


def box(start, end, texture, name, *, up=None, north=None, south=None, rotation=None, full=False):
    dx, dy, dz = (end[i] - start[i] for i in range(3))
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        width, height = (dx, dz) if face in ("up", "down") else (dx if face in ("north", "south") else dz, dy)
        faces[face] = {"uv": [0, 0, 16 if full else min(16, width), 16 if full else min(16, height)],
                       "texture": "#" + texture}
    for face, override in (("up", up), ("north", north), ("south", south)):
        if override:
            faces[face] = {"uv": [0, 0, 16, 16], "texture": "#" + override}
    element = {"name": name, "from": start, "to": end, "faces": faces}
    if rotation:
        element["rotation"] = rotation
    ELEMENTS.append(element)


# Four carved feet, tapered through stepped native cuboids. The tall rear legs
# continue into the standard's posts, so every raised ornament has real support.
for x in (.75, 12.75):
    for z in (1.2, 12.0):
        box([x, 0, z], [x + 2.5, .65, z + 2.6], "walnut_dark", "plinth_foot")
        box([x + .17, .65, z + .17], [x + 2.33, 1.1, z + 2.43], "walnut", "bevelled_foot")
        box([x + .4, 1.1, z + .4], [x + 2.1, 8.8, z + 2.2], "walnut", "carved_leg")
        for y in (1.6, 7.8):
            box([x + .27, y, z + .27], [x + 2.23, y + .35, z + 2.33], "gilt", "gilt_leg_collar")
        box([x + .25, 8.5, z + .25], [x + 2.25, 11.3, z + 2.35], "walnut_dark", "upper_leg_block")
        box([x + .67, 2.6, z + .34], [x + 1.83, 6.7, z + .41], "gilt", "leg_fluting")
box([2, 3.1, 7.4], [14, 3.8, 8.6], "walnut_dark", "central_stretcher")
for x in (1.7, 13.1):
    box([x, 3.1, 2.8], [x + 1.2, 3.8, 13.3], "walnut", "side_stretcher")
    box([x, 8.8, 2.4], [x + 1.2, 11.25, 14.3], "walnut", "side_apron")
box([2.2, 8.7, 1.65], [13.8, 11.1, 2.8], "walnut_dark", "front_apron")
box([2.2, 8.7, 13.2], [13.8, 11.1, 14.4], "walnut_dark", "back_apron")
box([3.5, 8.95, 1.57], [12.5, 10.8, 1.67], "carved_panel", "gilt_front_marquetry", north="carved_panel")
for x in (3.1, 12.55):
    box([x, 9.55, 1.37], [x + .35, 9.9, 1.59], "gilt", "apron_rosette")
box([.2, 11.1, .6], [15.8, 11.55, 15.4], "walnut_dark", "table_lower_moulding")
box([.45, 11.55, .85], [15.55, 12.35, 15.15], "walnut", "walnut_tabletop", up="walnut_top")
box([.2, 12.35, .6], [15.8, 12.7, 15.4], "walnut_dark", "table_rounded_rim", up="walnut_top")
for z in (.54, 15.37):
    box([.7, 12.05, z], [15.3, 12.2, z + .08], "gilt", "fine_gold_table_edge")
for x in (.15, 15.77):
    box([x, 12.05, 1], [x + .08, 12.2, 15], "gilt", "fine_gold_side_edge")
box([1.5, 12.7, 1.8], [14.5, 12.79, 12.2], "velvet", "gold_edged_writing_velvet", up="velvet")

# Open illuminated register. Covers, layered page edges and differently decorated
# pages turn inward toward a recessed spine. Both angled leaves stay below y=16.
for side, start, end, angle in (("left", [2.0, 12.92, 3], [6.15, 13.16, 9.9], -22.5),
                                 ("right", [6.15, 12.92, 3], [10.3, 13.16, 9.9], 22.5)):
    rotation = {"origin": [6.15, 12.92, 6.45], "axis": "z", "angle": angle}
    box(start, end, "walnut_dark", side + "_book_cover", rotation=rotation)
    pages0 = [start[0] + .16, 13.16, 3.16]
    pages1 = [end[0] - .16, 13.47, 9.74]
    box(pages0, pages1, "page_edge", side + "_book_pages", up="register_" + side, rotation=rotation)
    for z in (3.03, 9.55):
        box([start[0] + .1, 13.17, z], [start[0] + .6, 13.24, z + .3], "gilt", side + "_cover_corner", rotation=rotation)
box([5.98, 12.84, 2.9], [6.32, 13.24, 10.15], "walnut_dark", "book_spine")
box([6.02, 13.24, 7], [6.24, 13.28, 11.3], "velvet", "green_bookmark")
box([5.99, 13.23, 11.3], [6.27, 13.32, 11.65], "gilt", "bookmark_tassel")

# Royal gold stamp on a garnet wax pad; a faceted base, turned handle and crown cap
# provide a second recognisable silhouette beside the open register.
box([11.1, 12.8, 2.7], [14.6, 12.94, 6.8], "walnut_dark", "seal_tray")
box([11.35, 12.94, 2.95], [14.35, 13.02, 6.55], "velvet", "seal_tray_velvet", up="velvet")
box([11.7, 13.02, 3.4], [13.95, 13.29, 5.65], "gilt", "royal_seal_foot", up="royal_seal")
box([12.05, 13.29, 3.75], [13.6, 13.55, 5.3], "gilt", "stamp_bevel")
box([12.45, 13.55, 4.15], [13.2, 15.1, 4.9], "walnut_dark", "turned_stamp_handle")
box([12.22, 14.25, 3.92], [13.43, 14.67, 5.13], "walnut", "stamp_handle_bulb")
box([12.34, 15.1, 4.04], [13.31, 15.5, 5.01], "gilt", "stamp_crown_cap", up="royal_seal")
box([11.0, 12.81, 7], [13.0, 13.06, 8.55], "wax", "small_wax_impression", up="wax")

# Green glass inkwell, visible ink pool and a pale quill. The thin feather has
# genuine alpha cutouts and is clipped with continuous UVs across the two halves.
box([11.6, 12.8, 9.45], [14.8, 13.1, 12.0], "gilt", "inkwell_saucer")
box([12.0, 13.1, 9.8], [14.4, 14.05, 11.65], "ink_glass", "inkwell_glass")
box([12.2, 14.05, 10], [14.2, 14.27, 11.45], "gilt", "inkwell_lip")
box([12.45, 14.26, 10.24], [13.95, 14.3, 11.2], "ink", "inkwell_pool")
for i in range(6):
    box([12.95 + i * .1, 14.28 + i * .33, 10.62],
        [13.15 + i * .1, 14.7 + i * .33, 10.8], "gilt", "stepped_quill_shaft")
box([11.9, 15.1, 10.66], [15.35, 22.4, 10.78], "quill", "ivory_quill", north="quill", south="quill")

# Rear standard. Corner posts continue directly from the legs. Small outer gold
# strips and stepped finials read clearly from the back as well as the front.
for x in (1.35, 13.35):
    box([x, 12.7, 12.65], [x + 1.3, 29.5, 14.0], "walnut", "standard_post")
    box([x + .43, 13.4, 12.55], [x + .87, 28.15, 12.66], "gilt", "standard_post_gold_flute")
    for y in (14.3, 27.8, 29.45):
        box([x - .16, y, 12.49], [x + 1.46, y + .35, 14.16], "gilt", "standard_post_collar")
    box([x + .05, 29.8, 12.7], [x + 1.25, 30.4, 13.9], "walnut_dark", "finial_lower_step")
    box([x + .22, 30.4, 12.87], [x + 1.08, 31.17, 13.73], "gilt", "finial_golden_knop")
    box([x + .4, 31.17, 13.05], [x + .9, 31.5, 13.55], "gilt", "finial_tip")
box([1, 28.5, 12.4], [15, 29.25, 14.3], "walnut_dark", "standard_crossbar")
box([1.4, 29.25, 12.6], [14.6, 29.65, 14.1], "gilt", "standard_top_gilt")
box([3.1, 27.9, 12.65], [12.9, 28.6, 13.9], "walnut", "banner_roller")
for x in (3.5, 6.35, 9.2, 12.0):
    box([x, 27.4, 12.53], [x + .48, 28.7, 13.87], "gilt", "banner_suspension_ring")
box([2.9, 18.6, 13.0], [13.1, 27.9, 13.23], "velvet", "royal_banner", north="royal_standard", south="royal_standard")
box([3.65, 17.85, 13.0], [12.35, 18.6, 13.23], "velvet", "banner_lower_step")
box([4.65, 17.1, 13.0], [11.35, 17.85, 13.23], "velvet", "banner_point_step")
box([6.5, 16.3, 13.0], [9.5, 17.1, 13.23], "velvet", "banner_centre_point")
for x in (2.83, 12.92):
    box([x, 18.55, 12.92], [x + .23, 27.85, 13.31], "gilt", "banner_side_gold_braid")
for x, y, width in ((3.65, 18.45, 8.7), (4.65, 17.7, 6.7), (6.5, 16.95, 3)):
    box([x, y, 12.91], [x + width, y + .18, 13.32], "gilt", "banner_hem_braid")
box([7.62, 16.05, 12.92], [8.38, 16.65, 13.32], "gilt", "banner_gold_pendant")
# Carved five-point crown pediment: low side prongs, raised central jewel.
box([5.2, 29.65, 12.75], [10.8, 30.15, 14], "gilt", "pediment_crown_band")
box([5.4, 30.15, 12.83], [6.3, 31.1, 13.92], "gilt", "pediment_left_prong")
box([6.3, 30.15, 12.83], [7.3, 30.7, 13.92], "gilt", "pediment_left_valley")
box([7.3, 30.15, 12.83], [8.7, 31.9, 13.92], "gilt", "pediment_centre_prong")
box([8.7, 30.15, 12.83], [9.7, 30.7, 13.92], "gilt", "pediment_right_valley")
box([9.7, 30.15, 12.83], [10.6, 31.1, 13.92], "gilt", "pediment_right_prong")
box([7.7, 30.6, 12.72], [8.3, 31.2, 12.84], "velvet", "crown_emerald")


def split_model(half):
    minimum = 0 if half == "lower" else 16
    result = []
    for original in ELEMENTS:
        lo = max(minimum, original["from"][1])
        hi = min(minimum + 16, original["to"][1])
        if hi <= lo:
            continue
        element = deepcopy(original)
        element["from"][1], element["to"][1] = round(lo - minimum, 5), round(hi - minimum, 5)
        if "rotation" in element:
            element["rotation"]["origin"][1] -= minimum
        height = original["to"][1] - original["from"][1]
        for side in ("north", "south", "east", "west"):
            uv = original["faces"][side]["uv"]
            top = (original["to"][1] - hi) / height
            bottom = (original["to"][1] - lo) / height
            element["faces"][side]["uv"] = [uv[0], round(uv[1] + (uv[3] - uv[1]) * top, 5),
                                                uv[2], round(uv[1] + (uv[3] - uv[1]) * bottom, 5)]
        if lo > original["from"][1]:
            del element["faces"]["down"]
        if hi < original["to"][1]:
            del element["faces"]["up"]
        for point in (element["from"], element["to"]):
            assert all(0 <= value <= 16 for value in point), (element["name"], point)
        result.append(element)
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": True,
            "textures": TEXTURES, "elements": result}


for half in ("lower", "upper"):
    write_json(ASSETS / f"models/block/royal_seal_desk_{half}.json", split_model(half))
write_json(ASSETS / "blockstates/royal_seal_desk.json", {"variants": {
    f"facing={direction},half={half}": {"model": f"realmtitles:block/royal_seal_desk_{half}",
                                       **({"y": angle} if angle else {})}
    for direction, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
    for half in ("lower", "upper")}})
write_json(ASSETS / "models/item/royal_seal_desk.json", {
    "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": True,
    "textures": TEXTURES, "elements": ELEMENTS,
    "display": {
        "gui": {"rotation": [25, 225, 0], "translation": [0, -3.5, 0], "scale": [.45, .45, .45]},
        "ground": {"translation": [0, 1.5, 0], "scale": [.24, .24, .24]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, 0], "scale": [.45, .45, .45]},
        "firstperson_righthand": {"rotation": [0, 35, 0], "translation": [1, -2, 0], "scale": [.32, .32, .32]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [1, -2, 0], "scale": [.32, .32, .32]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [.26, .26, .26]},
        "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 1.5, 0], "scale": [.26, .26, .26]}}})
write_json(RES / "data/realmtitles/recipe/royal_seal_desk.json", {
    "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["BFG", "WEW", "WWW"],
    "key": {"B": {"item": "minecraft:book"}, "F": {"item": "minecraft:feather"},
            "G": {"item": "minecraft:gold_ingot"}, "W": {"tag": "minecraft:planks"},
            "E": {"item": "minecraft:green_wool"}},
    "result": {"id": "realmtitles:royal_seal_desk", "count": 1}})
write_json(RES / "data/realmtitles/advancement/recipes/decorations/royal_seal_desk.json", {
    "parent": "minecraft:recipes/root", "criteria": {
        "has_book": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ["minecraft:book"]}]}},
        "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "realmtitles:royal_seal_desk"}}},
    "requirements": [["has_book", "has_the_recipe"]],
    "rewards": {"recipes": ["realmtitles:royal_seal_desk"]}})
write_json(RES / "data/realmtitles/loot_table/blocks/royal_seal_desk.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "realmtitles:royal_seal_desk"}], "conditions": [
            {"condition": "minecraft:block_state_property", "block": "realmtitles:royal_seal_desk", "properties": {"half": "lower"}},
            {"condition": "minecraft:survives_explosion"}]}]})
write_json(RES / "data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": ["realmtitles:royal_seal_desk"]})
write_json(RES / "pack.mcmeta", {"pack": {"pack_format": 34, "description": "Realm Titles — original royal desk artwork"}})

# Original 128px pixel art, enlarged by nearest-neighbour to a crisp 256px mod
# list icon. A crowned green escutcheon, open gilded register and seal echo the
# actual block, with no stock or externally generated artwork.
icon = Image.new("RGBA", (128, 128), (16, 30, 26, 255))
d = ImageDraw.Draw(icon)
d.rectangle((3, 3, 124, 124), fill=(35, 27, 22, 255), outline=DARK_GOLD, width=2)
d.rectangle((6, 6, 121, 121), fill=(19, 44, 34, 255), outline=GOLD)
for y in range(9, 120):
    for x in range(9, 120):
        c = icon.getpixel((x, y))
        icon.putpixel((x, y), color(c, RNG.randrange(-2, 3)))
d = ImageDraw.Draw(icon)
for x in (12, 115):
    for y in (12, 115):
        d.rectangle((x - 3, y - 1, x + 3, y + 1), fill=GOLD)
        d.rectangle((x - 1, y - 3, x + 1, y + 3), fill=GOLD)
# Low-contrast radiating stitch work behind the central emblem.
for x, y in ((24, 25), (104, 25), (19, 65), (109, 65), (30, 101), (98, 101)):
    d.line((x, y - 4, x, y + 4), fill=(63, 79, 47, 255))
    d.line((x - 3, y, x + 3, y), fill=(63, 79, 47, 255))
crown(d, 44, 15, 3.8)
d.polygon([(34, 49), (94, 49), (91, 76), (81, 93), (64, 105), (47, 94), (37, 76)], fill=(10, 26, 22, 255))
d.polygon([(36, 47), (92, 47), (89, 74), (79, 90), (64, 101), (49, 91), (39, 74)], fill=GOLD)
d.polygon([(40, 51), (88, 51), (85, 73), (76, 87), (64, 96), (52, 87), (43, 73)], fill=(32, 70, 48, 255))
# Open register is the large legible foreground mark.
d.polygon([(24, 69), (60, 64), (65, 67), (68, 64), (102, 70), (99, 100), (67, 96), (62, 99), (27, 104)], fill=(19, 28, 23, 255))
d.polygon([(25, 66), (59, 61), (64, 65), (69, 61), (103, 67), (100, 97), (68, 93), (63, 96), (28, 101)], fill=GOLD)
d.polygon([(29, 66), (58, 63), (62, 67), (62, 91), (31, 96)], fill=(236, 218, 175, 255))
d.polygon([(66, 67), (70, 63), (99, 68), (97, 92), (66, 89)], fill=(223, 201, 155, 255))
d.line((62, 67, 62, 92), fill=(141, 103, 55, 255))
d.line((66, 67, 66, 90), fill=(248, 230, 186, 255))
d.rectangle((35, 70, 42, 79), fill=(38, 75, 50, 255), outline=GOLD)
for y, end in ((72, 55), (77, 54), (82, 55), (87, 53)):
    start = 46 if y < 81 else 35
    d.line((start, y, end, y - 1), fill=(116, 106, 69, 255))
for y in (71, 76, 81, 86):
    d.line((72, y, 93, y + 2), fill=(116, 106, 69, 255))
# Warm gold royal seal with forest-green ribbon tails.
d.polygon([(81, 95), (89, 97), (85, 116), (80, 112), (76, 114)], fill=GOLD)
d.polygon([(90, 96), (97, 94), (104, 112), (98, 110), (96, 115)], fill=GOLD)
d.polygon([(83, 97), (87, 98), (83, 112), (81, 109), (79, 111)], fill=GREEN)
d.polygon([(91, 97), (95, 96), (101, 109), (97, 108), (96, 111)], fill=GREEN)
d.ellipse((76, 84, 100, 107), fill=DARK_GOLD)
d.ellipse((77, 84, 99, 105), fill=GOLD, outline=LIGHT)
d.ellipse((80, 87, 96, 102), outline=DARK_GOLD)
crown(d, 83, 90, .9, gold=DARK_GOLD, bright=LIGHT)
icon.resize((256, 256), Image.Resampling.NEAREST).save(ASSETS / "icon.png")
print(f"Generated {len(ELEMENTS)} original native elements, {len(TEXTURES) - 1} block textures, 8 states and a 256px pixel icon.")
