package com.example.smartphone;

import java.util.ArrayList;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.graphics.Color;

public class LineGraph {

	private GraphicalView view;

	private ArrayList<TimeSeries> dataset;
	private ArrayList<XYSeriesRenderer> rendererset;
	private int lines;
	private int[] colors = new int[]{Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.YELLOW, Color.CYAN};
	
	private XYMultipleSeriesDataset mDataset = new XYMultipleSeriesDataset();
	private XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer(); // Holds a collection of XYSeriesRenderer and customizes the graph
	
	public LineGraph(int lines)
	{
		dataset = new ArrayList<TimeSeries>();
		rendererset = new ArrayList<XYSeriesRenderer>();
		this.lines = lines;
		
		for (int i = 1; i <= this.lines; i++) {
			TimeSeries ts = new TimeSeries("x" + i);
			dataset.add(ts);
			mDataset.addSeries(ts);
			
			XYSeriesRenderer xysr = new XYSeriesRenderer();
			rendererset.add(xysr);
			xysr.setColor(colors[i-1]);
			xysr.setPointStyle(PointStyle.SQUARE);
			xysr.setFillPoints(true);
		}
		
		mRenderer.setXTitle("Time step");
		
		// colors
		mRenderer.setApplyBackgroundColor(true);
		mRenderer.setBackgroundColor(Color.WHITE);
		mRenderer.setMarginsColor(Color.LTGRAY);
		mRenderer.setAxesColor(Color.BLACK);
		
		mRenderer.setXLabelsColor(Color.BLACK);
		mRenderer.setYLabelsColor(0, Color.BLACK);
		mRenderer.setYTitle("Value"); // can't find axis title text color
		
		// text sizes
		mRenderer.setAxisTitleTextSize(20);
		mRenderer.setLabelsTextSize(20);
		
		for (int i = 0; i < this.lines; i++) {
		// Add single renderer to multiple renderer
			mRenderer.addSeriesRenderer(rendererset.get(i));
		}
	}
	
	public GraphicalView getView(Context context) 
	{
		view =  ChartFactory.getLineChartView(context, mDataset, mRenderer);
		return view;
	}
	
	private int chart_width = 40;
	private int chart_padding = chart_width / 2;
	
	public void addNewPoints(int timestep, Object data)
	{
		if (data instanceof Integer && this.lines == 1) {
			dataset.get(0).add(timestep, (int)data);
			
		} else if (data instanceof JSONArray && this.lines <= ((JSONArray)data).length() ) {
			/* I'm not sure if this behavior makes sense: */
			/* 	"if input data is more than you declared, ignore the rest" */
			for (int i = 0; i < this.lines; i++) {
				try {
					dataset.get(i).add(timestep, ((JSONArray)data).getDouble(i) );
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			
		}
		
		if (timestep < chart_padding) {
			mRenderer.setXAxisMin(0);
			mRenderer.setXAxisMax(chart_width);
			
		} else {
			mRenderer.setXAxisMin(timestep - chart_padding);
			mRenderer.setXAxisMax(timestep + chart_padding);
			
		}
	}
	
}
