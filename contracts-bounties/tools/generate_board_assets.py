#!/usr/bin/env python3
"""Generate original pixel art and multipart noticeboards. Requires Pillow.

All coordinates describe a north-facing board. Multipart geometry is split at
block boundaries; item models retain the complete, assembled board.
"""
from __future__ import annotations

import copy
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/contracts"
TEXTURES = ASSETS / "textures/block"
RNG = random.Random(26831)


def save_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def texture(name, image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURES / f"{name}.png")


def make_textures():
    # The slightly irregular grain and small, interrupted highlights are drawn
    # directly at pixel resolution, rather than sampled from photographs.
    im = Image.new("RGB", (32, 32), "#956d43")
    d = ImageDraw.Draw(im)
    for x in range(32):
        col = ["#966f46", "#9d754a", "#8e653f", "#a17a4e"][x // 3 % 4]
        d.line((x, 0, x, 31), fill=col)
    for _ in range(42):
        x, y = RNG.randrange(2, 30), RNG.randrange(32)
        d.line((x, y, x, min(31, y + RNG.randrange(2, 9))),
               fill=RNG.choice(["#aa8255", "#805a37", "#b18b5b"]))
    d.line((0, 0, 0, 31), fill="#c29a66")
    d.line((1, 0, 1, 31), fill="#aa8254")
    d.line((30, 0, 30, 31), fill="#785331")
    d.line((31, 0, 31, 31), fill="#62442c")
    d.rectangle((12, 18, 16, 23), fill="#7f5937")
    d.rectangle((13, 19, 15, 22), fill="#a17a4e")
    texture("noticeboard_beam", im)
    texture("noticeboard_rail", im.transpose(Image.Transpose.ROTATE_90))

    im = Image.new("RGB", (32, 32), "#a17b4d")
    d = ImageDraw.Draw(im)
    for inset, color in [(1, "#bc9560"), (4, "#88613d"),
                         (6, "#b28a57"), (10, "#8a643f"),
                         (12, "#b18a58")]:
        d.rectangle((inset, inset, 31-inset, 31-inset), outline=color)
    texture("noticeboard_endgrain", im)

    for variant in range(3):
        im = Image.new("RGB", (32, 16), ["#896744", "#92704b", "#81603e"][variant])
        d = ImageDraw.Draw(im)
        d.line((0, 0, 31, 0), fill="#b08b5c")
        d.line((0, 1, 31, 1), fill="#9d794f")
        d.line((0, 14, 31, 14), fill="#715033")
        d.line((0, 15, 31, 15), fill="#523b2a")
        for _ in range(17):
            x, y = RNG.randrange(1, 30), RNG.randrange(3, 13)
            d.line((x, y, min(30, x+RNG.randrange(2, 9)), y),
                   fill=RNG.choice(["#a47e50", "#9a754b", "#795735"]))
        # One quiet rectangular knot, as in hand-hewn Minecraft timber.
        k = 6 + variant * 7
        d.rectangle((k, 6, k+3, 9), fill="#6b4b30")
        d.line((k, 5, k+3, 5), fill="#ae8555")
        texture(f"noticeboard_plank_{variant}", im)

    im = Image.new("RGB", (32, 32), "#61462f")
    d = ImageDraw.Draw(im)
    for y in range(0, 32, 8):
        d.line((0, y, 31, y), fill="#412e22")
        d.line((0, y+1, 31, y+1), fill="#77593a")
    texture("noticeboard_back", im)

    im = Image.new("RGB", (64, 32), "#8d3528")
    d = ImageDraw.Draw(im)
    for row in range(4):
        for column in range(-1, 9):
            x = column * 8 + (row % 2) * 4
            y = row * 8
            base = RNG.choice(["#ce6238", "#c45932", "#d66c3c", "#cb5c33"])
            d.rectangle((x+1, y+1, x+7, y+6), fill=base)
            d.line((x+1, y+1, x+7, y+1), fill="#ed9453")
            d.line((x+1, y+2, x+1, y+5), fill="#dc7946")
            d.line((x+2, y+6, x+7, y+6), fill="#aa442c")
            d.line((x+2, y+7, x+7, y+7), fill="#763026")
    texture("noticeboard_tiles", im)
    im = Image.new("RGB", (32, 16), "#aa4930")
    d = ImageDraw.Draw(im)
    for x in range(0, 32, 8):
        d.rectangle((x+1, 2, x+7, 12), fill="#c36038")
        d.line((x+1, 2, x+7, 2), fill="#df8951")
        d.line((x+1, 13, x+7, 13), fill="#823525")
    texture("noticeboard_roof_edge", im)

    for n in range(5):
        im = Image.new("RGB", (24, 32), ["#f5e7bd", "#f2dfb2", "#f8eac5", "#ebd2a2", "#f4e5bf"][n])
        d = ImageDraw.Draw(im)
        d.line((0, 0, 23, 0), fill="#fff5d8")
        d.line((0, 0, 0, 31), fill="#fff2ce")
        d.line((23, 1, 23, 31), fill="#c9ad7c")
        d.line((1, 31, 23, 31), fill="#c1a175")
        ink = ["#ad8b5d", "#b39060", "#ac8557"][n % 3]
        d.rectangle((4, 5, 18, 8), fill=ink)
        for row, width in [(12, 16), (17, 13), (22, 16)]:
            d.rectangle((4, row, min(19, 3+width-(n % 2)*2), row+2), fill="#c5a779")
        if n in (1, 4):
            d.rectangle((7, 25, 16, 29), fill="#ba9869")
            d.rectangle((9, 26, 14, 28), fill="#ebd8ae")
        else:
            d.rectangle((4, 27, 12, 28), fill="#c2a16e")
        texture(f"noticeboard_paper_{n}", im.resize((32, 32), Image.Resampling.NEAREST))

    im = Image.new("RGB", (16, 16), "#c6a372")
    d = ImageDraw.Draw(im)
    d.line((0, 0, 15, 0), fill="#e0c293")
    d.line((0, 0, 0, 15), fill="#d9b988")
    d.line((0, 15, 15, 15), fill="#ad895d")
    texture("noticeboard_paper_back", im)

    im = Image.new("RGB", (8, 8), "#62665e")
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 7, 1), fill="#a9b4a4")
    d.rectangle((0, 0, 1, 6), fill="#89998f")
    d.rectangle((6, 1, 7, 7), fill="#4c514b")
    d.line((1, 7, 6, 7), fill="#40443d")
    texture("noticeboard_nail", im.resize((16, 16), Image.Resampling.NEAREST))

    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    d.rectangle((7, 0, 8, 15), fill="#647441")
    for x, y, w, h in [(1, 2, 7, 5), (8, 6, 7, 5), (2, 11, 6, 4)]:
        d.rectangle((x+1, y, x+w-2, y+h-1), fill="#4f7e35")
        d.rectangle((x, y+1, x+w-1, y+h-2), fill="#58943a")
        d.rectangle((x+1, y, x+w-2, y+1), fill="#7ca944")
        d.rectangle((x+1, y+1, x+2, y+h-2), fill="#75a942")
        d.point((x+w-2, y+h-2), fill="#39642b")
    texture("noticeboard_ivy", im)


