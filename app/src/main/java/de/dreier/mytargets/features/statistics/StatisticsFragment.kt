/*
 * Copyright (C) 2017 Florian Dreier
 *
 * This file is part of MyTargets.
 *
 * MyTargets is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
 *
 * MyTargets is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package de.dreier.mytargets.features.statistics;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.databinding.DataBindingUtil;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.renderer.LineChartRenderer;
import com.github.mikephil.charting.utils.ColorTemplate;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;
import org.threeten.bp.format.FormatStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.dreier.mytargets.R;
import de.dreier.mytargets.app.ApplicationInstance;
import de.dreier.mytargets.base.fragments.FragmentBase;
import de.dreier.mytargets.databinding.FragmentStatisticsBinding;
import de.dreier.mytargets.databinding.ItemImageSimpleBinding;
import de.dreier.mytargets.features.settings.SettingsManager;
import de.dreier.mytargets.shared.analysis.aggregation.EAggregationStrategy;
import de.dreier.mytargets.shared.models.Dimension;
import de.dreier.mytargets.shared.models.Score;
import de.dreier.mytargets.shared.models.SelectableZone;
import de.dreier.mytargets.shared.models.Target;
import de.dreier.mytargets.shared.models.db.End;
import de.dreier.mytargets.shared.models.db.Round;
import de.dreier.mytargets.shared.models.db.Shot;
import de.dreier.mytargets.shared.models.db.Training;
import de.dreier.mytargets.shared.streamwrapper.Stream;
import de.dreier.mytargets.shared.targets.drawable.TargetImpactAggregationDrawable;
import de.dreier.mytargets.shared.utils.Color;
import de.dreier.mytargets.shared.utils.LongUtils;
import de.dreier.mytargets.shared.utils.SharedUtils;
import de.dreier.mytargets.utils.MobileWearableClient;
import de.dreier.mytargets.utils.RoundedTextDrawable;
import de.dreier.mytargets.utils.ToolbarUtils;
import de.dreier.mytargets.utils.Utils;

import static de.dreier.mytargets.utils.MobileWearableClient.BROADCAST_UPDATE_TRAINING_FROM_REMOTE;

public class StatisticsFragment extends FragmentBase {

    private static final String ARG_TARGET = "target";
    private static final String ARG_ROUND_IDS = "round_ids";
    private static final String ARG_ANIMATE = "animate";
    private static final String PIE_CHART_CENTER_TEXT_FORMAT = "<font color='gray'>%s</font><br>" +
            "<big>%s</big><br>" +
            "<small>&nbsp;</small><br>" +
            "<font color='gray'>%s</font><br>" +
            "<big>%d</big>";
    @NonNull
    private static final Description EMPTY_DESCRIPTION;

    static {
        EMPTY_DESCRIPTION = new Description();
        EMPTY_DESCRIPTION.setText("");
    }

    @Nullable
    private long[] roundIds;
    private List<Round> rounds;
    private ArrowStatisticAdapter adapter;
    private FragmentStatisticsBinding binding;
    private Target target;
    private boolean animate;

    @NonNull
    private BroadcastReceiver updateReceiver = new MobileWearableClient.EndUpdateReceiver() {

        @Override
        protected void onUpdate(Long trainingId, Long roundId, End end) {
            if (Stream.of(LongUtils.toList(roundIds))
                    .anyMatch(r -> SharedUtils.INSTANCE.equals(r, roundId))) {
                reloadData();
            }
        }
    };

    @NonNull
    public static StatisticsFragment newInstance(@NonNull List<Long> roundIds, Target item, boolean animate) {
        StatisticsFragment fragment = new StatisticsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(StatisticsFragment.ARG_TARGET, item);
        bundle.putLongArray(StatisticsFragment.ARG_ROUND_IDS, LongUtils.toArray(roundIds));
        bundle.putBoolean(StatisticsFragment.ARG_ANIMATE, animate);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(updateReceiver,
                new IntentFilter(BROADCAST_UPDATE_TRAINING_FROM_REMOTE));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(updateReceiver);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_statistics, container, false);

        Target target = getArguments().getParcelable(ARG_TARGET);
        if(SettingsManager.INSTANCE.getStatisticsDispersionPatternMergeSpot()) {
            this.target = Target.Companion.singleSpotTargetFrom(target);
        } else {
            this.target = target;
        }
        roundIds = getArguments().getLongArray(ARG_ROUND_IDS);
        animate = getArguments().getBoolean(ARG_ANIMATE);

        binding.arrows.setHasFixedSize(true);
        adapter = new ArrowStatisticAdapter();
        binding.arrows.setAdapter(adapter);
        binding.arrows.setNestedScrollingEnabled(false);

        ToolbarUtils.showHomeAsUp(this);
        return binding.getRoot();
    }

    @NonNull
    @Override
    protected LoaderUICallback onLoad(Bundle args) {
        rounds = Stream.of(LongUtils.toList(roundIds))
                .map(Round.Companion::get)
                .withoutNulls()
                .toList();

        List<ArrowStatistic> data = ArrowStatistic.Companion.getAll(target, rounds);

        return () -> {
            showLineChart();
            showPieChart();
            showDispersionView();
            binding.distributionChart.invalidate();
            binding.chartView.invalidate();

            binding.arrowRankingLabel.setVisibility(data.isEmpty() ? View.GONE : View.VISIBLE);
            Collections.sort(data);
            adapter.setData(data);
        };
    }

    private void showDispersionView() {
        final List<Shot> exactShots = Stream.of(rounds)
                .flatMap(r -> Stream.of(r.loadEnds()))
                .filter(p -> p.getExact())
                .flatMap(p -> Stream.of(p.loadShots()))
                .filter(p -> p.getScoringRing() != Shot.Companion.getNOTHING_SELECTED())
                .toList();
        if (exactShots.isEmpty()) {
            binding.dispersionPatternLayout.setVisibility(View.GONE);
            return;
        }
        ArrowStatistic stats = new ArrowStatistic(target, exactShots);
        stats.setArrowDiameter(new Dimension(5, Dimension.Unit.MILLIMETER));

        EAggregationStrategy strategy = SettingsManager.INSTANCE.getStatisticsDispersionPatternAggregationStrategy();
        TargetImpactAggregationDrawable drawable = stats.getTarget().getImpactAggregationDrawable();
        drawable.setAggregationStrategy(strategy);
        drawable.replaceShotsWith(stats.getShots());
        drawable.setArrowDiameter(stats.getArrowDiameter(), SettingsManager.INSTANCE.getInputArrowDiameterScale());
        binding.dispersionView.setImageDrawable(drawable);

        binding.dispersionViewOverlay.setOnClickListener(view -> {
            ArrowStatistic statistics = new ArrowStatistic(target, exactShots);
            DispersionPatternActivity.getIntent(statistics)
                    .withContext(this)
                    .start();
        });
    }

    private void showLineChart() {
        LineData data = getLineChartDataSet();
        if (data == null) {
            return;
        }
        binding.chartView.getXAxis().setTextSize(10);
        binding.chartView.getXAxis().setTextColor(0xFF848484);
        binding.chartView.getAxisRight().setEnabled(false);
        binding.chartView.getLegend().setEnabled(false);
        binding.chartView.setData(data);
        final Description desc = new Description();
        desc.setText(getString(R.string.average_arrow_score_per_end));
        binding.chartView.setDescription(desc);
        final int maxCeil = (int) Math.ceil(data.getYMax());
        binding.chartView.getAxisLeft().setAxisMaximum(maxCeil);
        binding.chartView.getAxisLeft().setLabelCount(maxCeil);
        binding.chartView.getAxisLeft().setAxisMinimum(0);
        binding.chartView.getXAxis().setDrawGridLines(false);
        binding.chartView.setDoubleTapToZoomEnabled(false);
        if (animate) {
            binding.chartView.animateXY(2000, 2000);
        }
        binding.chartView.setRenderer(
                new LineChartRenderer(binding.chartView, binding.chartView.getAnimator(),
                        binding.chartView.getViewPortHandler()) {
                    @Override
                    public void drawHighlighted(@NonNull Canvas canvas, Highlight[] indices) {
                        mRenderPaint.setStyle(Paint.Style.FILL);

                        List<ILineDataSet> dataSets = mChart.getLineData().getDataSets();

                        int colorIndex = 0;
                        for (Highlight highlight : indices) {
                            int i = highlight.getDataSetIndex();
                            ILineDataSet dataSet = dataSets.get(i);

                            mRenderPaint.setColor(dataSet.getCircleColor(colorIndex));

                            float circleRadius = dataSet.getCircleRadius();

                            canvas.drawCircle(
                                    highlight.getDrawX(),
                                    highlight.getDrawY(),
                                    circleRadius,
                                    mRenderPaint);
                            colorIndex = colorIndex + 1 % dataSet.getCircleColorCount();
                        }

                        // draws highlight lines (if enabled)
                        super.drawHighlighted(canvas, indices);
                    }
                });
    }

    private void showPieChart() {
        // enable hole and configure
        binding.distributionChart.setTransparentCircleRadius(15);
        binding.distributionChart.setHoleColor(ContextCompat.getColor(getContext(), R.color.md_grey_50));
        binding.distributionChart.getLegend().setEnabled(false);
        binding.distributionChart.setDescription(EMPTY_DESCRIPTION);

        // enable rotation of the chart by touch
        binding.distributionChart.setRotationAngle(0);
        binding.distributionChart.setRotationEnabled(true);

        binding.distributionChart.setUsePercentValues(false);
        binding.distributionChart.highlightValues(null);
        binding.distributionChart.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.md_grey_50));
        binding.distributionChart.invalidate();
        addPieData();
    }

    private void addPieData() {
        List<Map.Entry<SelectableZone, Integer>> scores = End.Companion
                .getSortedScoreDistribution(rounds);

        ArrayList<PieEntry> yValues = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();
        ArrayList<Integer> textColors = new ArrayList<>();

        for (Map.Entry<SelectableZone, Integer> s : scores) {
            if (s.getValue() > 0) {
                yValues.add(new PieEntry(s.getValue(), s.getKey()));
                colors.add(s.getKey().getZone().getFillColor());
                textColors.add(s.getKey().getZone().getTextColor());
            }
        }

        // create pie data set
        PieDataSet dataSet = new PieDataSet(yValues, "");
        dataSet.setValueFormatter(
                (value, entry, dsi, vph) -> ((SelectableZone) entry.getData()).getText());
        dataSet.setSliceSpace(3);
        dataSet.setSelectionShift(5);

        colors.add(ColorTemplate.getHoloBlue());
        dataSet.setColors(colors);

        // instantiate pie data object now
        PieData data = new PieData(dataSet);
        data.setValueTextSize(13f);
        data.setValueTextColor(Color.GRAY);
        data.setDrawValues(true);
        data.setValueTextColors(textColors);

        binding.distributionChart.setData(data);
        final String hitMissText = getHitMissText();
        binding.distributionChart.setCenterText(Utils.fromHtml(hitMissText));

        binding.distributionChart
                .setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
                    @Override
                    public void onValueSelected(@NonNull Entry e, Highlight h) {
                        final SelectableZone selectableZone = (SelectableZone) e.getData();
                        final String s = String.format(Locale.US,
                                PIE_CHART_CENTER_TEXT_FORMAT,
                                getString(R.string.points), selectableZone.getText(),
                                getString(R.string.count), (int) e.getY());
                        binding.distributionChart.setCenterText(Utils.fromHtml(s));
                    }

                    @Override
                    public void onNothingSelected() {
                        binding.distributionChart.setCenterText(Utils.fromHtml(hitMissText));
                    }
                });
    }

    private String getHitMissText() {
        final List<Shot> shots = Stream.of(rounds)
                .flatMap(r -> Stream.of(r.loadEnds()))
                .flatMap(p -> Stream.of(p.loadShots()))
                .filter(p -> p.getScoringRing() !=
                        Shot.Companion.getNOTHING_SELECTED()) //TODO: Refactor to not save NOTHING_SELECTED at all
                .toList();
        long missCount = Stream.of(shots).filter(s -> s.getScoringRing() ==
                Shot.Companion.getMISS()).count();
        long hitCount = shots.size() - missCount;

        return String.format(Locale.US, PIE_CHART_CENTER_TEXT_FORMAT,
                getString(R.string.hits), String.valueOf(hitCount),
                getString(R.string.misses), missCount);
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadData();
    }

    private LineData getLineChartDataSet() {
        Map<Long, Training> trainingsMap = Stream.of(rounds)
                .map(r -> r.getTrainingId())
                .distinct()
                .map(Training.Companion::get)
                .toMap(Training::getId, t->t);

        List<Pair<Float, LocalDateTime>> values = Stream.of(rounds)
                .map(r -> new Pair<>(trainingsMap.get(r.getTrainingId()).getDate(), r))
                .flatMap(roundIdPair -> Stream.of(roundIdPair.second.loadEnds())
                        .map(end -> new Pair<>(roundIdPair.first, end)))
                .map(endPair -> getPairEndSummary(target, endPair.second, endPair.first))
                .sortBy(pair -> pair.second)
                .toList();
        if (values.isEmpty()) {
            return null;
        }

        Evaluator eval = getEntryEvaluator(values);
        binding.chartView.getXAxis().setValueFormatter(
                (value, axis) -> eval.getXValueFormatted(value));

        LineData data;
        if (values.size() < 2) {
            // Without regression line
            data = new LineData(convertToLineData(values, eval));
        } else {
            data = new LineData(generateLinearRegressionLine(values, eval));
            data.addDataSet(convertToLineData(values, eval));
        }
        data.setDrawValues(false);
        return data;
    }

    @NonNull
    private Evaluator getEntryEvaluator(@NonNull final List<Pair<Float, LocalDateTime>> values) {
        boolean singleTraining = Stream.of(rounds)
                .groupBy(r -> r.getTrainingId()).count() == 1;

        Evaluator eval;
        if (singleTraining) {
            eval = new Evaluator() {
                @NonNull
                private DateTimeFormatter dateFormat = DateTimeFormatter
                        .ofLocalizedTime(FormatStyle.SHORT);

                @Override
                public long getXValue(@NonNull List<Pair<Float, LocalDateTime>> values, int i) {
                    return Duration.between(values.get(0).second, values.get(i).second)
                            .getSeconds();
                }

                @Override
                public String getXValueFormatted(float value) {
                    final long diffToFirst = (long) value;
                    return values.get(0).second.plusSeconds(diffToFirst).format(dateFormat);
                }
            };
        } else {
            eval = new Evaluator() {
                @NonNull
                private DateTimeFormatter dateFormat = DateTimeFormatter
                        .ofLocalizedDate(FormatStyle.SHORT);

                @Override
                public long getXValue(List<Pair<Float, LocalDateTime>> values, int i) {
                    return i;
                }

                @Override
                public String getXValueFormatted(float value) {
                    int index = Math.max(Math.min((int) value, values.size() - 1), 0);
                    return dateFormat.format(values.get(index).second);
                }
            };
        }
        return eval;
    }

    private Pair<Float, LocalDateTime> getPairEndSummary(@NonNull Target target, @NonNull End end, @NonNull LocalDate trainingDate) {
        Score reachedScore = target.getReachedScore(end);
        return new Pair<>(reachedScore.getShotAverage(),
                LocalDateTime.of(trainingDate, end.getSaveTime()));
    }

    @NonNull
    private LineDataSet convertToLineData(@NonNull List<Pair<Float, LocalDateTime>> values, @NonNull Evaluator evaluator) {
        List<Entry> seriesEntries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            seriesEntries.add(new Entry(evaluator.getXValue(values, i), values.get(i).first));
        }

        LineDataSet series = new LineDataSet(seriesEntries, "");
        final int color = ApplicationInstance.Companion.getContext().getResources()
                .getColor(R.color.colorPrimary);
        series.setColors(color);
        series.setLineWidth(2);
        series.setCircleColor(color);
        series.setCircleRadius(5);
        series.setCircleColorHole(color);
        series.setDrawValues(false);
        series.setDrawCircles(false);
        series.setHighLightColor(0xff9c9c9c);
        series.setDrawHorizontalHighlightIndicator(false);
        series.setDrawVerticalHighlightIndicator(true);
        series.enableDashedHighlightLine(4, 4, 0);
        return series;
    }

    private ILineDataSet generateLinearRegressionLine(@NonNull List<Pair<Float, LocalDateTime>> values, @NonNull Evaluator eval) {
        int dataSetSize = values.size();
        double[] x = new double[dataSetSize];
        double[] y = new double[dataSetSize];
        // first pass: read in data, compute x bar and y bar
        int n = 0;
        double sumX = 0.0f;
        double sumY = 0.0f;
        long minX = Long.MAX_VALUE;
        long maxX = Long.MIN_VALUE;
        for (int i = 0; i < dataSetSize; i++) {
            x[n] = eval.getXValue(values, i);
            y[n] = values.get(i).first;
            sumX += x[n];
            sumY += y[n];
            if (x[n] < minX) {
                minX = (long) x[n];
            }
            if (x[n] > maxX) {
                maxX = (long) x[n];
            }
            n++;
        }
        if (n < 1) {
            return null;
        }
        double xBar = sumX / n;
        double yBar = sumY / n;

        // second pass: compute summary statistics
        double xxBar = 0.0f;
        double xyBar = 0.0f;
        for (int i = 0; i < n; i++) {
            xxBar += (x[i] - xBar) * (x[i] - xBar);
            xyBar += (x[i] - xBar) * (y[i] - yBar);
        }
        double beta1 = xyBar / xxBar;
        double beta0 = yBar - beta1 * xBar;
        float y0 = (float) (beta1 * eval.getXValue(values, 0) + beta0);
        float y1 = (float) (beta1 * eval.getXValue(values, dataSetSize - 1) + beta0);
        Entry first = new Entry(minX, y0);
        Entry last = new Entry(maxX, y1);
        List<Entry> yValues = Arrays.asList(first, last);
        LineDataSet lineDataSet = new LineDataSet(yValues, "");
        lineDataSet.setColors(0xffff9100);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setDrawValues(false);
        lineDataSet.setLineWidth(1);
        lineDataSet.setHighlightEnabled(false);
        return lineDataSet;
    }

    private interface Evaluator {
        long getXValue(List<Pair<Float, LocalDateTime>> values, int i);

        String getXValueFormatted(float value);
    }

    private class ArrowStatisticAdapter extends RecyclerView.Adapter<ViewHolder> {

        private List<ArrowStatistic> data = new ArrayList<>();

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_simple, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bindItem(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public void setData(List<ArrowStatistic> data) {
            this.data = data;
            notifyDataSetChanged();
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private ItemImageSimpleBinding binding;
        private ArrowStatistic mItem;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setClickable(true);
            binding = DataBindingUtil.bind(itemView);
            binding.content.setOnClickListener(v -> onItemClicked());
        }

        private void onItemClicked() {
            DispersionPatternActivity.getIntent(mItem)
                    .withContext(StatisticsFragment.this)
                    .start();
        }

        void bindItem(@NonNull ArrowStatistic item) {
            mItem = item;
            binding.name.setText(
                    getString(R.string.arrow_x_of_set_of_arrows, item.getArrowNumber(), item
                            .getArrowName()));
            binding.image.setImageDrawable(new RoundedTextDrawable(item));
        }
    }
}