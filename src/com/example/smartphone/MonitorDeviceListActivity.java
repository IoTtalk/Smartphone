package com.example.smartphone;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;

import org.achartengine.GraphicalView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.example.smartphone.R;

public class MonitorDeviceListActivity extends Activity {
	
	ArrayList<String> device_list;
    ArrayAdapter device_list_adapter;
	
	private String msg_empty = "";
    
    int page = 0;
    String monitoring_device_name;
    final DecimalFormat six_digit_formater = new DecimalFormat("#0.000000");
    JSONObject newest_raw_data_copy = null;

    JSONObject meta_index;
    JSONArray  view_index;
    int timestep;
    
	private Handler message_handler = new Handler() {
	    public void handleMessage (Message msg) {
	    	// Do a reference of newest raw data, because the raw data will keep updating
	    	newest_raw_data_copy = MonitorDataThread.newest_raw_data;
	    	update_index();
	    	
	    }
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_monitor);

		meta_index = new JSONObject();
		view_index = new JSONArray();
		timestep = 0;
		
		msg_empty = getResources().getString(R.string.msg_empty);

		device_list = new ArrayList<String>();
		device_list.add(msg_empty);
        device_list_adapter = new ArrayAdapter(
            this, android.R.layout.simple_list_item_1, android.R.id.text1, device_list);
        
        ListView lv_online_device_list = (ListView) findViewById(R.id.lv_online_device_list);
        lv_online_device_list.setAdapter(device_list_adapter);
        
        lv_online_device_list.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    View view, int position, long id) {
            	
            	String selected_d_name = device_list.get(position);
            	
            	if ( !device_list.get(position).equals(msg_empty) && !device_list.get(position).equals(msg_empty)) {
            		
            		final LinearLayout ll_monitor_page1 = (LinearLayout) findViewById(R.id.ll_monitor_page1);
            		ll_monitor_page1.setVisibility(View.GONE);
            		page = 1;
            		
            		monitoring_device_name = device_list.get(position);
            		
            		/* hide all other device blocks */
        			LinearLayout wrapper_view = (LinearLayout) findViewById(R.id.ll_wrapper_view);
        			
        			Iterator<String> iter = meta_index.keys();
        			while (iter.hasNext()) {
        				String d_name = iter.next();
        				if ( d_name.equals(selected_d_name) ) {
        					wrapper_view.findViewWithTag(d_name).setVisibility(View.VISIBLE);
        				} else {
        					wrapper_view.findViewWithTag(d_name).setVisibility(View.GONE);
        				}
        				
        			}
                    
            	}
                
            }
        });
        
