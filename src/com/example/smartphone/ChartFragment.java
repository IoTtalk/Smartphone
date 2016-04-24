package com.example.smartphone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.achartengine.GraphicalView;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class ChartFragment extends Fragment {
	static final String local_tag = ChartFragment.class.getSimpleName();
	
	FeatureActivity coordinator;
	View root_view;
	final ArrayList<FeatureMetaData> feature_metadata = new ArrayList<FeatureMetaData>();
	FeatureMetaDataListAdapter adapter;
	PAGE current_page;
	String watching_d_name;
	String watching_f_name;
	GraphicalView chart_view;
	final LineGraph line_graph = new LineGraph();
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        root_view = inflater.inflate(R.layout.frag_chart, container, false);
        
        final ListView lv_available_ec_endpoints = (ListView) root_view.findViewById(R.id.lv_feature_list);
        adapter = new FeatureMetaDataListAdapter(getActivity(), feature_metadata);
        lv_available_ec_endpoints.setAdapter(adapter);
        lv_available_ec_endpoints.setEmptyView((TextView) root_view.findViewById(R.id.tv_empty));
        lv_available_ec_endpoints.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent,
                    	View view, int position, long id) {
                show_page(PAGE.DATA);
                watching_d_name = feature_metadata.get(position).d_name;
                watching_f_name = feature_metadata.get(position).f_name;
            	((TextView) root_view.findViewById(R.id.tv_chart_d_name)).setText(watching_d_name);
            	((TextView) root_view.findViewById(R.id.tv_chart_f_name)).setText(watching_f_name);
            	line_graph.reset_y_max();
            }
        });

        show_page(PAGE.LIST);
        
        chart_view = line_graph.getView(getActivity());
		((LinearLayout) root_view.findViewById(R.id.v_chart_wrapper)).addView(chart_view);
		
        return root_view;
    }
	
	enum PAGE {
		LIST, DATA,
	}
	
	public PAGE current_page () {
		return current_page;
	}
	
	public void show_page (PAGE page) {
		current_page = page;
		switch (current_page) {
		case LIST:
			root_view.findViewById(R.id.ll_feature_list_wrapper).setVisibility(View.VISIBLE);
			root_view.findViewById(R.id.ll_feature_data_wrapper).setVisibility(View.GONE);
			break;
		case DATA:
			root_view.findViewById(R.id.ll_feature_list_wrapper).setVisibility(View.GONE);
			root_view.findViewById(R.id.ll_feature_data_wrapper).setVisibility(View.VISIBLE);
			break;
		}
		
		final ActionBar actionbar = getActivity().getActionBar();
		actionbar.setHomeButtonEnabled(current_page == PAGE.DATA);
		actionbar.setDisplayHomeAsUpEnabled(current_page == PAGE.DATA);
	}
	
	public void set_newest_metadata (JSONObject newest_data) {
		feature_metadata.clear();
	    try {
			Iterator<String> d_name_iter = newest_data.keys();
			while (d_name_iter.hasNext()) {
			    String d_name = d_name_iter.next();
			    feature_metadata.add(new FeatureMetaData(d_name));
			    JSONObject feature_data = newest_data.getJSONObject(d_name);
			    Iterator<String> f_name_iter = feature_data.keys();
			    ArrayList<String> features = new ArrayList<String>();
			    while (f_name_iter.hasNext()) {
			    	features.add(f_name_iter.next());
			    }
			    String[] feature_array = new String[features.size()];
			    features.toArray(feature_array);
			    Arrays.sort(feature_array);
			    for (String f_name: feature_array) {
				    feature_metadata.add(new FeatureMetaData(d_name, f_name));
				    if (d_name.equals(watching_d_name) && f_name.equals(watching_f_name)) {
						line_graph.add_new_point(feature_data.getJSONArray(f_name));
						chart_view.repaint();
				    }
			    }
			}
	    } catch (JSONException e) {
	    	Utils.logging(local_tag, "JSONException");
            watching_f_name = "";
	    }
	    
		adapter.notifyDataSetChanged();
	}
	
	public class FeatureMetaData {
		boolean is_d_name;
		String d_name;
		String f_name;
		
		public FeatureMetaData (String d_name) {
			this.is_d_name = true;
			this.d_name = d_name;
		}
		
		public FeatureMetaData (String d_name, String f_name) {
			this.is_d_name = false;
			this.d_name = d_name;
			this.f_name = f_name;
		}
	}

	public class FeatureMetaDataListAdapter extends BaseAdapter {
	    Context context;
	    ArrayList<FeatureMetaData> data = null;
	    
	    public FeatureMetaDataListAdapter (Context context, ArrayList<FeatureMetaData> data) {
	        this.context = context;
	        this.data = data;
	    }
	    
	    @Override
	    public View getView (int position, View convertView, ViewGroup parent) {
	        View row = convertView;
	        FeatureMetaDataHolder holder = null;
	        FeatureMetaData i = data.get(position);
	        
	        if(row == null) {
	            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
	            holder = new FeatureMetaDataHolder();
	            row = inflater.inflate(R.layout.item_metadata, parent, false);
	            holder.tv_d_name = (TextView) row.findViewById(R.id.tv_item_d_name);
	            holder.tv_f_name = (TextView) row.findViewById(R.id.tv_item_f_name);
	            row.setTag(holder);
	        } else {
	            holder = (FeatureMetaDataHolder) row.getTag();
	        }
	        
	        if (i.is_d_name) {
	        	holder.tv_d_name.setVisibility(View.VISIBLE);
	        	holder.tv_f_name.setVisibility(View.GONE);
	        	holder.tv_d_name.setText(i.d_name);
	        } else {
	        	holder.tv_d_name.setVisibility(View.GONE);
	        	holder.tv_f_name.setVisibility(View.VISIBLE);
	        	holder.tv_f_name.setText(i.f_name);
	        }
	        return row;
	    }
	    
	    @Override
	    public boolean isEnabled(int position) {
	        return !data.get(position).is_d_name;
	    }
	
	    class FeatureMetaDataHolder {
	    	TextView tv_d_name;
	        TextView tv_f_name;
	    }

		@Override
		public int getCount() {
			return data.size();
		}

		@Override
		public Object getItem(int position) {
			return data.get(position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}
	}
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
        	coordinator = (FeatureActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement xxx");
        }
    }
}