TEXTURE_KEYS = {
    "beam": "noticeboard_beam", "rail": "noticeboard_rail",
    "end": "noticeboard_endgrain", "back": "noticeboard_back",
    "roof": "noticeboard_tiles", "roof_edge": "noticeboard_roof_edge",
    "paper_back": "noticeboard_paper_back", "nail": "noticeboard_nail",
    "ivy": "noticeboard_ivy",
    **{f"plank{i}": f"noticeboard_plank_{i}" for i in range(3)},
    **{f"paper{i}": f"noticeboard_paper_{i}" for i in range(5)},
}


def cuboid(lo, hi, tex="beam", *, front=None, rotation=None, ends=False, shade=True):
    faces = {face: {"uv": [0, 0, 16, 16], "texture": f"#{tex}"}
             for face in ("north", "south", "east", "west", "up", "down")}
    if ends:
        for face in ("up", "down"):
            faces[face]["texture"] = "#end"
    if front:
        faces["north"]["texture"] = f"#{front}"
    obj = {"from": list(lo), "to": list(hi), "faces": faces}
    if rotation:
        origin, axis, angle = rotation
        obj["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle, "rescale": False}
    if not shade:
        obj["shade"] = False
    return obj


def paper(elements, x, y, width, height, variant, *, angle=0, backing=False, z=6.69):
    center = [x+width/2, y+height/2, z]
    rot = (center, "z", angle) if angle else None
    if backing:
        elements.append(cuboid((x-.35, y-.25, z+.15), (x+width+.35, y+height+.25, z+.27),
                               "paper_back", rotation=(center, "z", 22.5 if angle <= 0 else -22.5)))
    elements.append(cuboid((x, y, z), (x+width, y+height, z+.1), f"paper{variant}", rotation=rot))
    # Every paper is physically pinned through its upper edge, not painted onto
    # a wood texture. Matching rotation keeps its pin attached to the paper.
    nail_x, nail_y = x+width-.65, y+height-.6
    elements.append(cuboid((nail_x, nail_y, z-.14), (nail_x+.42, nail_y+.42, z+.015), "nail", rotation=rot))


def ivy(elements, x, y, z=6.82, width=2.3, height=3):
    el = cuboid((x, y, z), (x+width, y+height, z+.035), "ivy", shade=False)
    el["faces"] = {k: v for k, v in el["faces"].items() if k in ("north", "south")}
    elements.append(el)


def panel(elements, left, right, bottom, top):
    elements.append(cuboid((left, bottom, 7.1), (right, top, 8.6), "back"))
    # Separate boards have visible recessed horizontal joints.
    count = round((top-bottom)/3.5)
    step = (top-bottom)/count
    for i in range(count):
        y = bottom+i*step
        elements.append(cuboid((left+.12, y+.12, 6.95), (right-.12, y+step-.1, 8.73), f"plank{i%3}"))
    elements.append(cuboid((left-.24, bottom-.16, 6.8), (right+.24, bottom+.46, 8.94), "rail"))
    elements.append(cuboid((left-.24, top-.45, 6.8), (right+.24, top+.18, 8.94), "rail"))
    elements.append(cuboid((left-.25, bottom, 6.8), (left+.3, top, 8.95), "beam"))
    elements.append(cuboid((right-.3, bottom, 6.8), (right+.25, top, 8.95), "beam"))


def roof(elements, width):
    center = [width/2, 29.9, 8]
    # Two true pitched slopes, with a substantial terracotta edge and an oak
    # fascia beneath each eave. A continuous capped ridge closes the roof.
    for front, z0, z1, angle in [(True, .15, 8.02, -22.5), (False, 7.98, 15.85, 22.5)]:
        rot = (center, "x", angle)
        elements.append(cuboid((0, 29.68, z0), (width, 30.23, z1), "roof", rotation=rot))
        eave0, eave1 = (z0, z0+.6) if front else (z1-.6, z1)
        elements.append(cuboid((0, 29.16, eave0), (width, 29.73, eave1), "rail", rotation=rot))
        elements.append(cuboid((0, 29.65, eave0-.04), (width, 30.22, eave1+.04), "roof_edge", rotation=rot))
        # Raised tile courses provide geometry at the silhouette as well as
        # original tile texture; offset joins keep the little roof hand-built.
        for row in range(3):
            zz = z0 + (z1-z0) * (row+1) / 4
            elements.append(cuboid((0, 30.22, zz-.095), (width, 30.34, zz+.095), "roof_edge", rotation=rot))
    elements.append(cuboid((-.0, 29.96, 7.45), (width, 30.67, 8.55), "roof_edge"))
    # Oak verge trims make the roof readable from the sides and the back.
    for x in (0, width-.55):
        for z0, z1, angle in [(.05, 8.05, -22.5), (7.95, 15.95, 22.5)]:
            elements.append(cuboid((x, 29.43, z0), (x+.55, 29.89, z1), "rail", rotation=(center, "x", angle)))


def small_board():
    el = [cuboid((7, 0, 7), (9, 9.5, 9), "beam", ends=True)]
    panel(el, 1.15, 14.85, 6.35, 15.45)
    paper(el, 3.15, 9.3, 3.4, 5.2, 0, backing=True)
    paper(el, 9.2, 7.95, 3.65, 5.45, 1, angle=-22.5)
    paper(el, 6.9, 7.0, 1.95, 2.8, 2, angle=22.5, z=6.48)
    ivy(el, 6.15, 1.0, width=3.4, height=4.2)
    ivy(el, 12.9, 11.45, width=2.4, height=3.8)
    return el


def tall_board():
    el = []
    for x in (1.0, 13.0):
        el.append(cuboid((x, 0, 7), (x+2, 29.75, 9.15), "beam", ends=True))
        el.append(cuboid((x-.15, .2, 6.85), (x+2.15, .85, 9.3), "rail"))
    el.append(cuboid((2.7, 4.25, 7.3), (13.3, 5.0, 8.7), "rail"))
    panel(el, 2.7, 13.3, 8.1, 26.3)
    paper(el, 4.0, 19.7, 3.6, 5.1, 0, backing=True)
    paper(el, 8.7, 17.4, 2.9, 4.25, 3, angle=22.5)
    paper(el, 4.25, 10.15, 2.7, 4.15, 2, angle=22.5)
    paper(el, 8.15, 9.15, 3.65, 5.1, 1, angle=-22.5)
    ivy(el, .3, 1.6, width=3.4, height=4.5)
    ivy(el, 1.0, 5.45, width=2.3, height=3.2)
    ivy(el, 12.45, 11.6, width=3.1, height=4.25)
    ivy(el, 13.0, 21.0, width=2.65, height=4.3)
    roof(el, 16)
    return el


def wide_board():
    el = []
    for x in (1., 29.):
        el.append(cuboid((x, 0, 7), (x+2, 29.75, 9.15), "beam", ends=True))
        el.append(cuboid((x-.15, .2, 6.85), (x+2.15, .85, 9.3), "rail"))
    el.append(cuboid((2.7, 4.25, 7.3), (29.3, 5., 8.7), "rail"))
    panel(el, 2.7, 29.3, 8.1, 26.3)
    paper(el, 5.1, 19.2, 4., 5.8, 0)
    paper(el, 11.0, 17.2, 3.0, 4.2, 3, angle=22.5, backing=True)
    paper(el, 19.3, 20., 3.7, 5.2, 3, angle=-22.5, backing=True)
    paper(el, 24.8, 17.6, 3.15, 4.8, 2)
    paper(el, 5.0, 10.15, 2.8, 4.05, 2, angle=22.5)
    paper(el, 9.15, 9.1, 3.3, 4.4, 4)
    paper(el, 18.55, 10.4, 3.8, 5.0, 1, angle=-22.5)
    paper(el, 24.1, 9.25, 2.6, 3.7, 2, angle=22.5)
    ivy(el, .3, 1.6, width=3.4, height=4.5)
    ivy(el, 2.0, 5.45, width=3.3, height=3.2)
    ivy(el, 7.5, 3.5, width=3.1, height=3.3)
    ivy(el, 28.5, 11.25, width=3.1, height=4.3)
    ivy(el, 29.0, 21.0, width=2.65, height=4.3)
    roof(el, 32)
    return el


def clip_part(elements, col, row):
    result = []
    bounds = (col*16, row*16)
    for original in elements:
        lo, hi = original["from"], original["to"]
        if hi[0] <= bounds[0] or lo[0] >= bounds[0]+16 or hi[1] <= bounds[1] or lo[1] >= bounds[1]+16:
            continue
        el = copy.deepcopy(original)
        for axis, base in [(0, bounds[0]), (1, bounds[1])]:
            old0, old1 = el["from"][axis], el["to"][axis]
            el["from"][axis] = max(old0, base)-base
            el["to"][axis] = min(old1, base+16)-base
            if old0 < base or old1 > base+16:
                # Preserve the texture across a split, instead of stretching
                # its complete image over each piece of the same plank.
                low = (max(old0, base)-old0)/(old1-old0)*16
                high = (min(old1, base+16)-old0)/(old1-old0)*16
                for face, info in el["faces"].items():
                    uv = info["uv"]
                    if axis == 0 and face in ("north", "south", "up", "down"):
                        if face == "north":
                            uv[0], uv[2] = 16-high, 16-low
                        else:
                            uv[0], uv[2] = low, high
                    elif axis == 1 and face in ("north", "south", "east", "west"):
                        uv[1], uv[3] = 16-high, 16-low
        if "rotation" in el:
            el["rotation"]["origin"][0] -= bounds[0]
            el["rotation"]["origin"][1] -= bounds[1]
        result.append(el)
    return result


def displays(width, height):
    scale = .9 if height == 16 else .55
    # Transform the complete larger model around its own centre. Minecraft
    # applies model transforms about (8,8,8), so the offset must be compensated.
    cx, cy = width/2-8, height/2-8
    import math
    yaw, pitch = math.radians(-32), math.radians(18)
    rx, ry, rz = cx*math.cos(yaw), cy, -cx*math.sin(yaw)
    yy = ry*math.cos(pitch)-rz*math.sin(pitch)
    zz = ry*math.sin(pitch)+rz*math.cos(pitch)
    return {
        "gui": {"rotation": [18, -32, 0], "translation": [-rx*scale, -yy*scale, -zz*scale], "scale": [scale]*3},
        "ground": {"rotation": [0, 0, 0], "translation": [-cx*.3, 3-cy*.3, 0], "scale": [.3]*3},
        "fixed": {"rotation": [0, 180, 0], "translation": [cx*.5, -cy*.5, 0], "scale": [.5]*3},
        "thirdperson_righthand": {"rotation": [65, 0, 0], "translation": [-cx*.35, 2-cy*.35, 1], "scale": [.35]*3},
        "thirdperson_lefthand": {"rotation": [65, 0, 0], "translation": [-cx*.35, 2-cy*.35, 1], "scale": [.35]*3},
        "firstperson_righthand": {"rotation": [0, -25, 0], "translation": [-cx*.42, 1-cy*.42, 0], "scale": [.42]*3},
        "firstperson_lefthand": {"rotation": [0, 25, 0], "translation": [-cx*.42, 1-cy*.42, 0], "scale": [.42]*3},
    }


def model(elements, display=None):
    result = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": True,
        "textures": {"particle": "contracts:block/noticeboard_plank_0",
                     **{key: f"contracts:block/{value}" for key, value in TEXTURE_KEYS.items()}},
        "elements": elements,
    }
    if display:
        result["display"] = display
    return result


