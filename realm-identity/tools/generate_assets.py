#!/usr/bin/env python3
"""Generate Realm Identity's original pixel artwork and native Java Edition models.

All artwork is drawn here at its final pixel resolution; no external art, generated
web imagery, custom renderer, or model library is required. Regeneration: Pillow.
"""
from pathlib import Path
import json
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/realmidentity"
BLOCK = ASSETS / "textures/block"
ITEM = ASSETS / "textures/item"
BLOCK.mkdir(parents=True, exist_ok=True)
ITEM.mkdir(parents=True, exist_ok=True)
RNG = random.Random(284601)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def shade(base, amount):
    return tuple(max(0, min(255, value + amount)) for value in base) + (255,)


def grain(size, base, variation=5):
    image = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            image.putpixel((x, y), shade(base, RNG.randint(-variation, variation)))
    return image


def save(name, image):
    image.save(BLOCK / (name + ".png"))


# Distinct oak tones with long broken grain and hand-cut plank joints.
for name, base, seams in (("oak", (125, 84, 48), (0, 16)),
                           ("oak_top", (153, 108, 62), (0, 10, 21)),
                           ("oak_dark", (78, 53, 34), (0, 16))):
    image = grain(32, base, 5)
    draw = ImageDraw.Draw(image)
    for y in seams:
        draw.line((0, y, 31, y), fill=shade(base, -31))
        draw.line((0, y + 1, 31, y + 1), fill=shade(base, 18))
    for _ in range(38):
        x, y = RNG.randrange(1, 30), RNG.randrange(2, 30)
        draw.line((x, y, min(31, x + RNG.randrange(2, 9)), y), fill=shade(base, RNG.choice((-18, 12))))
    draw.line((9, 5, 13, 5), fill=shade(base, -22))
    draw.line((8, 6, 10, 7), fill=shade(base, -17))
    draw.line((11, 8, 14, 7), fill=shade(base, -17))
    save(name, image)

image = grain(16, (173, 130, 53), 4)
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 15, 15), outline=(91, 65, 29, 255))
draw.line((1, 1, 14, 1), fill=(226, 188, 99, 255))
draw.line((1, 1, 1, 14), fill=(204, 162, 73, 255))
draw.line((2, 14, 14, 14), fill=(126, 88, 32, 255))
save("brass", image)

image = grain(16, (38, 66, 48), 3)
draw = ImageDraw.Draw(image)
for x in (1, 14):
    draw.line((x, 0, x, 15), fill=(128, 111, 60, 255))
for y in (1, 14):
    draw.line((0, y, 15, y), fill=(102, 100, 55, 255))
save("green_cloth", image)

image = grain(32, (233, 216, 170), 3)
draw = ImageDraw.Draw(image)
draw.rectangle((0, 0, 31, 31), outline=(161, 128, 78, 255))
draw.line((2, 2, 29, 2), fill=(199, 174, 120, 255))
draw.line((2, 2, 2, 29), fill=(205, 181, 129, 255))
draw.rectangle((6, 4, 24, 5), fill=(55, 78, 52, 255))
draw.line((9, 7, 22, 7), fill=(143, 114, 66, 255))
draw.rectangle((5, 11, 8, 15), outline=(79, 85, 59, 255))
draw.line((9, 11, 25, 11), fill=(95, 92, 65, 255))
draw.line((9, 14, 23, 14), fill=(108, 97, 65, 255))
for y, end in ((18, 26), (21, 23), (24, 25)):
    draw.line((5, y, end, y), fill=(116, 104, 72, 255))
draw.line((7, 28, 15, 27), fill=(82, 85, 60, 255))
draw.line((9, 26, 11, 29), fill=(82, 85, 60, 255))
save("charter", image)

image = grain(16, (220, 199, 148), 3)
draw = ImageDraw.Draw(image)
for y in range(2, 16, 4):
    draw.line((0, y, 15, y), fill=(168, 140, 91, 255))
save("paper_edge", image)

