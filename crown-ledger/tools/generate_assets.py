#!/usr/bin/env python3
"""Generate original, deterministic Crown Treasury pixel textures and native block models.

Requires Pillow only when regenerating assets. No external artwork is used.
"""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/kingdomtreasury"
TEXTURES = ASSETS / "textures/block"
TEXTURES.mkdir(parents=True, exist_ok=True)
RNG = random.Random(79143)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def color(base, shift):
    return tuple(max(0, min(255, channel + shift)) for channel in base) + (255,)


def noise(size, base, spread=6):
    image = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            image.putpixel((x, y), color(base, RNG.randint(-spread, spread)))
    return image


def save(name, image):
    image.save(TEXTURES / (name + ".png"))


# Warm, individually shaded oak boards. Long, sparse grain keeps the low-res
# material legible from Minecraft's normal viewing distances.
for name, base, vertical in [("chest_wood", (119, 78, 45), False),
                              ("chest_lid", (139, 95, 53), True),
                              ("chest_dark_wood", (74, 48, 33), False)]:
    image = noise(32, base, 5)
    draw = ImageDraw.Draw(image)
    for seam in (0, 8, 16, 24):
        if vertical:
            draw.line((seam, 0, seam, 31), fill=color(base, -27))
            draw.line((seam + 1, 0, seam + 1, 31), fill=color(base, 16))
        else:
            draw.line((0, seam, 31, seam), fill=color(base, -29))
            draw.line((0, seam + 1, 31, seam + 1), fill=color(base, 14))
    for _ in range(29):
        x, y = RNG.randrange(2, 29), RNG.randrange(2, 29)
        length = RNG.randrange(2, 7)
        if vertical:
            draw.line((x, y, x, min(31, y + length)), fill=color(base, RNG.choice((-17, 11))))
        else:
            draw.line((x, y, min(31, x + length), y), fill=color(base, RNG.choice((-17, 11))))
    save(name, image)

for name, base in [("chest_iron", (57, 64, 61)), ("chest_gold", (174, 127, 49))]:
    image = noise(16, base, 4)
    draw = ImageDraw.Draw(image)
    draw.line((0, 0, 15, 0), fill=color(base, 47))
    draw.line((0, 1, 15, 1), fill=color(base, 24))
    draw.line((0, 0, 0, 15), fill=color(base, 28))
    draw.line((15, 1, 15, 15), fill=color(base, -36))
    draw.line((0, 15, 15, 15), fill=color(base, -44))
    draw.point((4, 5), fill=color(base, 13))
    draw.point((11, 10), fill=color(base, -15))
    save(name, image)

image = noise(16, (88, 40, 34), 4)
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 15, 15), outline=(43, 27, 24, 255))
draw.rectangle((1, 1, 14, 14), outline=(145, 102, 48, 255))
for x, y in [(2, 2), (12, 2), (2, 12), (12, 12)]:
    draw.rectangle((x, y, x + 1, y + 1), fill=(188, 145, 68, 255))
save("ledger_cover", image)

for name, right in [("ledger_page_left", False), ("ledger_page_right", True)]:
    image = noise(32, (234, 218, 179), 3)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 31, 31), outline=(164, 134, 88, 255))
    draw.line((3 if right else 28, 2, 3 if right else 28, 29), fill=(192, 166, 120, 255))
    draw.rectangle((6, 4, 23, 5), fill=(136, 65, 47, 255))
    draw.line((6, 8, 25, 8), fill=(176, 148, 102, 255))
    for row, y in enumerate((12, 16, 20, 24, 28)):
        draw.line((6, y, 17 - (row % 3), y), fill=(131, 108, 75, 255))
        draw.line((21, y, 24 + (row % 2), y), fill=(112, 105, 75, 255))
        if right:
            draw.point((25, y - 1), fill=(112, 105, 75, 255))
    save(name, image)

image = noise(16, (222, 202, 157), 3)
draw = ImageDraw.Draw(image)
for y in range(2, 16, 3):
    draw.line((0, y, 15, y), fill=(174, 148, 102, 255))
save("ledger_edges", image)

# A tiny pixel crown on a green plate, authored at its final 16px resolution.
image = noise(16, (43, 60, 48), 2)
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 15, 15), outline=(99, 76, 39, 255))
draw.line((1, 1, 14, 1), fill=(203, 165, 78, 255))
draw.line((1, 1, 1, 14), fill=(170, 128, 57, 255))
draw.rectangle((4, 10, 11, 12), fill=(184, 137, 48, 255))
draw.polygon([(3, 5), (6, 8), (7, 3), (8, 3), (9, 8), (12, 5), (11, 10), (4, 10)], fill=(218, 176, 77, 255))
for xy in ((3, 4), (7, 2), (12, 4)):
    draw.rectangle((*xy, xy[0] + 1, xy[1] + 1), fill=(237, 206, 116, 255))
save("chest_crest", image)

image = noise(16, (188, 142, 57), 3)
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 15, 15), outline=(118, 79, 35, 255))
draw.line((1, 1, 14, 1), fill=(231, 192, 99, 255))
draw.rectangle((6, 5, 9, 8), fill=(56, 45, 31, 255))
draw.rectangle((7, 8, 8, 11), fill=(56, 45, 31, 255))
save("chest_lock", image)

textures = {key: "kingdomtreasury:block/" + name for key, name in {
    "wood": "chest_wood", "lid": "chest_lid", "dark": "chest_dark_wood",
    "iron": "chest_iron", "gold": "chest_gold", "cover": "ledger_cover",
    "paper_left": "ledger_page_left", "paper_right": "ledger_page_right",
    "paper_edge": "ledger_edges", "crest": "chest_crest", "lock": "chest_lock",
}.items()}
textures["particle"] = textures["wood"]
elements = []


