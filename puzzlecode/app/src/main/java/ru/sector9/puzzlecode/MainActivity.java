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
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Main screen for 4x4 and 100x100 puzzle boards. Android-only, no WebView or native libraries.
 * The source image is never loaded: only the already scrambled image is needed.
 */
public final class MainActivity extends Activity {
    private static final int PICK_FILE = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PuzzleBoard board;
    private TextView info;
    private EditText codeField;
    private Button solveButton, resetButton;
    private Spinner gridPicker;
    private int gridSize = 4;
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

        TextView title = text("PuzzleCode 2.2", 25, Config.ACCENT);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView help = text("Загрузи перемешанный PNG из чата. Меняй местами фрагменты, а затем вставь код для автоматической сборки.", 14, Config.TEXT);
        root.addView(help);

        gridPicker = new Spinner(this);
        String[] sizes = {"4×4 — 16 фрагментов", "100×100 — 10 000 фрагментов"};
        ArrayAdapter<String> sizesAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sizes);
        sizesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        gridPicker.setAdapter(sizesAdapter);
        root.addView(gridPicker);
        gridPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) { }
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                int wanted = pos == 0 ? 4 : 100;
                if (gridSize == wanted) return;
                if (busy) return;
                gridSize = wanted;
                imageCrc = null;
                if (board != null) board.clearImageAndChangeGrid(wanted);
                if (codeField != null) codeField.setHint(wanted == 4 ? "PZ1.4. ..." : "PZ2.100.XXXXXXXX.CRC32");
                updateControls();
                status("Сетка " + wanted + "×" + wanted +
                       ". Открой PNG, стороны которого делятся на " + wanted + ".");
            }
        });
        Button open = button("Открыть PNG из телефона");
        root.addView(open);
        open.setOnClickListener(v -> chooseFile());

        board = new PuzzleBoard();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(280));
        bp.topMargin = dp(7);
        bp.bottomMargin = dp(7);
        root.addView(board, bp);

        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);
        Button zoomIn = button("Увеличить +");
        Button zoomOut = button("−");
        Button fitImage = button("Целиком");
        zoomRow.addView(zoomIn, new LinearLayout.LayoutParams(0, dp(50), 2f));
        zoomRow.addView(zoomOut, new LinearLayout.LayoutParams(0, dp(50), 1f));
        zoomRow.addView(fitImage, new LinearLayout.LayoutParams(0, dp(50), 2f));
        root.addView(zoomRow);
        zoomIn.setOnClickListener(v -> board.zoomBy(2.0f));
        zoomOut.setOnClickListener(v -> board.zoomBy(0.5f));
        fitImage.setOnClickListener(v -> board.resetView());

        TextView controls = text(
            "Одним пальцем: перетащи фрагмент к другому или нажми дважды. "+
            "Двумя пальцами: увеличивай и перемещай изображение.", 12, Config.TEXT);
        root.addView(controls);

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
        if (gridPicker != null) gridPicker.setEnabled(!busy);
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
            if (bounds.outWidth % gridSize != 0 || bounds.outHeight % gridSize != 0)
                throw new IOException("Стороны PNG должны делиться на " + gridSize + ".");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            opts.inSampleSize = 1;
            while (Math.max(bounds.outWidth / opts.inSampleSize, bounds.outHeight / opts.inSampleSize) > Config.MAX_BITMAP_DIM)
                opts.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            if (bitmap == null || bitmap.getWidth() % gridSize != 0 || bitmap.getHeight() % gridSize != 0)
                throw new IOException("После загрузки стороны должны делиться на " + gridSize + ". Попробуй PNG меньшего размера.");
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

    /** Both PZ1 legacy codes and compact PZ2.100 seed codes are accepted. */
    private void startSolving() {
        if (busy || !board.hasImage()) return;
        try {
            int[] permutation = PuzzleCodec.decode(codeField.getText().toString(), gridSize, imageCrc);
            pendingSteps = PuzzleCodec.swapsToSolve(board.copyTiles(), permutation);
            if (pendingSteps.length == 0) {
                board.showGrid(false);
                status("Пазл уже собран.");
                return;
            }
            stepNumber = 0;
            busy = true;
            updateControls();
            status("Сборка: 0 / " + pendingSteps.length);
            handler.postDelayed(nextStep, gridSize == 100 ? 40 : Config.STEP_DELAY_MS);
        } catch (Exception ex) {
            status("Ошибка кода: " + ex.getMessage());
        }
    }

    /**
     * Small puzzles animate each swap; 10,000-piece puzzles animate in batches
     * to avoid a 30-minute UI lockup.
     */
    private final Runnable nextStep = new Runnable() {
        @Override public void run() {
            if (!busy || isFinishing()) return;
            int batch = gridSize == 100 ? 400 : 1;
            int stop = Math.min(stepNumber + batch, pendingSteps.length);
            while (stepNumber < stop) {
                int[] action = pendingSteps[stepNumber++];
                board.swapWithoutDraw(action[0], action[1]);
            }
            board.invalidate();
            status("Сборка: " + stepNumber + " / " + pendingSteps.length);
            if (stepNumber < pendingSteps.length) {
                handler.postDelayed(this, gridSize == 100 ? 40 : Config.STEP_DELAY_MS);
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
     * Zoomable puzzle canvas. Draws ONLY viewport-visible cells, crucial when
     * the image consists of 10,000 pieces. No 10,000 separate bitmap objects.
     *
     * One-finger drag swaps start and end cells; two-finger drag pans;
     * pinch gesture zooms about the fingers' center.
     */
    private final class PuzzleBoard extends View {
        private Bitmap image;
        private int[] tiles = new int[16];
        private int n = 4;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector scaleDetector;
        private boolean grid = true, blocked = false, multiTouch = false;
        private int selected = -1, startTile = -1;
        private float zoom = 1f, panX = 0f, panY = 0f;
        private float previousMidX, previousMidY;

        PuzzleBoard() {
            super(MainActivity.this);
            scaleDetector = new ScaleGestureDetector(MainActivity.this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    float newZoom = Math.max(1f, Math.min(64f, zoom * detector.getScaleFactor()));
                    applyZoom(newZoom, detector.getFocusX(), detector.getFocusY());
                    return true;
                }
            });
            reset();
        }

        boolean hasImage() { return image != null && !image.isRecycled(); }
        int[] copyTiles() { return tiles.clone(); }
        void setBusy(boolean b) { blocked = b; }
        void showGrid(boolean show) { grid = show; invalidate(); }

        void clearImageAndChangeGrid(int newN) {
            if (image != null) image.recycle();
            image = null;
            n = newN;
            tiles = new int[n*n];
            reset();
            requestLayout();
        }

        void setBitmap(Bitmap newImage) {
            Bitmap old = image;
            image = newImage;
            n = gridSize;
            tiles = new int[n*n];
            reset();
            requestLayout();
            if (old != null && old != newImage) old.recycle();
        }

        /** Each tile ID is its position in the original *scrambled* PNG. */
        void reset() {
            for (int i=0; i<tiles.length; i++) tiles[i] = i;
            selected = -1;
            grid = true;
            resetView();
            invalidate();
        }
        void swapWithoutDraw(int a, int b) {
            int t=tiles[a]; tiles[a]=tiles[b]; tiles[b]=t;
            selected=-1;
        }
        void swap(int a,int b) { swapWithoutDraw(a,b); invalidate(); }

        void resetView() { zoom=1f; panX=0f; panY=0f; invalidate(); }
        void zoomBy(float factor) { applyZoom(zoom*factor, getWidth()/2f, getHeight()/2f); }
        void applyZoom(float newZoom, float focusX, float focusY) {
            if (!hasImage() || getWidth() <= 0 || getHeight() <= 0) return;
            newZoom=Math.max(1f,Math.min(64f,newZoom));
            float oldScale=fitScale()*zoom;
            float newScale=fitScale()*newZoom;
            float imageX=(focusX-originX())/oldScale;
            float imageY=(focusY-originY())/oldScale;
            panX=focusX-(getWidth()-image.getWidth()*newScale)/2f-imageX*newScale;
            panY=focusY-(getHeight()-image.getHeight()*newScale)/2f-imageY*newScale;
            zoom=newZoom;
            clampPan();
            invalidate();
        }
        float fitScale() {
            if (!hasImage()) return 1f;
            return Math.min(getWidth()/(float)image.getWidth(), getHeight()/(float)image.getHeight());
        }
        float originX() { return (getWidth()-image.getWidth()*fitScale()*zoom)/2f+panX; }
        float originY() { return (getHeight()-image.getHeight()*fitScale()*zoom)/2f+panY; }
        void clampPan() {
            if (!hasImage()) return;
            float limitX=Math.max(0f,(image.getWidth()*fitScale()*zoom-getWidth())/2f);
            float limitY=Math.max(0f,(image.getHeight()*fitScale()*zoom-getHeight())/2f);
            panX=Math.max(-limitX,Math.min(limitX,panX));
            panY=Math.max(-limitY,Math.min(limitY,panY));
        }

        @Override protected void onMeasure(int wm,int hm) {
            int w=MeasureSpec.getSize(wm);
            int h=hasImage()?Math.round(w*(float)image.getHeight()/image.getWidth()):dp(280);
            setMeasuredDimension(w,Math.max(dp(80),h));
        }

        private int cellAt(float x,float y) {
            if (!hasImage()) return -1;
            float fx=(x-originX())/(fitScale()*zoom);
            float fy=(y-originY())/(fitScale()*zoom);
            if (fx<0 || fy<0 || fx>=image.getWidth() || fy>=image.getHeight()) return -1;
            int col=Math.min(n-1,(int)((double)fx*n/image.getWidth()));
            int row=Math.min(n-1,(int)((double)fy*n/image.getHeight()));
            return row*n+col;
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(Config.PANEL);
            if (!hasImage()) return;
            float scale=fitScale()*zoom;
            float ox=originX(), oy=originY();
            float dw=image.getWidth()*scale/n;
            float dh=image.getHeight()*scale/n;
            int minCol=Math.max(0,(int)Math.floor((0-ox)/dw));
            int maxCol=Math.min(n-1,(int)Math.ceil((getWidth()-ox)/dw));
            int minRow=Math.max(0,(int)Math.floor((0-oy)/dh));
            int maxRow=Math.min(n-1,(int)Math.ceil((getHeight()-oy)/dh));
            for (int row=minRow;row<=maxRow;row++) {
                for(int col=minCol;col<=maxCol;col++) {
                    int tile=tiles[row*n+col];
                    int fromX=(tile%n)*image.getWidth()/n;
                    int fromY=(tile/n)*image.getHeight()/n;
                    Rect source=new Rect(fromX,fromY,(tile%n+1)*image.getWidth()/n,
                        (tile/n+1)*image.getHeight()/n);
                    RectF dest=new RectF(ox+col*dw,oy+row*dh,ox+(col+1)*dw,oy+(row+1)*dh);
                    canvas.drawBitmap(image,source,dest,bitmapPaint);
                }
            }
            // Skip indistinguishable grid lines when cells become very tiny.
            if(grid && dw>=4 && dh>=4) {
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setStrokeWidth(dp(0.7f));
                linePaint.setColor(Config.GRID_COLOR);
                for(int col=Math.max(1,minCol);col<=maxCol;col++) {
                    float x=ox+col*dw;
                    canvas.drawLine(x,Math.max(0,oy),x,Math.min(getHeight(),oy+n*dh),linePaint);
                }
                for(int row=Math.max(1,minRow);row<=maxRow;row++) {
                    float y=oy+row*dh;
                    canvas.drawLine(Math.max(0,ox),y,Math.min(getWidth(),ox+n*dw),y,linePaint);
                }
                linePaint.setStyle(Paint.Style.FILL);
            }
            if(selected>=0) {
                int col=selected%n,row=selected/n;
                linePaint.setStyle(Paint.Style.STROKE);
                linePaint.setColor(Config.ACCENT);
                linePaint.setStrokeWidth(dp(3));
                canvas.drawRect(ox+col*dw,oy+row*dh,
                     ox+(col+1)*dw,oy+(row+1)*dh,linePaint);
                linePaint.setStyle(Paint.Style.FILL);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!hasImage() || blocked) return false;
            // Prevent ScrollView from taking the single-finger drag
            // which is needed to swap pieces.
            if (getParent()!=null && e.getActionMasked()==MotionEvent.ACTION_DOWN)
                getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(e);
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN) {
                multiTouch=false;
                startTile=cellAt(e.getX(),e.getY());
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_DOWN) {
                multiTouch=true;startTile=-1;
                if (e.getPointerCount()>=2) {
                    previousMidX=(e.getX(0)+e.getX(1))/2f;
                    previousMidY=(e.getY(0)+e.getY(1))/2f;
                }
                return true;
            }
            if(action==MotionEvent.ACTION_MOVE && e.getPointerCount()>=2) {
                float midX=(e.getX(0)+e.getX(1))/2f;
                float midY=(e.getY(0)+e.getY(1))/2f;
                panX+=midX-previousMidX;
                panY+=midY-previousMidY;
                previousMidX=midX;previousMidY=midY;
                clampPan();
                invalidate();
                return true;
            }
            if(action==MotionEvent.ACTION_UP) {
                int end=cellAt(e.getX(),e.getY());
                if(!multiTouch && end>=0 && startTile>=0) {
                    if(end!=startTile) {
                        swap(startTile,end);status("Фрагменты обменяны.");
                    } else if(selected<0) {
                        selected=end; status("Выбран фрагмент "+(selected+1));
                    } else if(selected==end) {
                        selected=-1;status("Выбор снят.");
                    } else {
                        swap(selected,end);status("Фрагменты обменяны.");
                    }
                    invalidate();
                }
                startTile=-1;
                performClick();
                return true;
            }
            if(action==MotionEvent.ACTION_CANCEL) {
                startTile=-1;multiTouch=false;
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {super.performClick();return true;}
    }
}
