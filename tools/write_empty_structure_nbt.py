"""Write a minimal, all-air vanilla structure (.nbt) file with a given size — the same shape as
src/test/resources/data/gtrift/structures/empty.nbt (size [1,1,1], zero blocks, zero palette
entries), just with a caller-chosen bounding box. Useful for GameTest structures that only need to
claim grid space (see GameTestBatchRunner.createStructuresForBatch — a test's own structure size
directly determines how far the next test in the grid gets pushed away), not real block content.

Companion to tools/read_structure_nbt.py — same minimal from-scratch approach, no external
dependencies. Confirmed against empty.nbt's own real key set/order (size, entities, blocks, palette,
DataVersion) via read_structure_nbt.py before writing this, not assumed.

Usage: python tools/write_empty_structure_nbt.py <out-path.nbt> <size-x> <size-y> <size-z>
"""

import gzip
import struct
import sys

TAG_END, TAG_INT, TAG_LIST, TAG_COMPOUND = 0, 3, 9, 10


class Writer:
    def __init__(self):
        self.buf = bytearray()

    def u8(self, v):
        self.buf.append(v)

    def i16(self, v):
        self.buf += struct.pack(">h", v)

    def i32(self, v):
        self.buf += struct.pack(">i", v)

    def name(self, s):
        encoded = s.encode("utf-8")
        self.i16(len(encoded))
        self.buf += encoded

    def tag_int(self, name, value):
        self.u8(TAG_INT)
        self.name(name)
        self.i32(value)

    def tag_int_list(self, name, values):
        self.u8(TAG_LIST)
        self.name(name)
        self.u8(TAG_INT)
        self.i32(len(values))
        for v in values:
            self.i32(v)

    def tag_empty_list(self, name):
        # Real Mojang NBT writers use TAG_END as the element type for a genuinely empty list —
        # matches standard convention, and the count (0) is all that matters on read regardless.
        self.u8(TAG_LIST)
        self.name(name)
        self.u8(TAG_END)
        self.i32(0)

    def end(self):
        self.u8(TAG_END)


def build(size_x, size_y, size_z, data_version):
    w = Writer()
    w.u8(TAG_COMPOUND)
    w.name("")  # root name, conventionally empty — matches empty.nbt
    w.tag_int_list("size", [size_x, size_y, size_z])
    w.tag_empty_list("entities")
    w.tag_empty_list("blocks")
    w.tag_empty_list("palette")
    w.tag_int("DataVersion", data_version)
    w.end()
    return bytes(w.buf)


def main():
    if len(sys.argv) != 5:
        print("usage: python write_empty_structure_nbt.py <out-path.nbt> <size-x> <size-y> <size-z>")
        sys.exit(1)

    out_path, sx, sy, sz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
    data = build(sx, sy, sz, data_version=3465)  # matches empty.nbt's own DataVersion
    with gzip.open(out_path, "wb") as f:
        f.write(data)
    print("wrote %s (size [%d, %d, %d])" % (out_path, sx, sy, sz))


if __name__ == "__main__":
    main()
