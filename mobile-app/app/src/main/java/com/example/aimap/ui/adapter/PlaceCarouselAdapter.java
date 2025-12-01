package com.example.aimap.ui.adapter;

import android.content.Intent;
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
import android.location.Location; // Import Location class

public class PlaceCarouselAdapter extends RecyclerView.Adapter<PlaceCarouselAdapter.PlaceViewHolder> {

    private JSONArray places;
    private Location userLocation; // Thêm biến lưu vị trí người dùng

    public PlaceCarouselAdapter(JSONArray places, Location userLocation) {
        this.places = places;
        this.userLocation = userLocation; // Gán vị trí người dùng
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_suggested_place, parent, false);
        
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = (int) (parent.getContext().getResources().getDisplayMetrics().widthPixels * 0.85);
        view.setLayoutParams(lp);
        
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        try {
            JSONObject place = places.getJSONObject(position);
            holder.bind(place, userLocation); // Truyền userLocation vào bind
        } catch (Exception e) {
            Log.e("PlaceCarouselAdapter", "Error binding place at position " + position, e);
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

        void bind(JSONObject json, Location userLoc) { // Nhận userLocation
            try {
                String name = json.optString("name", "Địa điểm");
                String address = json.optString("address", "Không có địa chỉ");
                double lat = json.optDouble("lat", 0);
                double lon = json.optDouble("lon", 0);

                tvPlaceName.setText(name);
                tvPlaceAddress.setText(address);

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

                btnDirection.setOnClickListener(v -> {
                    String uriStr = String.format(Locale.US, "geo:%f,%f?q=%f,%f(%s)", lat, lon, lat, lon, name);
                    Uri gmmIntentUri = Uri.parse(uriStr);
                    Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                    mapIntent.setPackage("com.google.android.apps.maps");
                    
                    if (mapIntent.resolveActivity(v.getContext().getPackageManager()) != null) {
                        v.getContext().startActivity(mapIntent);
                    } else {
                         String browserUriStr = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f", lat, lon);
                         Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(browserUriStr));
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