for name, base in (("ink", (19, 30, 33)), ("ink_glass", (47, 67, 65)),
                   ("wax", (38, 79, 50)), ("feather", (230, 223, 192))):
    image = grain(16, base, 3)
    draw = ImageDraw.Draw(image)
    if name == "ink_glass":
        draw.line((2, 2, 2, 12), fill=(99, 121, 109, 255))
        draw.line((3, 2, 6, 2), fill=(119, 136, 117, 255))
    elif name == "wax":
        draw.rectangle((2, 2, 13, 13), outline=(71, 109, 65, 255))
        draw.line((6, 4, 6, 11), fill=(126, 143, 78, 255))
        draw.line((6, 4, 10, 4), fill=(126, 143, 78, 255))
        draw.line((6, 7, 9, 7), fill=(126, 143, 78, 255))
    elif name == "feather":
        draw.line((7, 0, 7, 15), fill=(165, 150, 113, 255))
        draw.line((8, 0, 8, 15), fill=(248, 238, 209, 255))
        for y in range(1, 15, 3):
            draw.line((1, y, 6, min(15, y + 2)), fill=(198, 188, 151, 255))
            draw.line((9, min(15, y + 2), 14, y), fill=(203, 192, 156, 255))
    save(name, image)

# A portable charter with an unfolded corner, marginal rules, green wax and a
# small embossed feather. The transparent silhouette remains legible at 16px.
image = Image.new("RGBA", (64, 64))
draw = ImageDraw.Draw(image)
outline = [(9, 3), (46, 3), (56, 13), (56, 58), (54, 61), (8, 61), (6, 58), (6, 6)]
draw.polygon(outline, fill=(107, 76, 43, 255))
inner = [(10, 5), (45, 5), (54, 14), (54, 57), (52, 59), (9, 59), (8, 57), (8, 7)]
draw.polygon(inner, fill=(234, 216, 169, 255))
for y in range(6, 59):
    for x in range(9, 54):
        if image.getpixel((x, y)) == (234, 216, 169, 255):
            image.putpixel((x, y), shade((234, 216, 169), RNG.randrange(-4, 5)))
draw = ImageDraw.Draw(image)
draw.line((11, 7, 43, 7), fill=(252, 235, 190, 255), width=2)
draw.line((10, 10, 10, 56), fill=(250, 232, 186, 255))
draw.line((12, 11, 12, 54), fill=(151, 132, 76, 255))
draw.line((15, 11, 15, 53), fill=(63, 90, 57, 255))
draw.line((17, 13, 42, 13), fill=(63, 90, 57, 255), width=2)
draw.line((20, 17, 44, 17), fill=(164, 130, 65, 255))
# Ornamental initial and hand-written lines deliberately avoid real personal data.
draw.rectangle((18, 22, 25, 30), outline=(59, 84, 54, 255))
draw.line((20, 24, 20, 28), fill=(100, 103, 65, 255))
draw.line((20, 24, 23, 24), fill=(100, 103, 65, 255))
for y, x0, x1 in ((23, 29, 48), (27, 29, 46), (32, 18, 48), (36, 18, 44), (40, 18, 47)):
    draw.line((x0, y, x1, y), fill=(119, 109, 72, 255))
    draw.point((x1 - 6, y), fill=(216, 192, 139, 255))
