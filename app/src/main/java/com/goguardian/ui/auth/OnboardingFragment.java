package com.goguardian.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.goguardian.R;
import com.google.android.material.button.MaterialButton;

public class OnboardingFragment extends Fragment {

    public interface OnOnboardingCompleteListener {
        void onOnboardingComplete();
    }

    // Slide data
    private static final int[] ICONS = {
        R.drawable.ic_shield,
        R.drawable.ic_car_side,
        R.drawable.ic_warning
    };
    private static final String[] TITLES = {
        "Safety First",
        "Smart Booking",
        "Always Protected"
    };
    private static final String[] DESCS = {
        "Every driver is background-checked and safety-verified before they can accept rides on GoGuardian.",
        "AI Mode, Quiet Mode, Bid Your Price, Recurring Rides — ride your way, every time.",
        "One-tap SOS. Offline SMS alerts. Live location sharing. We've got your back, always."
    };
    private static final int[] TINTS = {
        R.color.primary,
        R.color.warning,
        R.color.secondary
    };

    private OnOnboardingCompleteListener listener;
    private ViewPager2 viewPager;
    private MaterialButton buttonNext;
    private View[] dots;
    private int currentPage = 0;
    private static final int PAGE_COUNT = 3;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnOnboardingCompleteListener) {
            listener = (OnOnboardingCompleteListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager   = view.findViewById(R.id.viewpager_onboarding);
        buttonNext  = view.findViewById(R.id.button_next);
        View skip   = view.findViewById(R.id.button_skip);

        dots = new View[]{
            view.findViewById(R.id.dot_0),
            view.findViewById(R.id.dot_1),
            view.findViewById(R.id.dot_2)
        };

        viewPager.setAdapter(new OnboardingAdapter());
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateDots(position);
                updateButton(position);
            }
        });

        buttonNext.setOnClickListener(v -> {
            if (currentPage < PAGE_COUNT - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                finish();
            }
        });

        skip.setOnClickListener(v -> finish());

        updateDots(0);
        updateButton(0);
    }

    private void updateDots(int page) {
        for (int i = 0; i < dots.length; i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dots[i].getLayoutParams();
            if (i == page) {
                lp.width = dpToPx(10);
                lp.height = dpToPx(10);
                dots[i].setAlpha(1f);
            } else {
                lp.width = dpToPx(8);
                lp.height = dpToPx(8);
                dots[i].setAlpha(0.4f);
            }
            dots[i].setLayoutParams(lp);
        }
    }

    private void updateButton(int page) {
        if (page == PAGE_COUNT - 1) {
            buttonNext.setText("GET STARTED");
            buttonNext.setIcon(null);
        } else {
            buttonNext.setText("NEXT");
            buttonNext.setIcon(requireContext().getDrawable(R.drawable.ic_arrow_forward));
        }
    }

    private void finish() {
        if (listener != null) listener.onOnboardingComplete();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────
    private class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageVH> {

        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new PageVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH holder, int position) {
            holder.icon.setImageResource(ICONS[position]);
            holder.icon.setColorFilter(requireContext().getColor(TINTS[position]));
            holder.title.setText(TITLES[position]);
            holder.desc.setText(DESCS[position]);
        }

        @Override
        public int getItemCount() { return PAGE_COUNT; }

        class PageVH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView title, desc;
            PageVH(View v) {
                super(v);
                icon  = v.findViewById(R.id.onboarding_icon);
                title = v.findViewById(R.id.onboarding_title);
                desc  = v.findViewById(R.id.onboarding_desc);
            }
        }
    }
}
