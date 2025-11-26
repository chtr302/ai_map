package com.example.aimap.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.aimap.R;
import com.example.aimap.data.SuggestedPlace;
import com.example.aimap.ui.adapter.SuggestedPlaceAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SuggestedPlacesFragment extends Fragment {
    private double userLat = 10.777233, userLng = 106.695864;

    // Tạo fragment với vị trí truyền vào
    public static SuggestedPlacesFragment newInstance(double lat, double lng) {
        SuggestedPlacesFragment fragment = new SuggestedPlacesFragment();
        Bundle args = new Bundle();
        args.putDouble("lat", lat);
        args.putDouble("lng", lng);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_suggested_places, container, false);

        // Lấy lại tọa độ user từ bundle
        if (getArguments() != null) {
            userLat = getArguments().getDouble("lat", userLat);
            userLng = getArguments().getDouble("lng", userLng);
        }

        RecyclerView recyclerView = root.findViewById(R.id.recyclerViewPlaces);

        List<SuggestedPlace> placeList = new ArrayList<>();
        placeList.add(new SuggestedPlace("Công viên Tao Đàn", "Nguyễn Thị Minh Khai, Quận 1", 10.770325, 106.689172));
        placeList.add(new SuggestedPlace("Chợ Bến Thành", "Lê Lợi, Quận 1", 10.772730, 106.698220));
        placeList.add(new SuggestedPlace("Nhà thờ Đức Bà", "Công xã Paris, Quận 1", 10.779783, 106.699073));

        SuggestedPlaceAdapter adapter = new SuggestedPlaceAdapter(
                placeList, userLat, userLng,
                place -> {
                    String uri = String.format(
                            Locale.US,
                            "http://maps.google.com/maps?saddr=%f,%f&daddr=%f,%f",
                            userLat, userLng,
                            place.getLatitude(), place.getLongitude());
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");
                    startActivity(intent);
                }
        );
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        ImageButton buttonClose = root.findViewById(R.id.buttonClose);
        buttonClose.setOnClickListener(v -> {
            // Đóng fragment, KHÔNG set visibility container!
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        return root;
    }
}
