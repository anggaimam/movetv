#!/usr/bin/env python3
"""Converts Kenney's Starter-Kit-Racing GLB models into the small .mesh format Wiggle Play reads (T-16).

Usage:  python3 tools/glb_to_mesh.py <path to a clone of KenneyNL/Starter-Kit-Racing>

Writes (relative to the project root, which is the parent of this tools folder):
  app/src/main/assets/race/meshes/<name>.mesh   (10 files)
  app/src/main/assets/race/meshes/index.json
  app/src/main/assets/art/race/colormap.png     (the shared palette texture, copied unchanged)

The tool never runs inside the app and never downloads anything. Standard library + Pillow only.
Output is deterministic: running it twice gives byte-identical files.

.mesh format (little-endian):
  header  "MTVM", u16 version=1, u16 partCount, u8 doubleSided
  part    u16 nameLen + UTF-8 name, i16 parentIndex (-1 = root),
          f32[3] translation, f32[4] rotation (quaternion xyzw), f32[3] scale,
          u32 vertexCount, u32 indexCount,
          vertices: f32 px,py,pz, nx,ny,nz, u,v (32 bytes each, in the node's local space),
          indices: u16
"""
import json
import os
import shutil
import struct
import sys

MODELS = [
    "vehicle-truck-yellow", "vehicle-truck-red", "vehicle-truck-green", "vehicle-truck-purple",
]

COMPONENT = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
TYPE_COUNT = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


class StopAndReport(Exception):
    """A GLB breaks an assumption of ticket T-16; the builder must report instead of guessing."""


