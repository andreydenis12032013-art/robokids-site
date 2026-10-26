#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Make a 100×100 tile scrambled PNG and its compact reversible PuzzleCode.
Requires Pillow: python -m pip install Pillow

Example:
 python tools/generate_puzzle.py original.png scrambled.png 13579BDF

To recover the picture, the Android application uses the seed permutation;
neither the Android application nor the code store the original image.
"""
import sys
import zlib
from pathlib import Path
from PIL import Image


def destinations_for_seed(grid, seed):
    if not (2 <= grid <= 100 and 0 < seed <= 0xFFFFFFFF):
        raise ValueError("Invalid grid or seed")
    perm = list(range(grid * grid))
    state = seed
    for i in range(len(perm) - 1, 0, -1):
        state ^= (state << 13) & 0xFFFFFFFF
        state &= 0xFFFFFFFF
        state ^= state >> 17
        state &= 0xFFFFFFFF
        state ^= (state << 5) & 0xFFFFFFFF
        state &= 0xFFFFFFFF
        j = (state * (i + 1)) >> 32
        perm[i], perm[j] = perm[j], perm[i]
    return perm


def make(source, output, seed):
    grid = 100
    picture = Image.open(source).convert("RGB")
    width, height = picture.size
    if width % grid or height % grid:
        raise ValueError("Image width and height must each be divisible by 100.")
    tw, th = width // grid, height // grid
    mapping = destinations_for_seed(grid, seed)
    shuffled = Image.new("RGB", (width, height))
    for initial, final in enumerate(mapping):
        oldx = (final % grid) * tw
        oldy = (final // grid) * th
        newx = (initial % grid) * tw
        newy = (initial // grid) * th
        shuffled.paste(
            picture.crop((oldx, oldy, oldx + tw, oldy + th)),
            (newx, newy),
        )
    shuffled.save(output, format="PNG")
    crc = f"{zlib.crc32(Path(output).read_bytes()):08X}"
    return f"PZ2.100.{seed:08X}.{crc}"


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: python generate_puzzle.py ORIGINAL.png SHUFFLED.png SEED_HEX")
    seed = int(sys.argv[3], 16)
    print(make(sys.argv[1], sys.argv[2], seed))