def box(start, end, texture, name, *, rotation=None, north=None, up=None):
    faces = {side: {"uv": [0, 0, 16, 16], "texture": "#" + texture}
             for side in ("north", "south", "east", "west", "up", "down")}
    if north:
        faces["north"]["texture"] = "#" + north
    if up:
        faces["up"]["texture"] = "#" + up
    element = {"name": name, "from": start, "to": end, "faces": faces}
    if rotation:
        element["rotation"] = rotation
    elements.append(element)


for x in (1.3, 11.7):
    for z in (1.8, 11.1):
        box([x, 0, z], [x + 3, 1.6, z + 3], "dark", "carved_foot")
box([1, 1.3, 1.5], [15, 2.3, 14.5], "iron", "lower_iron_rim")
box([1.5, 2.3, 2], [14.5, 10.2, 14], "wood", "oak_body")
for x in (1, 13.5):
    for z in (1.5, 13):
        box([x, 2.2, z], [x + 1.5, 10.4, z + 1.5], "dark", "corner_post")
box([1, 10.25, 1.5], [15, 11.4, 14.5], "dark", "lid_shadow_rim")
box([1.35, 11.4, 1.85], [14.65, 12.45, 14.15], "lid", "stepped_lid")
box([2, 12.45, 2.5], [14, 13.15, 13.5], "lid", "lid_crown")
box([1.3, 10.65, 1.35], [14.7, 11.05, 1.7], "gold", "front_brass_inlay")
box([1.3, 2.4, 1.7], [14.7, 2.75, 2.1], "gold", "lower_brass_inlay")
for x in (3, 12):
    box([x, 2.25, 1.55], [x + 1, 10.25, 2.15], "iron", "front_band")
    box([x, 2.25, 13.85], [x + 1, 10.25, 14.45], "iron", "rear_band")
    box([x, 11.25, 1.75], [x + 1, 13.35, 14.25], "iron", "lid_band")
    for y in (3.5, 8.6):
        box([x + .3, y, 1.3], [x + .7, y + .4, 1.56], "gold", "front_rivet")
    box([x - .1, 10, 14.25], [x + 1.1, 12, 14.85], "gold", "rear_hinge")
# Small handles project from the wooden sides without suggesting a second block.
for x in (.75, 14.5):
    for z in (5.7, 9.5):
        box([x, 6.2, z], [x + .75, 8.3, z + .65], "iron", "handle_mount")
    box([x - .25 if x < 8 else x + .25, 6.4, 6.1],
        [x + .5 if x < 8 else x + 1, 7.1, 9.8], "gold", "side_handle")
# Royal plate above a real modeled lock, with no emissive or animated renderer.
box([5.8, 8.1, 1.18], [10.2, 11.15, 1.62], "iron", "crown_plate", north="crest")
box([7.05, 6.8, .95], [7.55, 8.55, 1.5], "gold", "lock_shackle_left")
box([8.45, 6.8, .95], [8.95, 8.55, 1.5], "gold", "lock_shackle_right")
box([7.1, 8.05, .95], [8.9, 8.55, 1.5], "gold", "lock_shackle_top")
box([6.7, 4.7, .6], [9.3, 7.2, 1.65], "gold", "brass_lock", north="lock")
# An open account book resting on the lid: covers and page stacks are sloped
# in native 22.5-degree elements, leaving the complete model within one block.
for left in (True, False):
    rot = {"origin": [8, 13.45, 9.1], "axis": "z", "angle": -22.5 if left else 22.5}
    x0, x1 = (3.55, 8) if left else (8, 12.45)
    box([x0, 13.2, 5.5], [x1, 13.5, 12.8], "cover", "ledger_cover", rotation=rot)
    box([x0 + (.2 if left else .05), 13.5, 5.75], [x1 - (.05 if left else .2), 13.82, 12.55],
        "paper_edge", "ledger_pages", rotation=rot, up="paper_left" if left else "paper_right")
box([7.76, 13.17, 5.4], [8.24, 13.47, 12.9], "cover", "ledger_spine")
box([7.85, 13.49, 11.4], [8.15, 13.56, 13.3], "gold", "ledger_ribbon")

model = {
    "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": True,
    "textures": textures, "elements": elements,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.72, .72, .72]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.35, .35, .35]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.65, .65, .65]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.45, .45, .45]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.35, .35, .35]},
    },
}
write_json(ASSETS / "models/block/crown_chest.json", model)
write_json(ASSETS / "models/item/crown_chest.json", {"parent": "kingdomtreasury:block/crown_chest"})
write_json(ASSETS / "blockstates/crown_chest.json", {"variants": {
    "facing=north": {"model": "kingdomtreasury:block/crown_chest"},
    "facing=east": {"model": "kingdomtreasury:block/crown_chest", "y": 90},
    "facing=south": {"model": "kingdomtreasury:block/crown_chest", "y": 180},
    "facing=west": {"model": "kingdomtreasury:block/crown_chest", "y": 270},
}})
write_json(RES / "data/kingdomtreasury/recipe/crown_chest.json", {
    "type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["IPI", "GCG", "WWW"],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "P": {"item": "minecraft:paper"},
            "G": {"item": "minecraft:gold_ingot"}, "C": {"item": "minecraft:chest"},
            "W": {"tag": "minecraft:planks"}},
    "result": {"id": "kingdomtreasury:crown_chest", "count": 1},
})
write_json(RES / "data/kingdomtreasury/loot_table/blocks/crown_chest.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "kingdomtreasury:crown_chest"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}],
})
print(f"Generated {len(elements)} native model elements and {len(list(TEXTURES.glob('*.png')))} original pixel textures.")