PARTS = {"lower_left": (0, 0), "lower_right": (1, 0), "upper_left": (0, 1), "upper_right": (1, 1)}


def generate_board(name, elements, width, height):
    variants = {}
    actual = {}
    for part, (col, row) in PARTS.items():
        real_col = col if width == 32 else 0
        real_row = row if height == 32 else 0
        real_part = next(k for k, v in PARTS.items() if v == (real_col, real_row))
        actual[part] = real_part
        if part != real_part:
            continue
        part_name = f"{name}_{part}"
        save_json(ASSETS / f"models/block/{part_name}.json", model(clip_part(elements, col, row)))
    for facing, degrees in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        for part in PARTS:
            variants[f"facing={facing},part={part}"] = {"model": f"contracts:block/{name}_{actual[part]}"}
            if degrees:
                variants[f"facing={facing},part={part}"]["y"] = degrees
    save_json(ASSETS / f"blockstates/{name}.json", {"variants": variants})
    save_json(ASSETS / f"models/block/{name}.json", model(elements))
    save_json(ASSETS / f"models/item/{name}.json", {
        "parent": f"contracts:block/{name}", "display": displays(width, height), "gui_light": "front"
    })
    save_json(ROOT / f"data/contracts/loot_table/blocks/{name}.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"contracts:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}, {
            "condition": "minecraft:block_state_property", "block": f"contracts:{name}",
            "properties": {"part": "lower_left"}}]}]
    })


