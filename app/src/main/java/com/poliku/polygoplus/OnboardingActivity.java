package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.poliku.polygoplus.data.AppDataStore;

public class OnboardingActivity extends AppCompatActivity {
    private static final String[] TITLES = {
            "Exclusive to PKS",
            "Buy and sell on campus",
            "Real-time chat",
            "Meet safely"
    };
    private static final String[] BODIES = {
            "PolyGo+ is only for Politeknik Kuching Sarawak students and staff. Verified campus IDs keep the marketplace trusted.",
            "Browse food, tech, books and more from people around you. No need to leave campus to complete a deal.",
            "Message sellers instantly, agree a price, and keep the conversation in one place.",
            "Meet at PKS landmarks like Block A or the cafeteria. Mark the deal complete and leave a review."
    };
    private static final String[] EMOJIS = { "🎓", "🛍️", "💬", "📍" };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        ViewPager2 pager = findViewById(R.id.onboardingPager);
        pager.setAdapter(new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false);
                return new Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                holder.emoji.setText(EMOJIS[position]);
                holder.title.setText(TITLES[position]);
                holder.body.setText(BODIES[position]);
            }

            @Override
            public int getItemCount() {
                return TITLES.length;
            }
        });

        com.google.android.material.tabs.TabLayout tabLayoutDots = findViewById(R.id.tabLayoutDots);
        new TabLayoutMediator(tabLayoutDots, pager, (tab, position) -> {}).attach();

        findViewById(R.id.btnOnboardingNext).setOnClickListener(v -> {
            if (pager.getCurrentItem() < TITLES.length - 1) pager.setCurrentItem(pager.getCurrentItem() + 1);
            else finishOnboarding();
        });
        findViewById(R.id.btnOnboardingSkip).setOnClickListener(v -> finishOnboarding());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                ((com.google.android.material.button.MaterialButton) findViewById(R.id.btnOnboardingNext))
                        .setText(position == TITLES.length - 1 ? "Get started" : "Next");
            }
        });
    }

    private void finishOnboarding() {
        AppDataStore.setOnboardingSeen(this);
        // Direct to Home regardless of login status
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView emoji, title, body;

        Holder(View itemView) {
            super(itemView);
            emoji = itemView.findViewById(R.id.tvOnboardEmoji);
            title = itemView.findViewById(R.id.tvOnboardTitle);
            body = itemView.findViewById(R.id.tvOnboardBody);
        }
    }
}
