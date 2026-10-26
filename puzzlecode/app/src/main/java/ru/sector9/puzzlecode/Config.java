package ru.sector9.puzzlecode;
/** Editable application parameters in one place. */
public final class Config {
    private Config() {}
    public static final int GRID = 4;
    public static final int MAX_INPUT_BYTES = 25*1024*1024;
    public static final int MAX_BITMAP_DIM = 2048;
    public static final long STEP_DELAY_MS = 220;
    public static final int BG = 0xff101522, PANEL = 0xff243049,
       TEXT = 0xfff3f5fc, ACCENT = 0xffffc16b, GRID_COLOR = 0xccffffff;
}