def read_glb(path):
    data = open(path, "rb").read()
    magic, version, length = struct.unpack("<4sII", data[:12])
    if magic != b"glTF" or version != 2:
        raise StopAndReport("%s: not a glTF 2 binary" % path)
    pos = 12
    gltf = None
    binary = None
    while pos < length:
        clen, ctype = struct.unpack("<I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + clen]
        if ctype == b"JSON":
            gltf = json.loads(chunk.decode("utf-8"))
        elif ctype == b"BIN\x00":
            binary = chunk
        pos += 8 + clen + (-clen % 4)
    if gltf is None or binary is None:
        raise StopAndReport("%s: missing JSON or BIN chunk" % path)
    return gltf, binary


def accessor(gltf, binary, index):
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    fmt, size = COMPONENT[acc["componentType"]]
    n = TYPE_COUNT[acc["type"]]
    stride = view.get("byteStride", size * n)
    base = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    out = []
    for i in range(acc["count"]):
        off = base + i * stride
        out.append(struct.unpack_from("<%d%s" % (n, fmt), binary, off))
    return out


def check_texture_rules(name, gltf):
    """Stop conditions of T-16: one texture, identity (or absent) KHR_texture_transform."""
    if len(gltf.get("textures", [])) != 1 or len(gltf.get("images", [])) != 1:
        raise StopAndReport("%s: expected exactly one texture, found %d textures / %d images"
                            % (name, len(gltf.get("textures", [])), len(gltf.get("images", []))))
    for m in gltf["materials"]:
        tex = m.get("pbrMetallicRoughness", {}).get("baseColorTexture", {})
        t = tex.get("extensions", {}).get("KHR_texture_transform")
        if t:
            meaningful = {k: v for k, v in t.items() if k != "texCoord"}
            if meaningful:
                raise StopAndReport("%s: KHR_texture_transform has values %s" % (name, meaningful))


def convert(name, path, is_truck):
    gltf, binary = read_glb(path)
    check_texture_rules(name, gltf)
    double_sided = all(m.get("doubleSided", False) for m in gltf["materials"])
    nodes = gltf["nodes"]
    scene_nodes = gltf["scenes"][gltf.get("scene", 0)]["nodes"]
    parts = []
    for ni in scene_nodes:
        node = nodes[ni]
        if "mesh" not in node:
            raise StopAndReport("%s: node %s has no mesh" % (name, node.get("name")))
        if "children" in node:
            raise StopAndReport("%s: node %s has children" % (name, node.get("name")))
        rot = node.get("rotation", [0.0, 0.0, 0.0, 1.0])
        scl = node.get("scale", [1.0, 1.0, 1.0])
        if is_truck and (any(abs(a - b) > 1e-6 for a, b in zip(rot, [0, 0, 0, 1])) or
                         any(abs(a - 1.0) > 1e-6 for a in scl)):
            raise StopAndReport("%s: truck node %s has rotation/scale %s %s" % (name, node.get("name"), rot, scl))
        prims = gltf["meshes"][node["mesh"]]["primitives"]
        if len(prims) != 1:
            raise StopAndReport("%s: node %s has %d primitives" % (name, node.get("name"), len(prims)))
        p = prims[0]
        if p.get("mode", 4) != 4:
            raise StopAndReport("%s: node %s is not a triangle list" % (name, node.get("name")))
        pos = accessor(gltf, binary, p["attributes"]["POSITION"])
        nor = accessor(gltf, binary, p["attributes"]["NORMAL"])
        uv = accessor(gltf, binary, p["attributes"]["TEXCOORD_0"])
        idx = [i[0] for i in accessor(gltf, binary, p["indices"])]
        if len(pos) > 65535:
            raise StopAndReport("%s: node %s has %d vertices (more than 65535)" % (name, node.get("name"), len(pos)))
        parts.append({
            "name": node.get("name", "part%d" % len(parts)),
            "translation": [float(v) for v in node.get("translation", [0.0, 0.0, 0.0])],
            "rotation": [float(v) for v in rot],
            "scale": [float(v) for v in scl],
            "pos": pos, "nor": nor, "uv": uv, "idx": idx,
        })
    return double_sided, parts


def write_mesh(path, double_sided, parts):
    out = bytearray()
    out += b"MTVM"
    out += struct.pack("<HHB", 1, len(parts), 1 if double_sided else 0)
    for part in parts:
        nb = part["name"].encode("utf-8")
        out += struct.pack("<H", len(nb)) + nb
        out += struct.pack("<h", -1)
        out += struct.pack("<3f", *part["translation"])
        out += struct.pack("<4f", *part["rotation"])
        out += struct.pack("<3f", *part["scale"])
        out += struct.pack("<II", len(part["pos"]), len(part["idx"]))
        for p, n, t in zip(part["pos"], part["nor"], part["uv"]):
            out += struct.pack("<8f", p[0], p[1], p[2], n[0], n[1], n[2], t[0], t[1])
        out += struct.pack("<%dH" % len(part["idx"]), *part["idx"])
    with open(path, "wb") as f:
        f.write(out)


def quat_rotate(q, v):
    x, y, z, w = q
    # v' = v + 2w(q x v) + 2 q x (q x v)
    cx = y * v[2] - z * v[1]
    cy = z * v[0] - x * v[2]
    cz = x * v[1] - y * v[0]
    dx = y * cz - z * cy
    dy = z * cx - x * cz
    dz = x * cy - y * cx
    return (v[0] + 2 * (w * cx + dx), v[1] + 2 * (w * cy + dy), v[2] + 2 * (w * cz + dz))


def bounds(parts):
    lo = [1e30] * 3
    hi = [-1e30] * 3
    for part in parts:
        for p in part["pos"]:
            v = (p[0] * part["scale"][0], p[1] * part["scale"][1], p[2] * part["scale"][2])
            v = quat_rotate(part["rotation"], v)
            v = (v[0] + part["translation"][0], v[1] + part["translation"][1], v[2] + part["translation"][2])
            for a in range(3):
                lo[a] = min(lo[a], v[a])
                hi[a] = max(hi[a], v[a])
    return [round(x, 4) for x in lo], [round(x, 4) for x in hi]


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    src = os.path.abspath(sys.argv[1])
    models_dir = os.path.join(src, "models")
    if not os.path.isdir(models_dir):
        print("No models/ folder in %s: pass a clone of KenneyNL/Starter-Kit-Racing" % src)
        return 2
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    mesh_dir = os.path.join(root, "app", "src", "main", "assets", "race", "meshes")
    art_dir = os.path.join(root, "app", "src", "main", "assets", "art", "race")
    os.makedirs(mesh_dir, exist_ok=True)
    os.makedirs(art_dir, exist_ok=True)

    index = []
    print("%-24s %5s %9s  %s" % ("name", "parts", "triangles", "bounds (min .. max)"))
    try:
        for name in MODELS:
            double_sided, parts = convert(name, os.path.join(models_dir, name + ".glb"), name.startswith("vehicle-"))
            write_mesh(os.path.join(mesh_dir, name + ".mesh"), double_sided, parts)
            tris = sum(len(p["idx"]) // 3 for p in parts)
            lo, hi = bounds(parts)
            index.append({"name": name, "parts": [p["name"] for p in parts], "triangles": tris,
                          "bounds": {"min": lo, "max": hi}, "doubleSided": double_sided})
            print("%-24s %5d %9d  %s .. %s" % (name, len(parts), tris, lo, hi))
    except StopAndReport as e:
        print("STOP AND REPORT: %s" % e)
        return 1
    with open(os.path.join(mesh_dir, "index.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(index, f, indent=1, sort_keys=True)
        f.write("\n")

    # The palette texture is copied byte for byte (no re-encoding), after a size check.
    cm = os.path.join(models_dir, "Textures", "colormap.png")
    from PIL import Image
    with Image.open(cm) as im:
        if im.size != (512, 512):
            print("STOP AND REPORT: colormap.png is %s, expected 512x512" % (im.size,))
            return 1
    shutil.copyfile(cm, os.path.join(art_dir, "colormap.png"))
    print("colormap.png copied (512x512)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
