"""Dump a vanilla structure (.nbt) file's size, block palette, and per-position blocks.

Useful for sanity-checking GameTest structure templates (src/test/resources/data/gtrift/structures/*.nbt)
without launching the game — e.g. confirming a captured multiblock's controller ends up where a test's
getBlockEntity(relativePos) call expects it. Structure files are gzip-compressed NBT; no external
dependencies needed, this is a minimal from-scratch reader covering the tag types structure files use.

Usage: python tools/read_structure_nbt.py path/to/structure.nbt
"""

import gzip
import struct
import sys

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, \
    TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def u8(self):
        v = self.data[self.pos]
        self.pos += 1
        return v

    def i16(self):
        v = struct.unpack_from(">h", self.data, self.pos)[0]
        self.pos += 2
        return v

    def i32(self):
        v = struct.unpack_from(">i", self.data, self.pos)[0]
        self.pos += 4
        return v

    def i64(self):
        v = struct.unpack_from(">q", self.data, self.pos)[0]
        self.pos += 8
        return v

    def f32(self):
        v = struct.unpack_from(">f", self.data, self.pos)[0]
        self.pos += 4
        return v

    def f64(self):
        v = struct.unpack_from(">d", self.data, self.pos)[0]
        self.pos += 8
        return v

    def bytes_n(self, n):
        v = self.data[self.pos:self.pos + n]
        self.pos += n
        return v

    def name(self):
        n = self.i16()
        return self.bytes_n(n).decode("utf-8")

    def payload(self, tag_type):
        if tag_type == TAG_END:
            return None
        if tag_type == TAG_BYTE:
            return self.u8()
        if tag_type == TAG_SHORT:
            return self.i16()
        if tag_type == TAG_INT:
            return self.i32()
        if tag_type == TAG_LONG:
            return self.i64()
        if tag_type == TAG_FLOAT:
            return self.f32()
        if tag_type == TAG_DOUBLE:
            return self.f64()
        if tag_type == TAG_BYTE_ARRAY:
            n = self.i32()
            return list(self.bytes_n(n))
        if tag_type == TAG_STRING:
            n = self.i16()
            return self.bytes_n(n).decode("utf-8")
        if tag_type == TAG_LIST:
            elem_type = self.u8()
            n = self.i32()
            return [self.payload(elem_type) for _ in range(n)]
        if tag_type == TAG_COMPOUND:
            d = {}
            while True:
                t = self.u8()
                if t == TAG_END:
                    break
                nm = self.name()
                d[nm] = self.payload(t)
            return d
        if tag_type == TAG_INT_ARRAY:
            n = self.i32()
            return [self.i32() for _ in range(n)]
        if tag_type == TAG_LONG_ARRAY:
            n = self.i32()
            return [self.i64() for _ in range(n)]
        raise ValueError("unknown tag type %d at pos %d" % (tag_type, self.pos))


def load(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    r = Reader(data)
    t = r.u8()
    assert t == TAG_COMPOUND, "root is not a compound tag: %d" % t
    r.name()  # root name, conventionally empty
    return r.payload(TAG_COMPOUND)


def main():
    if len(sys.argv) != 2:
        print("usage: python read_structure_nbt.py <path-to-structure.nbt>")
        sys.exit(1)

    root = load(sys.argv[1])
    print("size:", root.get("size"))
    print("DataVersion:", root.get("DataVersion"))

    palette = root.get("palette", [])
    print("palette (%d entries):" % len(palette))
    for i, p in enumerate(palette):
        extra = {k: v for k, v in p.items() if k != "Name"}
        print("  [%d] %s %s" % (i, p.get("Name"), extra))

    blocks = root.get("blocks", [])
    print("blocks (%d entries):" % len(blocks))
    for b in blocks:
        idx = b.get("state")
        pname = palette[idx]["Name"] if idx is not None and idx < len(palette) else "?"
        print("  pos=%s -> palette[%s]=%s" % (b.get("pos"), idx, pname))


if __name__ == "__main__":
    main()
