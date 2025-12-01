package com.example.aimap.ui;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.R;
import com.example.aimap.ui.adapter.PlacesListAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONException;

public class PlacesBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_JSON_DATA = "json_data";
    private static final String ARG_USER_LAT = "user_lat";
    private static final String ARG_USER_LON = "user_lon";

    private String jsonData;
    private Location userLocation;

    public static PlacesBottomSheetFragment newInstance(String jsonData, Location userLocation) {
        PlacesBottomSheetFragment fragment = new PlacesBottomSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JSON_DATA, jsonData);
        if (userLocation != null) {
            args.putDouble(ARG_USER_LAT, userLocation.getLatitude());
            args.putDouble(ARG_USER_LON, userLocation.getLongitude());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            jsonData = getArguments().getString(ARG_JSON_DATA);
            if (getArguments().containsKey(ARG_USER_LAT)) {
                userLocation = new Location("provider");
                userLocation.setLatitude(getArguments().getDouble(ARG_USER_LAT));
                userLocation.setLongitude(getArguments().getDouble(ARG_USER_LON));
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_places_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewPlaces);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        if (jsonData != null) {
            // Logic sửa lỗi JSON bị cắt cụt
            String safeJson = jsonData.trim();
            if (!safeJson.endsWith("]")) {
                int lastBrace = safeJson.lastIndexOf("}");
                if (lastBrace != -1) {
                    safeJson = safeJson.substring(0, lastBrace + 1) + "]";
                } else {
                    safeJson = "[]";
                }
            }

            try {
                JSONArray placesArray = new JSONArray(safeJson);
                PlacesListAdapter adapter = new PlacesListAdapter(placesArray, userLocation);
                recyclerView.setAdapter(adapter);
            } catch (JSONException e) {
                Log.e("PlacesBottomSheet", "Error parsing places JSON: " + e.getMessage());
            }
        }
    }
}