//		MonitorDataThread.set_device_list_handler(message_handler);
        Handler handler = new Handler() {
    	    public void handleMessage (Message msg) {
        		EasyConnect.DataSet ds = msg.getData().getParcelable("dataset");
        		JSONObject obj = (JSONObject)ds.newest().data;
				newest_raw_data_copy = obj;
				update_index();
    	    }
    	};
    	EasyConnect.subscribe("Display", handler);
		
	}
	
	@Override
	public void onBackPressed() {
		if (page == 1) {
			back_to_device_list();
		} else {
			finish();
		}
	}
	
	@Override
	public void onDestroy () {
		super.onDestroy();
    	EasyConnect.unsubscribe("Display");
		MonitorDataThread.set_device_list_handler(null);
	}
	
	public void update_index () {
		if (newest_raw_data_copy == null) {
			return;
		}
		
		if (newest_raw_data_copy.length() == 0) {
			if (meta_index.length() == 0) {
				device_list.clear();
			}
			
			if (device_list.size() == 0) {
				device_list.add( msg_empty );
	    		device_list_adapter.notifyDataSetChanged();
			}
			
		} else {
			if (device_list.size() == 1 && meta_index.length() == 0 ) {
				device_list.clear();
			}
			
		}
		
		Iterator<String> iter = newest_raw_data_copy.keys();
		
		while (iter.hasNext()) {
		    String d_name = iter.next();
    		try {
    			LayoutInflater mInflater = (LayoutInflater) getSystemService(MonitorDeviceListActivity.LAYOUT_INFLATER_SERVICE);
    			LinearLayout wrapper_view = (LinearLayout) findViewById(R.id.ll_wrapper_view);
    			
		    	if ( !(meta_index.has(d_name)) ) {
		    		/* ``d_name`` did not exist in meta_index, create one for it */
		    		JSONObject new_meta_record = new JSONObject();
		    		JSONObject new_view_record = new JSONObject();

		    		new_meta_record.put("features", new JSONObject());
		    		new_meta_record.put("index", view_index.length());
		    		
		    		new_view_record.put("features", new JSONArray());
		    		new_view_record.put("d_name", d_name);
		    		
		    		meta_index.put(d_name, new_meta_record);
		    		view_index.put(new_view_record);
		    		
		    		device_list.add(d_name);
		    		device_list_adapter.notifyDataSetChanged();

	    			/* new device block is needed as well */
	    		    View monitor_device_block = mInflater.inflate(R.layout.monitor_device_block, null);
	    		    TextView tv_device_name = (TextView) monitor_device_block.findViewById(R.id.tv_device_name);
	    		    tv_device_name.setText(d_name);
	    		    monitor_device_block.setTag(d_name);
	    		    wrapper_view.addView(monitor_device_block);
		    		
		    	}
	    	
		    	/* the device block is ready */
		    	int device_index = meta_index.getJSONObject(d_name).getInt("index");
		    	
		    	System.out.println( meta_index.toString(4) );
		    	System.out.println( view_index.toString(4) );
		    	
		    	/* now iterate over all features */
	    		Iterator<String> iter2 = newest_raw_data_copy.getJSONObject(d_name).keys();
	    		while (iter2.hasNext()) {
	    			
//	    			try {
		    			String f_name = iter2.next();
		    			
		    			if ( !(meta_index.getJSONObject(d_name).getJSONObject("features").has(f_name)) ) {
		    				/* ``f_name`` did not exist in meta_index['d_name']['features'], create one for it */
		    				JSONArray view_index_device_feature = view_index.getJSONObject(device_index).getJSONArray("features");
		    				
		    				JSONObject new_feature_view_record = new JSONObject();
			    			new_feature_view_record.put("f_name", f_name);
			    			
			    			/* add a GraphicalView here */
			    			int argc = 1;
			    			// many wrappings
			    			//Object a = newest_raw_data_copy.getJSONObject(d_name).getJSONArray(f_name).get(0);
			    			Object a = newest_raw_data_copy.getJSONObject(d_name).get(f_name);
			    			if (a instanceof Integer) {
			    				argc = 1;
			    			} else if (a instanceof JSONArray) {
			    				if (((JSONArray)a).get(0) instanceof JSONArray) {
			    					argc = ((JSONArray)a).getJSONArray(0).length();
			    				} else {
				    				argc = ((JSONArray)a).length();
			    				}
			    			}
			    			
			    			LineGraph line = new LineGraph(argc);
			    			GraphicalView view = line.getView(MonitorDeviceListActivity.this);
			    			new_feature_view_record.put("graph", line);
			    			new_feature_view_record.put("view", view);
			    			
			    			LinearLayout ll_device_block = (LinearLayout) wrapper_view.findViewWithTag(d_name);
			    			LinearLayout ll_device_features = (LinearLayout) ll_device_block.findViewById(R.id.ll_device_features);
			    			
			    			/* a line chart should follow a title (feature name) */
			    			View monitor_chart_wrapper = mInflater.inflate(R.layout.monitor_chart_wrapper, null);
			    			LinearLayout ll_chart_wrapper = (LinearLayout) monitor_chart_wrapper.findViewById(R.id.ll_chart_wrapper);
			    			ll_chart_wrapper.addView(view);
			    			TextView tv_feature_name = (TextView) monitor_chart_wrapper.findViewById(R.id.tv_feature_name);
			    			tv_feature_name.setText(f_name);
			    			ll_device_features.addView(monitor_chart_wrapper);
			    			
			    			meta_index.getJSONObject(d_name).getJSONObject("features").put(f_name, view_index_device_feature.length());
			    			view_index_device_feature.put(new_feature_view_record);				
		    			}
	    			
		    			/* now the LineGraph is ready, add point to it */
		    			int feature_index = meta_index.getJSONObject(d_name).getJSONObject("features").getInt(f_name);
		    			LineGraph line = (LineGraph)
		    					view_index.getJSONObject(device_index)
		    					.getJSONArray("features")
		    					.getJSONObject(feature_index)
		    					.get("graph");
		    			
		    			// many wrappings
		    			Object d = newest_raw_data_copy.getJSONObject(d_name).get(f_name);
		    			if (d instanceof JSONArray) {
		    				if (((JSONArray)d).get(0) instanceof JSONArray) {
		    					d = ((JSONArray)d).getJSONArray(0);
		    				}
		    			}
		    			line.addNewPoints(timestep , d);
		    			
		    			GraphicalView view = (GraphicalView)
		    					view_index.getJSONObject(device_index)
		    					.getJSONArray("features")
		    					.getJSONObject(feature_index)
		    					.get("view");
		    			
		    			view.repaint();
		    			
//	    			} catch (JSONException e) {
//	    				e.printStackTrace();
//	    			}
	    			
	    		}
	    		
	    		timestep += 1;
		    	
			} catch (JSONException e) {
				e.printStackTrace();
			}

		}

	}
	
	public void back_to_device_list () {
		final LinearLayout ll_monitor_page1 = (LinearLayout) findViewById(R.id.ll_monitor_page1);
		ll_monitor_page1.setVisibility(View.VISIBLE);
		page = 0;
		
	}
    
    static public void logging (String message) {
        Log.i(C.log_tag, "[MonitorDeviceListActivity] " + message);
    }
	
}
