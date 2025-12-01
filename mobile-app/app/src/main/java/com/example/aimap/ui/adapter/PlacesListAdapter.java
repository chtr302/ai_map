package com.example.aimap.ui.adapter;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class PlacesListAdapter extends RecyclerView.Adapter<PlacesListAdapter.PlaceViewHolder> {

    private JSONArray places;
    private Location userLocation;

    public PlacesListAdapter(JSONArray places, Location userLocation) {
        this.places = places;
        this.userLocation = userLocation;
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout cũ, nhưng width sẽ tự match_parent do RecyclerView vertical
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_suggested_place, parent, false);
        
        // Đảm bảo width match_parent, margin hợp lý
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 8, 0, 16); // Margin trên dưới
        view.setLayoutParams(lp);
        
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        try {
            JSONObject place = places.getJSONObject(position);
            holder.bind(place, userLocation);
        } catch (Exception e) {
            Log.e("PlacesListAdapter", "Error binding place at position " + position, e);
        }
    }

    @Override
    public int getItemCount() {
        return places != null ? places.length() : 0;
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        TextView tvPlaceName, tvPlaceAddress, tvPlaceDistance;
        Button btnDirection;

        PlaceViewHolder(View itemView) {
            super(itemView);
            tvPlaceName = itemView.findViewById(R.id.tvPlaceName);
            tvPlaceAddress = itemView.findViewById(R.id.tvPlaceAddress);
            tvPlaceDistance = itemView.findViewById(R.id.tvPlaceDistance);
            btnDirection = itemView.findViewById(R.id.btnDirection);
        }

        void bind(JSONObject json, Location userLoc) {
            try {
                String name = json.optString("name", "Địa điểm");
                String address = json.optString("address", "Không có địa chỉ");
                double lat = json.optDouble("lat", 0);
                double lon = json.optDouble("lon", 0);

                tvPlaceName.setText(name);
                tvPlaceAddress.setText(address);

                // Tính khoảng cách
                if (userLoc != null && lat != 0 && lon != 0) {
                    float[] results = new float[1];
                    Location.distanceBetween(userLoc.getLatitude(), userLoc.getLongitude(), lat, lon, results);
                    float distanceInMeters = results[0];
                    String distanceText;
                    if (distanceInMeters < 1000) {
                        distanceText = String.format(Locale.US, "Cách bạn %.0f m", distanceInMeters);
                    } else {
                        distanceText = String.format(Locale.US, "Cách bạn %.1f km", distanceInMeters / 1000);
                    }
                    tvPlaceDistance.setText(distanceText);
                    tvPlaceDistance.setVisibility(View.VISIBLE);
                } else {
                    tvPlaceDistance.setVisibility(View.GONE);
                }

                // Nút chỉ đường: Dùng Google Maps Universal URL để tìm kiếm theo tên + địa chỉ
                btnDirection.setOnClickListener(v -> {
                    String query = name + " " + address;
                    String encodedQuery = Uri.encode(query);
                    String url = "https://www.google.com/maps/search/?api=1&query=" + encodedQuery;
                    
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.setPackage("com.google.android.apps.maps"); // Ưu tiên app Maps

                    if (intent.resolveActivity(v.getContext().getPackageManager()) != null) {
                        v.getContext().startActivity(intent);
                    } else {
                        // Fallback ra trình duyệt nếu không có app Maps
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        v.getContext().startActivity(browserIntent);
                    }
                });

            } catch (Exception e) {
                Log.e("PlaceViewHolder", "Error parsing JSON metadata", e);
                tvPlaceName.setText("Lỗi hiển thị địa điểm");
            }
        }
    }
}
