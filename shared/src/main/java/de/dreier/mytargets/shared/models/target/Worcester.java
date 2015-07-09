/*
 * MyTargets Archery
 *
 * Copyright (C) 2015 Florian Dreier
 * All rights reserved
 */
package de.dreier.mytargets.shared.models.target;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;

import de.dreier.mytargets.shared.R;
import de.dreier.mytargets.shared.models.Dimension;

import static android.graphics.Color.WHITE;

public class Worcester extends CircularTargetBase {
    public Worcester(Context context) {
        super(context, 12, R.string.worcester_face);
        zones = 5;
        radius = new int[]{100, 200, 300, 400, 500};
        colorFill = new int[]{WHITE, DARK_GRAY, DARK_GRAY, DARK_GRAY, DARK_GRAY};
        colorStroke = new int[]{WHITE, WHITE, WHITE, WHITE, WHITE};
        strokeWidth = new int[]{4, 4, 4, 4, 0};
        zonePoints = new int[][]{{5, 4, 3, 2, 1}};
        showAsX = new boolean[]{false};
        diameters = new Dimension[]{new Dimension(16, Dimension.INCH)};
    }

    @Override
    protected void onPostDraw(Canvas canvas, Rect rect) {
        paintStroke.setColor(Target.DARK_GRAY);
        final float size = recalc(rect, 10.5f);
        paintStroke.setStrokeWidth(4 * rect.width() / 1000f);
        canvas.drawLine(rect.exactCenterX() - size, rect.exactCenterY(),
                rect.exactCenterX() + size, rect.exactCenterY(), paintStroke);
        canvas.drawLine(rect.exactCenterX(), rect.exactCenterY() - size,
                rect.exactCenterX(), rect.exactCenterY() + size, paintStroke);
    }
}