def recipes():
    save_json(ROOT / "data/contracts/recipe/tall_contract_board.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["BBB", "PMP", "L L"], "key": {
            "B": {"item": "minecraft:brick_slab"}, "P": {"item": "minecraft:paper"},
            "M": {"item": "contracts:contract_board"}, "L": {"item": "minecraft:oak_log"}},
        "result": {"id": "contracts:tall_contract_board", "count": 1}})
    save_json(ROOT / "data/contracts/recipe/wide_contract_board.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["MM"], "key": {"M": {"item": "contracts:tall_contract_board"}},
        "result": {"id": "contracts:wide_contract_board", "count": 1}})
    tag_path = ROOT / "data/minecraft/tags/block/mineable/axe.json"
    tag = json.loads(tag_path.read_text()) if tag_path.exists() else {"replace": False, "values": []}
    for name in ("contract_board", "tall_contract_board", "wide_contract_board"):
        ident = f"contracts:{name}"
        if ident not in tag["values"]:
            tag["values"].append(ident)
    save_json(tag_path, tag)


if __name__ == "__main__":
    make_textures()
    generate_board("contract_board", small_board(), 16, 16)
    generate_board("tall_contract_board", tall_board(), 16, 32)
    generate_board("wide_contract_board", wide_board(), 32, 32)
    recipes()
    print("Generated 3 original noticeboards, 7 placed parts, item models, textures, recipes and loot.")
