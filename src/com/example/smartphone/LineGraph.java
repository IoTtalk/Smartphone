package com.example.smartphone;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

public class LineGraph {

	private GraphicalView view;

	final int[] available_colors = new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.YELLOW, Color.CYAN, Color.BLACK};
	final TimeSeries[] dataset = new TimeSeries[available_colors.length];
	final XYSeriesRenderer[] line_renderer = new XYSeriesRenderer[available_colors.length];
	
	XYMultipleSeriesDataset chart_dataset = new XYMultipleSeriesDataset();
	XYMultipleSeriesRenderer chart_renderer = new XYMultipleSeriesRenderer(); // Holds a collection of XYSeriesRenderer and customizes the graph
	int timestep;
	int chart_width = 40;
	int chart_padding = chart_width / 2;
	
	boolean y_min_max_set;
	double y_min;
	double y_max;
	
	public LineGraph () {
		for (int i = 0; i < available_colors.length; i++) {
			TimeSeries ts = new TimeSeries("x" + (i + 1));
			dataset[i] = ts;
			chart_dataset.addSeries(ts);
			
			XYSeriesRenderer xysr = new XYSeriesRenderer();
			line_renderer[i] = xysr;
			xysr.setColor(available_colors[i]);
			xysr.setPointStyle(PointStyle.SQUARE);
			xysr.setFillPoints(true);
		}
		
		chart_renderer.setXTitle("Time step");
		
		// colors
		chart_renderer.setApplyBackgroundColor(true);
		chart_renderer.setBackgroundColor(Color.WHITE);
		chart_renderer.setMarginsColor(Color.LTGRAY);
		chart_renderer.setAxesColor(Color.BLACK);
		
		chart_renderer.setXLabelsColor(Color.BLACK);
		chart_renderer.setYLabelsColor(0, Color.BLACK);
		chart_renderer.setYTitle("Value"); // can't find axis title text color
		
		// text sizes
		chart_renderer.setAxisTitleTextSize(20);
		chart_renderer.setLabelsTextSize(20);
		
		for (int i = 0; i < available_colors.length; i++) {
		// Add single renderer to multiple renderer
			chart_renderer.addSeriesRenderer(line_renderer[i]);
		}
		chart_renderer.setZoomEnabled(false);
		chart_renderer.setPanEnabled(false);
		
		y_min_max_set = false;
		
		timestep = 0;
	}
	
	public GraphicalView getView (Context context) {
		view =  ChartFactory.getLineChartView(context, chart_dataset, chart_renderer);
		return view;
	}
	
	public void add_new_point (JSONArray data) throws JSONException {
		if (data.length() == 0) {
			logging("No input data?");
			return;
		}
		
		if (data.length() == 1
				&& data.get(0) instanceof JSONObject ) {
			JSONObject tmp = data.getJSONObject(0);
			if (tmp.length() == 1) {
				String single_key = tmp.keys().next();
				if (tmp.get(single_key) instanceof JSONArray) {
					data = tmp.getJSONArray(single_key);
				} else {
					logging("Input data too complicated");
					return;
				}
			} else {
				logging("Input data too complicated");
				return;
			}
		}
		
		if (data.length() > available_colors.length) {
			logging("Input data dimension too high, abort");
			return;
		}
			
		for (int i = 0; i < data.length(); i++) {
			double data_point = ((JSONArray)data).getDouble(i);
			dataset[i].add(timestep, data_point);
			if (!y_min_max_set) {
				y_min = data_point;
				y_max = data_point;
				chart_renderer.setYAxisMin(y_min);
				chart_renderer.setYAxisMax(y_max);
				y_min_max_set = true;
			} else if (y_min > data_point) {
				y_min = data_point;
				chart_renderer.setYAxisMin(y_min);
			} else if (data_point > y_max) {
				y_max = data_point;
				chart_renderer.setYAxisMax(y_max);
			}
		}
		
		timestep += 1;
		
		if (timestep < chart_padding) {
			chart_renderer.setXAxisMin(0);
			chart_renderer.setXAxisMax(chart_width);
			
		} else {
			chart_renderer.setXAxisMin(timestep - chart_padding);
			chart_renderer.setXAxisMax(timestep + chart_padding);
		}
	}
	
	public void reset_y_max () {
		y_min_max_set = false;
	}
    
    static public void logging (String message) {
        Log.i(Constants.log_tag, "[LineGraph] " + message);
    }
	
}
