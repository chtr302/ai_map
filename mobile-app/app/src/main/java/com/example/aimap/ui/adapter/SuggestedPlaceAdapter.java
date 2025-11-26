package com.example.aimap.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.aimap.data.SuggestedPlace;
import com.example.aimap.R;
import java.util.List;
import java.util.Locale;

public class SuggestedPlaceAdapter extends RecyclerView.Adapter<SuggestedPlaceAdapter.PlaceViewHolder> {
    private List<SuggestedPlace> places;
    private double userLat, userLng;
    private OnDirectionClickListener directionClickListener;

    public interface OnDirectionClickListener {
        void onDirectionClick(SuggestedPlace place);
    }

    public SuggestedPlaceAdapter(List<SuggestedPlace> places, double userLat, double userLng, OnDirectionClickListener listener) {
        this.places = places;
        this.userLat = userLat;
        this.userLng = userLng;
        this.directionClickListener = listener;
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_suggested_place, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        SuggestedPlace place = places.get(position);
        holder.tvName.setText(place.getName());
        holder.tvAddress.setText(place.getAddress());

        float[] results = new float[1];
        android.location.Location.distanceBetween(
                userLat, userLng,
                place.getLatitude(), place.getLongitude(), results);
        float distanceKm = results[0] / 1000f;
        holder.tvDistance.setText(String.format(Locale.US, "Cách bạn %.1f km", distanceKm));

        holder.btnDirection.setOnClickListener(v -> {
            if (directionClickListener != null)
                directionClickListener.onDirectionClick(place);
        });
    }

    @Override
    public int getItemCount() { return places.size(); }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvDistance;
        Button btnDirection;
        public PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPlaceName);
            tvAddress = itemView.findViewById(R.id.tvPlaceAddress);
            tvDistance = itemView.findViewById(R.id.tvPlaceDistance);
            btnDirection = itemView.findViewById(R.id.btnDirection);
        }
    }
}
