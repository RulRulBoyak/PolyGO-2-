package com.poliku.polygoplus.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.databinding.FragmentHomeBinding;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.HomeViewModel;
import com.poliku.polygoplus.ui.HapticManager;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ProductCardAdapter productAdapter;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppDataStore.initialize(requireContext());
        setupHeader();
        setupEventCarousel();
        setupProducts();
        setupSearchActions();
        
        observeViewModel();
        
        binding.swipeRefreshHome.setColorSchemeResources(R.color.pks_blue);
        binding.swipeRefreshHome.setOnRefreshListener(() -> {
            HapticManager.mediumTap(binding.swipeRefreshHome);
            viewModel.loadProducts();
        });
        
        viewModel.loadProducts();
    }

    private void observeViewModel() {
        viewModel.products.observe(getViewLifecycleOwner(), list -> {
            if (productAdapter != null) productAdapter.updateData(list);
            if (binding != null) {
                boolean isEmpty = list == null || list.isEmpty();
                binding.recyclerViewProducts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            if (binding == null) return;
            if (isLoading) {
                if (!binding.swipeRefreshHome.isRefreshing()) {
                    binding.shimmerMarket.shimmerView.setVisibility(View.VISIBLE);
                    binding.shimmerMarket.shimmerView.startShimmer();
                    binding.recyclerViewProducts.setVisibility(View.GONE);
                }
            } else {
                binding.swipeRefreshHome.setRefreshing(false);
                binding.shimmerMarket.shimmerView.stopShimmer();
                binding.shimmerMarket.shimmerView.setVisibility(View.GONE);
            }
        });
    }

    private void setupHeader() {
        boolean loggedIn = AppDataStore.isLoggedIn(requireContext());
        String name = AppDataStore.userName(requireContext());
        String firstName = name.split(" ")[0];
        
        String hourGreeting;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) hourGreeting = getString(R.string.greeting_morning);
        else if (hour < 18) hourGreeting = getString(R.string.greeting_afternoon);
        else hourGreeting = getString(R.string.greeting_evening);

        if (!loggedIn || name == null || name.trim().isEmpty() || "PolyGo member".equals(name)) {
            binding.tvGreeting.setText(hourGreeting + "!");
        } else {
            binding.tvGreeting.setText(hourGreeting + ",\n" + firstName);
        }

        String photo = AppDataStore.userProfilePic(requireContext());
        if (!photo.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(photo)
                    .circleCrop()
                    .into(binding.ivProfilePic);
        }

        binding.ivProfilePic.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            if (AppDataStore.isLoggedIn(requireContext())) {
                Intent intent = new Intent(requireContext(), AccountActivity.class);
                androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(), binding.ivProfilePic, "profile_pic_hero"
                );
                startActivity(intent, options.toBundle());
            } else {
                startActivity(new Intent(requireContext(), com.poliku.polygoplus.LoginActivity.class));
            }
        });
    }

    private void setupEventCarousel() {
        List<Event> events = new ArrayList<>();
        events.add(new Event("Digital Career Fair 2026", "Join the biggest tech event on campus", R.drawable.bg_home_header, "Career"));
        events.add(new Event("Campus Night Market", "Support student entrepreneurs this Friday", R.drawable.bg_home_header, "Food"));
        events.add(new Event("Book Exchange Program", "Swap your old textbooks for new ones", R.drawable.bg_home_header, "Education"));

        EventAdapter adapter = new EventAdapter(events);
        binding.eventCarousel.setAdapter(adapter);
        
        new TabLayoutMediator(binding.carouselIndicator, binding.eventCarousel, (tab, position) -> {}).attach();
        
        binding.eventCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                HapticManager.lightTap(binding.eventCarousel);
            }
        });
    }

    private void setupProducts() {
        productAdapter = new ProductCardAdapter(new ArrayList<>(), (adapter, product, sharedView) -> {
            Intent intent = new Intent(requireContext(), ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), sharedView, "product_image_hero"
            );
            startActivity(intent, options.toBundle());
        });
        binding.recyclerViewProducts.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.recyclerViewProducts.setAdapter(productAdapter);
    }

    private void setupSearchActions() {
        View.OnClickListener openSearch = v -> {
            HapticManager.lightTap(v);
            Intent intent = new Intent(requireContext(), SearchActivity.class);
            androidx.core.app.ActivityOptionsCompat options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), binding.searchBarCard, "search_bar_hero"
            );
            startActivity(intent, options.toBundle());
        };
        binding.searchBarCard.setOnClickListener(openSearch);
        binding.tvSearchVisual.setOnClickListener(v -> {
            HapticManager.swell(requireContext());
            startActivity(new Intent(requireContext(), com.poliku.polygoplus.AiDiscoveryActivity.class));
        });
        binding.btnTextbookHub.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            startActivity(new Intent(requireContext(), com.poliku.polygoplus.TextbookHubActivity.class));
        });
        binding.tvSeeAllProducts.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            HapticManager.lightTap(v);
            startActivity(new Intent(requireContext(), SearchActivity.class));
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public static class Event {
        String title, subtitle, tag;
        int imageRes;

        Event(String title, String subtitle, int imageRes, String tag) {
            this.title = title;
            this.subtitle = subtitle;
            this.imageRes = imageRes;
            this.tag = tag;
        }
    }

    private static class EventAdapter extends RecyclerView.Adapter<EventAdapter.ViewHolder> {
        private final List<Event> events;

        EventAdapter(List<Event> events) {
            this.events = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Event event = events.get(position);
            ((android.widget.TextView) holder.itemView.findViewById(R.id.tvEventTitle)).setText(event.title);
            ((android.widget.TextView) holder.itemView.findViewById(R.id.tvEventSubtitle)).setText(event.subtitle);
            ((android.widget.TextView) holder.itemView.findViewById(R.id.tvEventTag)).setText(event.tag);
            holder.itemView.setOnClickListener(v -> {
                HapticManager.swell(v.getContext());
                v.getContext().startActivity(new Intent(v.getContext(), com.poliku.polygoplus.CampusPulseActivity.class));
            });
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }

}
