package ru.sector9.puzzlecode;

import java.util.Base64;
import java.util.Locale;

/**
 * Immutable puzzle-code utilities. No Android imports: can be unit-tested on a PC.
 *
 * PZ1.4.<base64url sixteen destination bytes>.<CRC32 of scrambled PNG>
 * PZ2.100.<8 hex digits PRNG seed>.<CRC32 of scrambled PNG>
 *
 * A destination permutation maps each INITIAL position to a FINAL position.
 * It describes the shuffled PNG, rather than containing or reconstructing
 * any hidden image content. PNG bytes themselves are only on the user's phone.
 */
public final class PuzzleCodec {
    private PuzzleCodec() {}

    public static int[] decode(String raw, int grid, String imageCrc) {
        if (raw == null || imageCrc == null) throw new IllegalArgumentException("Сначала загрузи PNG.");
        String[] part = raw.replaceAll("\\s+", "").split("\\.", -1);
        if (part.length != 4) throw new IllegalArgumentException("Неверный формат кода.");
        if (!part[3].equalsIgnoreCase(imageCrc))
            throw new IllegalArgumentException("Код принадлежит другому PNG. Скачай исходный файл без изменений.");
        if (grid == 4 && part[0].equals("PZ1") && part[1].equals("4")) {
            byte[] bytes;
            try { bytes = Base64.getUrlDecoder().decode(part[2]); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Повреждён код 4×4."); }
            if (bytes.length != 16) throw new IllegalArgumentException("Требуется 16 позиций.");
            int[] permutation = new int[16];
            for (int i=0; i<16; i++) permutation[i] = bytes[i] & 255;
            validate(permutation);
            return permutation;
        }
        if (grid == 100 && part[0].equals("PZ2") && part[1].equals("100")) {
            if (!part[2].matches("(?i)[0-9a-f]{8}"))
                throw new IllegalArgumentException("Неверный ключ 100×100 (8 шестнадцатеричных знаков).");
            int seed = (int)Long.parseLong(part[2], 16);
            if (seed == 0) throw new IllegalArgumentException("Нулевой ключ не используется.");
            return destinationsForSeed(100, seed);
        }
        throw new IllegalArgumentException("Режим сетки и версия кода не совпадают.");
    }

    /**
     * Deterministic, platform-independent Fisher–Yates shuffle with xorshift32.
     *
     * Given the original image, generator MUST put original tile from FINAL
     * position destinations[i] into scrambled PNG at INITIAL position i.
     * The puzzle app never needs that original image.
     */
    public static int[] destinationsForSeed(int grid, int seed) {
        if (grid < 2 || grid > 100 || seed == 0)
            throw new IllegalArgumentException("Режим или ключ не поддерживается.");
        int size = grid*grid;
        int[] destinations = new int[size];
        for (int i=0; i<size; i++) destinations[i] = i;
        int state = seed;
        for (int i=size-1; i>0; i--) {
            state ^= (state << 13);
            state ^= (state >>> 17);
            state ^= (state << 5);
            long unsigned = state & 0xffffffffL;
            int j = (int)((unsigned * (i+1L)) >>> 32);
            int t = destinations[i]; destinations[i] = destinations[j]; destinations[j] = t;
        }
        return destinations;
    }

    public static String codeForSeed(int grid, int seed, String crc) {
        if (grid != 100 || seed == 0 || crc == null || !crc.matches("(?i)[0-9a-f]{8}"))
            throw new IllegalArgumentException("Неверные параметры пазла.");
        return "PZ2.100." + String.format(Locale.ROOT, "%08X", seed) + "." +
               crc.toUpperCase(Locale.ROOT);
    }

    public static void validate(int[] positions) {
        int n = positions.length;
        boolean[] used = new boolean[n];
        for (int v : positions) {
            if (v<0 || v>=n || used[v]) throw new IllegalArgumentException("Некорректная перестановка.");
            used[v] = true;
        }
    }

    /**
     * Returns exact swaps for *any* already modified board. Complexity O(N),
     * not O(N^2), even with 10,000 cells.
     */
    public static int[][] swapsToSolve(int[] current, int[] destinations) {
        int size = destinations.length;
        if (current.length != size) throw new IllegalArgumentException("Размер сетки не совпадает.");
        validate(current);
        validate(destinations);
        int[] destinationTile = new int[size]; // which original tile belongs in each final slot
        for (int id=0; id<size; id++) destinationTile[destinations[id]] = id;
        int[] state = current.clone();
        int[] location = new int[size];
        for (int i=0; i<size; i++) location[state[i]] = i;
        int[][] scratch = new int[size-1][2];
        int count = 0;
        for (int i=0; i<size; i++) {
            int target = destinationTile[i];
            if (state[i] == target) continue;
            int j = location[target];
            int other = state[i];
            state[j] = other;
            state[i] = target;
            location[target] = i;
            location[other] = j;
            scratch[count][0] = i;
            scratch[count][1] = j;
            count++;
        }
        int[][] result = new int[count][2];
        System.arraycopy(scratch, 0, result, 0, count);
        return result;
    }
}