draw.line((19, 48, 30, 45), fill=(77, 91, 59, 255))
draw.line((23, 44, 25, 49), fill=(77, 91, 59, 255))
draw.line((18, 52, 31, 52), fill=(165, 139, 84, 255))
# Folded upper corner casts a hard pixel shadow into the parchment.
draw.polygon([(44, 4), (55, 15), (45, 15)], fill=(144, 113, 64, 255))
draw.polygon([(46, 5), (54, 13), (47, 13)], fill=(253, 237, 194, 255))
draw.line((47, 6, 47, 12), fill=(213, 190, 141, 255))
# Two short gold-edged green ribbon tails descend from the stamped seal.
draw.polygon([(39, 45), (44, 46), (43, 59), (40, 56), (37, 58)], fill=(157, 116, 45, 255))
draw.polygon([(40, 46), (43, 47), (42, 56), (40, 54), (39, 55)], fill=(40, 75, 48, 255))
draw.polygon([(45, 46), (49, 45), (53, 56), (49, 55), (47, 58)], fill=(157, 116, 45, 255))
draw.polygon([(46, 47), (48, 47), (51, 54), (49, 53), (48, 55)], fill=(40, 75, 48, 255))
seal = [(38, 39), (42, 37), (47, 38), (51, 42), (50, 48), (47, 51), (41, 51), (37, 47), (36, 43)]
draw.polygon(seal, fill=(24, 49, 33, 255))
draw.polygon([(39, 40), (43, 39), (47, 40), (49, 43), (48, 47), (45, 49), (41, 48), (38, 45)], fill=(56, 98, 58, 255))
draw.line((40, 41, 44, 40, 47, 42), fill=(111, 139, 75, 255))
draw.line((41, 47, 46, 42), fill=(178, 159, 87, 255))
draw.line((43, 45, 43, 42, 46, 42, 46, 44, 44, 45), fill=(149, 149, 78, 255))
image.save(ITEM / "identity_document.png")

textures = {name: "realmidentity:block/" + name for name in (
    "oak", "oak_top", "oak_dark", "brass", "green_cloth", "charter", "paper_edge",
    "ink", "ink_glass", "wax", "feather")}
textures["particle"] = textures["oak"]
elements = []


def box(start, end, texture, name, *, up=None, north=None, rotation=None):
    faces = {side: {"uv": [0, 0, 16, 16], "texture": "#" + texture}
             for side in ("north", "south", "east", "west", "up", "down")}
    if up:
        faces["up"]["texture"] = "#" + up
    if north:
        faces["north"]["texture"] = "#" + north
    element = {"name": name, "from": start, "to": end, "faces": faces}
    if rotation:
        element["rotation"] = rotation
    elements.append(element)


# Four carved square legs, each with a collar and broad foot.
for x in (1.2, 12.1):
    for z in (2.1, 11.1):
        box([x, 0, z], [x + 2.7, .9, z + 2.7], "oak_dark", "oak_foot")
        box([x + .35, .9, z + .35], [x + 2.35, 8.8, z + 2.35], "oak", "oak_leg")
        box([x + .1, 1.1, z + .1], [x + 2.6, 1.65, z + 2.6], "oak_dark", "foot_collar")
        box([x + .1, 7.5, z + .1], [x + 2.6, 8.2, z + 2.6], "oak_dark", "upper_leg_collar")
box([2, 3, 7.3], [14, 3.9, 8.7], "oak_dark", "central_stretcher")
for x in (2, 12.7):
    box([x, 3, 3], [x + 1.3, 3.9, 13], "oak_dark", "side_stretcher")
    box([x, 8.7, 2], [x + 1.3, 10.7, 14], "oak", "side_apron")
