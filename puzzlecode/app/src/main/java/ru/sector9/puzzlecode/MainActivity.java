package ru.sector9.puzzlecode;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Main screen and 4x4 puzzle board. Android-only, no WebView or native libraries.
 * The source image is never loaded: only the already scrambled image is needed.
 */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PuzzleBoard board;
    private TextView info;
    private EditText codeField;
    private Button solveButton, resetButton;
    private String imageCrc = null;
    private int[][] pendingSteps;
    private int stepNumber;
    private boolean busy = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
    }

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView text(String label, int sp, int color) {
        TextView result = new TextView(this);
        result.setText(label);
        result.setTextSize(sp);
        result.setTextColor(color);
        return result;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    /** A regular ScrollView/Activity layout is robust on phones with different ratios. */
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(18));
        root.setBackgroundColor(Config.BG);
        root.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("PuzzleCode 2.1", 25, Config.ACCENT);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView help = text("Загрузи перемешанный PNG из чата. Меняй местами фрагменты, а затем вставь код для автоматической сборки.", 14, Config.TEXT);
        root.addView(help);
        Button open = button("Открыть PNG из телефона");
        root.addView(open);
        open.setOnClickListener(v -> chooseFile());

        board = new PuzzleBoard();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
        bp.topMargin = dp(7);
        bp.bottomMargin = dp(7);
        root.addView(board, bp);

        resetButton = button("Сбросить перестановки");
        root.addView(resetButton);
        resetButton.setOnClickListener(v -> {
            if (busy || !board.hasImage()) return;
            board.reset();
            status("Пазл снова перемешан как в исходном PNG.");
        });

        TextView label = text("Код автоматической сборки:", 15, Config.TEXT);
        label.setPadding(0, dp(10), 0, dp(4));
        root.addView(label);

        codeField = new EditText(this);
        codeField.setTextColor(Config.TEXT);
        codeField.setHintTextColor(0xff8b99b1);
        codeField.setTextSize(14);
        codeField.setHint("PZ1.4. ...");
        codeField.setSingleLine(false);
        codeField.setMinLines(2);
        codeField.setSelectAllOnFocus(true);
        root.addView(codeField);

        solveButton = button("Собрать по коду");
        root.addView(solveButton);
        solveButton.setOnClickListener(v -> startSolving());

        info = text("Открой PNG, чтобы начать.", 14, Config.ACCENT);
        info.setPadding(dp(6), dp(15), dp(6), dp(8));
        root.addView(info);
        updateControls();
    }

    private void status(String msg) {
        if (info != null) info.setText(msg);
    }

    private void updateControls() {
        boolean loaded = board != null && board.hasImage();
        if (solveButton != null) solveButton.setEnabled(loaded && !busy);
        if (resetButton != null) resetButton.setEnabled(loaded && !busy);
        if (codeField != null) codeField.setEnabled(!busy);
        if (board != null) board.setBusy(busy);
    }

    /** Android's document picker handles storage permissions on Android 16. */
    private void chooseFile() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_FILE);
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != PICK_FILE || result != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) { status("Файл не выбран."); return; }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Нет доступа к изображению.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (output.size() + n > Config.MAX_INPUT_BYTES) throw new IOException("Файл больше 25 МБ.");
                output.write(buffer, 0, n);
            }
            byte[] bytes = output.toByteArray();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                throw new IOException("Неизвестный формат картинки.");
            if (bounds.outWidth % 4 != 0 || bounds.outHeight % 4 != 0)
                throw new IOException("Размеры изображения должны делиться на 4.");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            opts.inSampleSize = 1;
            while (Math.max(bounds.outWidth / opts.inSampleSize, bounds.outHeight / opts.inSampleSize) > Config.MAX_BITMAP_DIM)
                opts.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bitmap == null || bitmap.getWidth() % 4 != 0 || bitmap.getHeight() % 4 != 0)
                throw new IOException("Не удалось загрузить картинку кратную сетке 4×4.");
            CRC32 crc = new CRC32();
            crc.update(bytes);
            imageCrc = String.format(Locale.ROOT, "%08X", crc.getValue());
            board.setBitmap(bitmap);
            updateControls();
            status("PNG открыт. CRC32: " + imageCrc + ". Переставляй части или вставь код.");
        } catch (Exception ex) {
            status("Ошибка открытия: " + ex.getMessage());
        }
    }

    /**
     * Decode PZ1.4 code. The 16-byte permutation maps an initial slot to
     * the final slot of that tile. It does NOT contain the original picture.
     */
    private int[] decodePermutation(String input) {
        if (imageCrc == null) throw new IllegalArgumentException("Сначала открой изображение.");
        String[] tokens = input.replaceAll("\\s+", "").split("\\.", -1);
        if (tokens.length != 4 || !tokens[0].equals("PZ1") || !tokens[1].equals("4"))
            throw new IllegalArgumentException("Ожидается код PZ1.4.");
        if (!tokens[3].equalsIgnoreCase(imageCrc))
            throw new IllegalArgumentException("Это код от другого PNG. Загрузи файл без изменений.");
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(tokens[2]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Код повреждён.");
        }
        if (decoded.length != 16) throw new IllegalArgumentException("Код должен содержать 16 номеров.");
        int[] positions = new int[16];
        boolean[] used = new boolean[16];
        for (int i = 0; i < 16; i++) {
            int k = decoded[i] & 0xff;
            if (k >= 16 || used[k]) throw new IllegalArgumentException("Неверная перестановка.");
            positions[i] = k;
            used[k] = true;
        }
        return positions;
    }

    /** Create a swap plan for ANY current state, including user swaps. */
    private void startSolving() {
        if (busy || !board.hasImage()) return;
        try {
            int[] p = decodePermutation(codeField.getText().toString());
            int[] target = new int[16];
            for (int i = 0; i < 16; i++) target[p[i]] = i;
            int[] current = board.copyTiles();
            int[][] actions = new int[15][2];
            int steps = 0;
            for (int i = 0; i < 16; i++) {
                if (current[i] == target[i]) continue;
                int j = i + 1;
                while (j < 16 && current[j] != target[i]) j++;
                if (j == 16) throw new IllegalArgumentException("Ошибка текущего состояния.");
                actions[steps][0] = i;
                actions[steps][1] = j;
                steps++;
                int t = current[i]; current[i] = current[j]; current[j] = t;
            }
            if (steps == 0) {
                board.showGrid(false);
                status("Пазл уже собран.");
                return;
            }
            pendingSteps = new int[steps][2];
            System.arraycopy(actions, 0, pendingSteps, 0, steps);
            stepNumber = 0;
            busy = true;
            updateControls();
            status("Сборка: 0 / " + pendingSteps.length);
            handler.postDelayed(nextStep, Config.STEP_DELAY_MS);
        } catch (Exception ex) {
            status("Ошибка кода: " + ex.getMessage());
        }
    }

    private final Runnable nextStep = new Runnable() {
        @Override public void run() {
            if (!busy || isFinishing()) return;
            int[] action = pendingSteps[stepNumber++];
            board.swap(action[0], action[1]);
            status("Сборка: " + stepNumber + " / " + pendingSteps.length);
            if (stepNumber < pendingSteps.length) {
                handler.postDelayed(this, Config.STEP_DELAY_MS);
            } else {
                busy = false;
                board.showGrid(false);
                updateControls();
                status("Готово! Пазл собран за " + stepNumber + " перестановок.");
            }
        }
    };

    @Override protected void onDestroy() {
        handler.removeCallbacks(nextStep);
        super.onDestroy();
    }

    /**
     * Four-by-four Canvas view. Bitmap is decoded once, and the touch
     * coordinates are translated to cell numbers, independent of screen size.
     */
    private final class PuzzleBoard extends View {
        private Bitmap image;
        private final int[] tiles = new int[16];
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean grid = true, blocked = false;
        private int selected = -1, startTile = -1;
        PuzzleBoard() { super(MainActivity.this); reset(); }

        boolean hasImage() { return image != null && !image.isRecycled(); }
        int[] copyTiles() { return tiles.clone(); }
        void setBusy(boolean b) { blocked = b; }
        void showGrid(boolean g) { grid = g; invalidate(); }
        void setBitmap(Bitmap newImage) {
            Bitmap old = image;
            image = newImage;
            reset();
            requestLayout();
            if (old != null && old != newImage) old.recycle();
        }
        void reset() {
            for (int i = 0; i < 16; i++) tiles[i] = i;
            selected = -1;
            grid = true;
            invalidate();
        }
        void swap(int a, int b) {
            int t = tiles[a]; tiles[a] = tiles[b]; tiles[b] = t;
            selected = -1;
            invalidate();
        }
        @Override protected void onMeasure(int wm, int hm) {
            int w = MeasureSpec.getSize(wm);
            int h = hasImage() ? Math.round((float)w * image.getHeight() / image.getWidth()) : dp(280);
            setMeasuredDimension(w, Math.max(dp(80), h));
        }
        private int cellAt(float x, float y) {
            if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) return -1;
            return Math.min(3, (int)(y*4/getHeight()))*4 + Math.min(3, (int)(x*4/getWidth()));
        }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Config.PANEL);
            if (!hasImage()) return;
            int w = getWidth(), h = getHeight();
            for (int at = 0; at < 16; at++) {
                int src = tiles[at];
                Rect from = new Rect(
                   (src%4)*image.getWidth()/4, (src/4)*image.getHeight()/4,
                   ((src%4)+1)*image.getWidth()/4, ((src/4)+1)*image.getHeight()/4);
                RectF to = new RectF((at%4)*w/4f, (at/4)*h/4f,
                                   ((at%4)+1)*w/4f, ((at/4)+1)*h/4f);
                canvas.drawBitmap(image, from, to, bitmapPaint);
            }
            if (grid) {
                linePaint.setColor(Config.GRID_COLOR);
                linePaint.setStrokeWidth(dp(1));
                for (int k=1;k<4;k++) {
                    canvas.drawLine(w*k/4f,0,w*k/4f,h,linePaint);
                    canvas.drawLine(0,h*k/4f,w,h*k/4f,linePaint);
                }
            }
            if (selected>=0) {
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setColor(Config.ACCENT);
                linePaint.setStrokeWidth(dp(3));
                int c=selected%4,r=selected/4;
                canvas.drawRect(c*w/4f,r*h/4f,(c+1)*w/4f,(r+1)*h/4f,linePaint);
                linePaint.setStyle(Paint.Style.FILL);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!hasImage() || blocked) return false;
            if (e.getActionMasked()==MotionEvent.ACTION_DOWN) {
                startTile = cellAt(e.getX(),e.getY());
                return startTile>=0;
            }
            if (e.getActionMasked()==MotionEvent.ACTION_UP) {
                int end=cellAt(e.getX(),e.getY());
                if (end>=0 && startTile>=0) {
                    if (end != startTile) {swap(startTile,end);status("Фрагменты обменяны.");}
                    else if (selected<0) {selected=end;status("Выбран фрагмент "+(selected+1));}
                    else if (selected==end) {selected=-1;status("Выбор снят.");}
                    else {swap(selected,end);status("Фрагменты обменяны.");}
                    invalidate();
                }
                startTile=-1;
                performClick();
                return true;
            }
            return true;
        }
        @Override public boolean performClick() {super.performClick();return true;}
    }
}
