package com.example.smartphone;

import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DisplayFeatureListFragment extends Fragment {
	View root_view;
	final ArrayList<FeatureMetaData> feature_metadata = new ArrayList<FeatureMetaData>();
	FeatureMetaDataListAdapter adapter;
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        root_view = inflater.inflate(R.layout.frag_feature_list, container, false);
        
        final ListView lv_available_ec_endpoints = (ListView) root_view.findViewById(R.id.lv_feature_list);
        adapter = new FeatureMetaDataListAdapter(getActivity(), feature_metadata);
        lv_available_ec_endpoints.setAdapter(adapter);
        lv_available_ec_endpoints.setEmptyView((TextView) root_view.findViewById(R.id.tv_empty));
        return root_view;
    }
	
	public void set_newest_metadata (JSONObject newest_data) {
		feature_metadata.clear();
	    try {
			Iterator<String> d_name_iter = newest_data.keys();
			while (d_name_iter.hasNext()) {
			    String d_name = d_name_iter.next();
			    feature_metadata.add(new FeatureMetaData(d_name));
			    Iterator<String> f_name_iter = newest_data.getJSONObject(d_name).keys();
			    while (f_name_iter.hasNext()) {
			    	String f_name = f_name_iter.next();
				    feature_metadata.add(new FeatureMetaData(d_name, f_name));
			    }
			}
	    } catch (JSONException e) {
	        // Something went wrong!
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
		        if (i.is_d_name) {
		            row = inflater.inflate(R.layout.item_metadata_d_name, parent, false);
		            holder.tv_d_name = (TextView) row.findViewById(R.id.tv_d_name);
		        } else {
		            row = inflater.inflate(R.layout.item_metadata_f_name, parent, false);
		            holder.tv_f_name = (TextView) row.findViewById(R.id.tv_f_name);
		        }
	            row.setTag(holder);
	        } else {
	            holder = (FeatureMetaDataHolder) row.getTag();
	        }
	        
	        if (i.is_d_name) {
	        	holder.tv_d_name.setText(i.d_name);
	        } else {
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
}