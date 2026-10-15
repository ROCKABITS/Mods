#!/usr/bin/env python3
"""Render the real Minecraft JSON board models for a quick, offline art review.

This is an orthographic model preview, not a screenshot from Minecraft. Textures
are sampled with nearest-neighbour filtering, and standard cuboid element
rotations and face UVs are honoured. Requires Pillow and numpy.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"


def resource_path(identifier: str, category: str, suffix: str) -> Path:
    namespace, location = identifier.split(":", 1) if ":" in identifier else ("minecraft", identifier)
    return RESOURCES / "assets" / namespace / category / (location + suffix)


def load_model(identifier: str, parents: tuple[str, ...] = ()) -> dict:
    if identifier in parents:
        raise ValueError(f"Cyclic model inheritance: {parents!r} -> {identifier}")
    path = resource_path(identifier, "models", ".json")
    if not path.exists() and identifier == "minecraft:block/block":
        # Vanilla's root only supplies display transforms and GUI lighting.
        return {}
    if not path.exists() and identifier in {"minecraft:block/cube", "minecraft:block/cube_all"}:
        faces = {side: {"texture": "#" + ("all" if identifier.endswith("_all") else side)}
                 for side in ("down", "up", "north", "south", "west", "east")}
        return {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}]}
    data = json.loads(path.read_text())
    inherited = load_model(data["parent"], parents + (identifier,)) if "parent" in data else {}
    textures = dict(inherited.get("textures", {}))
    textures.update(data.get("textures", {}))
    inherited.update(data)
    inherited["textures"] = textures
    return inherited


def texture_image(reference: str, textures: dict, cache: dict) -> np.ndarray:
    seen = set()
    while reference.startswith("#"):
        if reference in seen:
            raise ValueError(f"Cyclic texture alias: {reference}")
        seen.add(reference)
        reference = textures[reference[1:]]
    if reference not in cache:
        path = resource_path(reference, "textures", ".png")
        if not path.exists():
            raise FileNotFoundError(f"Missing texture required by preview: {path}")
        cache[reference] = np.asarray(Image.open(path).convert("RGBA"))
    return cache[reference]


def face_geometry(side: str, low: list, high: list) -> tuple[np.ndarray, np.ndarray, list]:
    x0, y0, z0 = low
    x1, y1, z1 = high
    # Vertices follow Minecraft UV order: top-left, top-right, bottom-right,
    # bottom-left when the face is viewed from outside the cuboid.
    faces = {
        "north": ([(x1,y1,z0),(x0,y1,z0),(x0,y0,z0),(x1,y0,z0)], (0,0,-1), [16-x1,16-y1,16-x0,16-y0]),
        "south": ([(x0,y1,z1),(x1,y1,z1),(x1,y0,z1),(x0,y0,z1)], (0,0,1), [x0,16-y1,x1,16-y0]),
        "east": ([(x1,y1,z1),(x1,y1,z0),(x1,y0,z0),(x1,y0,z1)], (1,0,0), [16-z1,16-y1,16-z0,16-y0]),
        "west": ([(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(x0,y0,z0)], (-1,0,0), [z0,16-y1,z1,16-y0]),
        "up": ([(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)], (0,1,0), [x0,z0,x1,z1]),
        "down": ([(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)], (0,-1,0), [x0,16-z1,x1,16-z0]),
    }
    vertices, normal, uv = faces[side]
    return np.array(vertices, dtype=float), np.array(normal, dtype=float), uv


def rotation_matrix(axis: str, angle: float) -> np.ndarray:
    radians = math.radians(angle)
    sine, cosine = math.sin(radians), math.cos(radians)
    if axis == "x":
        return np.array([[1,0,0],[0,cosine,-sine],[0,sine,cosine]])
    if axis == "y":
        return np.array([[cosine,0,sine],[0,1,0],[-sine,0,cosine]])
    if axis == "z":
        return np.array([[cosine,-sine,0],[sine,cosine,0],[0,0,1]])
    raise ValueError(axis)


def model_faces(identifier: str, offset: np.ndarray, textures: dict) -> list:
    model = load_model(identifier)
    result = []
    for element in model.get("elements", []):
        rotation = element.get("rotation")
        for side, face in element.get("faces", {}).items():
            vertices, normal, default_uv = face_geometry(side, element["from"], element["to"])
            if rotation:
                matrix = rotation_matrix(rotation["axis"], rotation["angle"])
                origin = np.array(rotation["origin"])
                vertices -= origin
                if rotation.get("rescale"):
                    scale = np.ones(3) / math.cos(math.radians(rotation["angle"]))
                    scale["xyz".index(rotation["axis"])] = 1
                    vertices *= scale
                vertices = vertices @ matrix.T + origin
                normal = normal @ matrix.T
            vertices += offset
            u0, v0, u1, v1 = face.get("uv", default_uv)
            uv = np.array([(u0,v0),(u1,v0),(u1,v1),(u0,v1)], dtype=float) / 16
            if face.get("rotation"):
                uv = np.roll(uv, -face["rotation"] // 90, axis=0)
            texture = texture_image(face["texture"], model.get("textures", {}), textures)
            result.append((vertices, normal, uv, texture, element.get("shade", True)))
    return result


def raster_triangle(canvas: np.ndarray, depth_buffer: np.ndarray, vertices: np.ndarray,
                    depths: np.ndarray, uvs: np.ndarray, texture: np.ndarray, light: float):
    height, width = depth_buffer.shape
    min_x = max(0, int(math.floor(vertices[:,0].min())))
    max_x = min(width-1, int(math.ceil(vertices[:,0].max())))
    min_y = max(0, int(math.floor(vertices[:,1].min())))
    max_y = min(height-1, int(math.ceil(vertices[:,1].max())))
    if min_x > max_x or min_y > max_y:
        return
    x0,y0 = vertices[0]
    x1,y1 = vertices[1]
    x2,y2 = vertices[2]
    denominator = (y1-y2)*(x0-x2) + (x2-x1)*(y0-y2)
    if abs(denominator) < 1e-8:
        return
    yy, xx = np.mgrid[min_y:max_y+1, min_x:max_x+1].astype(float)
    xx += .5
    yy += .5
    a = ((y1-y2)*(xx-x2)+(x2-x1)*(yy-y2))/denominator
    b = ((y2-y0)*(xx-x2)+(x0-x2)*(yy-y2))/denominator
    c = 1-a-b
    inside = (a >= -1e-8) & (b >= -1e-8) & (c >= -1e-8)
    z = a*depths[0]+b*depths[1]+c*depths[2]
    depths_region = depth_buffer[min_y:max_y+1, min_x:max_x+1]
    inside &= z > depths_region
    if not np.any(inside):
        return
    u = a*uvs[0,0]+b*uvs[1,0]+c*uvs[2,0]
    v = a*uvs[0,1]+b*uvs[1,1]+c*uvs[2,1]
    tex_height, tex_width = texture.shape[:2]
    tx = np.clip(np.floor(u*tex_width).astype(int), 0, tex_width-1)
    ty = np.clip(np.floor(v*tex_height).astype(int), 0, tex_height-1)
    sample = texture[ty,tx]
    # Model textures are cutout, matching the mod's RenderType.CUTOUT block path.
    inside &= sample[:,:,3] >= 128
    rgb = np.clip(sample[:,:,:3].astype(float) * light, 0, 255).astype(np.uint8)
    region = canvas[min_y:max_y+1, min_x:max_x+1]
    region[inside,:3] = rgb[inside]
    region[inside,3] = 255
    depths_region[inside] = z[inside]


def render(models: list[str], output: Path, width: int, height: int, back: bool, labels: bool):
    textures = {}
    meshes = []
    model_sizes = []
    for identifier in models:
        faces = model_faces(identifier, np.zeros(3), textures)
        vertices = np.concatenate([face[0] for face in faces])
        low, high = vertices.min(axis=0), vertices.max(axis=0)
        meshes.append(faces)
        model_sizes.append((low,high))
    # Make standing boards share the same floor, and leave a measured gap.
    offset_x = 0
    all_faces = []
    centres = []
    for faces, (low,high) in zip(meshes,model_sizes):
        board_width = high[0]-low[0]
        # North-facing models have their positive X on the viewer's left.
        # Keep the CLI order left-to-right in both front and back previews.
        board_x = offset_x-low[0] if back else -offset_x-high[0]
        offset = np.array([board_x,-low[1],0])
        all_faces.extend([(v+offset,n,u,t,s) for v,n,u,t,s in faces])
        centres.append(np.array([offset_x+board_width/2 if back else -offset_x-board_width/2,0,8]))
        offset_x += board_width+8
    camera_direction = np.array([.32,.22,-1.0]) if not back else np.array([-.32,.22,1.0])
    camera_direction /= np.linalg.norm(camera_direction)
    right = np.cross(np.array([0,1,0]),camera_direction)
    right /= np.linalg.norm(right)
    up = np.cross(camera_direction,right)
    all_vertices = np.concatenate([face[0] for face in all_faces])
    projected = np.stack([all_vertices@right, -all_vertices@up],axis=1)
    minimum, maximum = projected.min(axis=0), projected.max(axis=0)
    margin_x, margin_y = .065*width, .12*height
    scale = min((width-2*margin_x)/(maximum[0]-minimum[0]),
                (height-2*margin_y)/(maximum[1]-minimum[1]))
    centre = (minimum+maximum)/2
    screen_centre = np.array([width/2,height/2+height*.015])
    def project(vertices):
        return (np.stack([vertices@right,-vertices@up],axis=1)-centre)*scale+screen_centre
    background = np.empty((height,width,4),dtype=np.uint8)
    yy = np.linspace(0,1,height)[:,None]
    for channel, (top,bottom) in enumerate(((28,20),(32,24),(30,23))):
        background[:,:,channel] = (top+(bottom-top)*yy).astype(np.uint8)
    background[:,:,3] = 255
    shadow = Image.new("RGBA",(width,height),(0,0,0,0))
    draw = ImageDraw.Draw(shadow)
    for centre_3d,(low,high) in zip(centres,model_sizes):
        cx,cy = project(np.array([centre_3d]))[0]
        radius_x = (high[0]-low[0])*.58*scale
        radius_y = 1.9*scale
        draw.ellipse((cx-radius_x,cy-radius_y,cx+radius_x,cy+radius_y),fill=(0,0,0,95))
    background = np.array(Image.alpha_composite(Image.fromarray(background),shadow.filter(ImageFilter.GaussianBlur(13))))
    depth_buffer = np.full((height,width),-np.inf)
    light_direction = np.array([-.35,.8,-.7])
    light_direction /= np.linalg.norm(light_direction)
    if back:
        light_direction[2] *= -1
    for vertices,normal,uv,texture,shade in all_faces:
        if np.dot(normal,camera_direction) <= 1e-7:
            continue
        screen = project(vertices)
        depths = vertices@camera_direction
        light = .72+.28*max(0,np.dot(normal,light_direction)) if shade else 1.0
        for indices in ([0,1,2],[0,2,3]):
            raster_triangle(background,depth_buffer,screen[indices],depths[indices],uv[indices],texture,light)
    result = Image.fromarray(background)
    if labels:
        draw = ImageDraw.Draw(result)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",15)
        except OSError:
            font = ImageFont.load_default()
        draw.text((24,height-34),"Pré-visualização dos modelos 3D • Contracts & Bounties",fill=(155,166,158),font=font)
    output.parent.mkdir(parents=True,exist_ok=True)
    result.save(output)
    print(f"Rendered {len(models)} models / {len(all_faces)} textured faces to {output}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models",nargs="+",default=["contracts:item/tall_contract_board","contracts:item/contract_board","contracts:item/wide_contract_board"])
    parser.add_argument("--output",type=Path,default=Path("/workspace/outputs/contracts-bounties-boards-preview.png"))
    parser.add_argument("--width",type=int,default=1600)
    parser.add_argument("--height",type=int,default=1000)
    parser.add_argument("--back",action="store_true")
    parser.add_argument("--no-label",action="store_true")
    args = parser.parse_args()
    render(args.models,args.output,args.width,args.height,args.back,not args.no_label)


if __name__ == "__main__":
    main()
