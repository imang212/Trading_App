package com.trading.trading_app.ui.correlation;

import android.content.Context;
import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint;
import android.util.AttributeSet; import android.view.View;
/**Custom View that draws a Pearson correlation matrix as a colour-coded heatmap.
 * Centered horizontally with square cells and rotated labels.*/
public class CorrelationGridView extends View {
    private String[] labels;
    private double[][] data;
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG), textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG), labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int measuredLabelWidth = 0, measuredCellSize = 0, measuredTopPadding = 0;
    public CorrelationGridView(Context context) { super(context); init(); }
    public CorrelationGridView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CorrelationGridView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }
    private void init() {
        textPaint.setColor(Color.BLACK); textPaint.setTextAlign(Paint.Align.CENTER); labelPaint.setColor(Color.LTGRAY);
        if (isInEditMode()) {
            labels = new String[]{"BTC", "ETH", "SOL", "ADA"};
            data = new double[][]{{1.0, 0.8, 0.6, 0.4}, {0.8, 1.0, 0.5, 0.3}, {0.6, 0.5, 1.0, 0.7}, {0.4, 0.3, 0.7, 1.0}};
        }
    }
    public void setData(String[] labels, double[][] data) {
        this.labels = labels; this.data = data;
        requestLayout(); invalidate();
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int n = (labels != null) ? labels.length : 0;
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (n == 0) { setMeasuredDimension(w, 0); return; }
        // Proportional padding: enough for labels but not excessive
        measuredLabelWidth = (int) (w * 0.16f); // Left margin for row labels
        measuredTopPadding = (int) (w * 0.14f); // Top margin for column labels
        int availableW = w - (measuredLabelWidth * 2); // Leave some space on both sides
        int cell = availableW / n;
        // Cap cell size to keep matrix compact on small asset lists (e.g. 4 assets)
        int maxCell = (int) (w * 0.12f);
        if (cell > maxCell) cell = maxCell;
        if (cell < 50) cell = 50; // Minimum readable
        measuredCellSize = cell;
        int h = measuredTopPadding + (n * cell) + 20; // +20 for bottom breathing room
        setMeasuredDimension(w, h);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels == null || data == null || labels.length == 0) return;
        int n = labels.length;
        int cell = measuredCellSize, lw = measuredLabelWidth, th = measuredTopPadding;
        // Centering logic: calculate total width of the drawn content and offset it
        int gridWidth = n * cell;
        int totalContentW = lw + gridWidth;
        int startX = (getWidth() - totalContentW) / 2;
        float ts = cell * 0.32f;
        textPaint.setTextSize(ts); labelPaint.setTextSize(ts * 0.95f);
        for (int i = 0; i < n; i++) {
            canvas.save(); // 1. Column labels (rotated -45 degrees)
            float cx = startX + lw + i * cell + cell / 2f;
            float cy = th - 10; // Draw just above the first row
            canvas.rotate(-45, cx, cy);
            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(labels[i], cx, cy, labelPaint);
            canvas.restore();
            labelPaint.setTextAlign(Paint.Align.RIGHT); // 2. Row labels (Right aligned to the left of the grid)
            float ry = th + i * cell + cell / 2f;
            canvas.drawText(labels[i], startX + lw - 12, ry + ts / 3f, labelPaint);
            for (int j = 0; j < n; j++) {
                double v = data[i][j];
                cellPaint.setColor(corrColor(v));
                float left = startX + lw + j * cell, top = th + i * cell;
                float right = left + cell, bottom = top + cell;
                canvas.drawRect(left, top, right, bottom, cellPaint);
                String val = String.format("%.2f", v); // 3. Correlation values
                textPaint.setColor(Math.abs(v) > 0.5 ? Color.WHITE : Color.BLACK);
                canvas.drawText(val, left + cell / 2f, top + cell / 2f + ts / 3f, textPaint);
            }
        }
    }
    private int corrColor(double v) {
        v = Math.max(-1, Math.min(1, v));
        if (v >= 0) { // Green scale
            int r = (int) (255 * (1 - v)); return Color.rgb(r, 255, r);
        } else { // Red scale
            int g = (int) (255 * (1 + v)); return Color.rgb(255, g, g);
        }
    }
}