box([2, 8.5, 2], [14, 10.5, 3.1], "oak_dark", "front_apron")
box([2, 8.5, 12.9], [14, 10.5, 14], "oak_dark", "rear_apron")
box([4.5, 8.85, 1.9], [11.5, 10.1, 2.1], "green_cloth", "green_front_inlay")
box([5.1, 9.05, 1.74], [5.45, 9.4, 1.94], "brass", "left_inlay_rivet")
box([10.55, 9.05, 1.74], [10.9, 9.4, 1.94], "brass", "right_inlay_rivet")
box([.5, 10.5, 1], [15.5, 11.1, 15], "oak_dark", "table_shadow_rim")
box([.5, 11.1, 1], [15.5, 11.85, 15], "oak", "oak_tabletop", up="oak_top")
box([.8, 11.3, .94], [15.2, 11.55, 1.02], "brass", "fine_front_brass_edge")
box([1.7, 11.85, 2.1], [10.9, 11.96, 13.4], "green_cloth", "writing_mat")
# One open charter sheet with two subtle raised curls at the side edges.
box([2.4, 11.96, 3], [10.2, 12.15, 12.6], "paper_edge", "open_charter", up="charter")
box([2.25, 12.05, 3], [2.5, 12.32, 12.6], "paper_edge", "left_paper_curl")
box([10.1, 12.05, 3], [10.35, 12.28, 12.6], "paper_edge", "right_paper_curl")
# Dark green wax rests beside a short, modelled brass seal press.
box([11, 11.85, 2.7], [14, 11.98, 6.4], "green_cloth", "seal_mat")
box([10.9, 11.98, 6.7], [12.5, 12.19, 8.1], "wax", "wax_pad")
box([11.5, 11.98, 3.4], [13.5, 12.35, 5.4], "brass", "seal_stamp_base")
box([12.15, 12.35, 4.05], [12.85, 13.1, 4.75], "oak_dark", "seal_stamp_handle")
box([11.95, 13.1, 3.85], [13.05, 13.55, 4.95], "brass", "seal_stamp_cap")
# Square blue-black inkwell with a recessed visible pool and a gold neck.
box([11.1, 11.85, 10.2], [13.9, 12.05, 13], "brass", "inkwell_foot")
box([11.4, 12.05, 10.5], [13.6, 13.25, 12.7], "ink_glass", "inkwell_body")
box([11.65, 13.25, 10.75], [13.35, 13.5, 12.45], "brass", "inkwell_neck")
box([11.9, 13.49, 11], [13.1, 13.53, 12.2], "ink", "visible_ink_pool")
# An ivory quill rises within this block's bounds; stepped native boxes form
# the feather silhouette instead of rendering an opaque crossed sprite.
rot = {"origin": [12.4, 13.25, 11.6], "axis": "z", "angle": -22.5}
box([12.28, 13.3, 11.48], [12.48, 15.8, 11.68], "brass", "quill_shaft", rotation=rot)
box([11.85, 14, 11.49], [12.8, 15.25, 11.66], "feather", "quill_feather_middle", rotation=rot)
box([12.02, 15.25, 11.49], [12.68, 15.65, 11.66], "feather", "quill_feather_tip", rotation=rot)
box([12.08, 13.8, 11.49], [12.65, 14, 11.66], "feather", "quill_feather_base", rotation=rot)

model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
         "ambientocclusion": True, "textures": textures, "elements": elements,
         "display": {
             "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [.72, .72, .72]},
             "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.35, .35, .35]},
             "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.65, .65, .65]},
             "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.45, .45, .45]},
             "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.35, .35, .35]},
         }}
write_json(ASSETS / "models/block/writing_desk.json", model)
write_json(ASSETS / "models/item/writing_desk.json", {"parent": "realmidentity:block/writing_desk"})
write_json(ASSETS / "models/item/identity_document.json", {
    "parent": "minecraft:item/generated", "textures": {"layer0": "realmidentity:item/identity_document"}})
write_json(ASSETS / "blockstates/writing_desk.json", {"variants": {
    "facing=" + direction: {"model": "realmidentity:block/writing_desk", **({"y": index * 90} if index else {})}
    for index, direction in enumerate(("north", "east", "south", "west"))}})
write_json(RES / "data/realmidentity/recipe/writing_desk.json", {
    "type": "minecraft:crafting_shaped", "category": "decorations", "pattern": ["PFI", "BWB", "WWW"],
    "key": {"P": {"item": "minecraft:paper"}, "F": {"item": "minecraft:feather"},
            "I": {"item": "minecraft:ink_sac"}, "B": {"item": "minecraft:book"},
            "W": {"tag": "minecraft:planks"}},
    "result": {"id": "realmidentity:writing_desk", "count": 1}})
write_json(RES / "data/realmidentity/loot_table/blocks/writing_desk.json", {
    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": "realmidentity:writing_desk"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
print(f"Generated {len(elements)} native elements, 11 block textures and 1 document texture.")
