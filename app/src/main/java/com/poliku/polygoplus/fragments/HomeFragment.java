package com.poliku.polygoplus.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.snackbar.Snackbar;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.AiDiscoveryActivity;
import com.poliku.polygoplus.CampusPulseActivity;
import com.poliku.polygoplus.LoginActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.TextbookHubActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.util.Resource;
import com.poliku.polygoplus.databinding.FragmentHomeBinding;
import androidx.lifecycle.ViewModelProvider;
import com.poliku.polygoplus.viewmodel.HomeViewModel;
import com.poliku.polygoplus.ui.HapticManager;
import com.poliku.polygoplus.ui.UiUtils;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ProductCardAdapter productAdapter;
    private HomeViewModel viewModel;
    private ActivityResultLauncher<Intent> detailLauncher;
    @Nullable private View lastProductSharedElement;

    private static final long AUTO_ROTATE_MS = 4000L;
    private final Handler autoRotateHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRotateRunnable = new Runnable() {
        @Override
        public void run() {
            ViewPager2 pager = binding == null ? null : binding.eventCarousel;
            if (pager != null && pager.getAdapter() != null && pager.getAdapter().getItemCount() > 1) {
                int next = (pager.getCurrentItem() + 1) % pager.getAdapter().getItemCount();
                pager.setCurrentItem(next, true);
            }
            startAutoRotate();
        }
    };

    private void startAutoRotate() {
        autoRotateHandler.removeCallbacks(autoRotateRunnable);
        autoRotateHandler.postDelayed(autoRotateRunnable, AUTO_ROTATE_MS);
    }

    private void stopAutoRotate() {
        autoRotateHandler.removeCallbacks(autoRotateRunnable);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        return binding.getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        detailLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                String changedId = result.getData().getStringExtra(ProductDetailActivity.RESULT_EXTRA_LISTING_ID);
                if (changedId != null) {
                    viewModel.loadProducts(); // Refresh the list if an item state changed
                }
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AppDataStore.initialize(requireContext());
        setupHeader();
        setupEventCarousel();
        setupProducts();
        setupCategoryRow();
        setupPicksRail();
        setupSearchActions();
        setupScrollListener();
        
        observeViewModel();
        
        binding.swipeRefreshHome.setColorSchemeResources(R.color.pks_blue, R.color.polygo_purple, R.color.polygo_teal, R.color.polygo_orange);
        binding.swipeRefreshHome.setOnRefreshListener(() -> {
            HapticManager.mediumTap(binding.swipeRefreshHome);
            viewModel.loadProducts();
        });
        
        viewModel.loadProducts();
        viewModel.loadCategories();
        viewModel.loadPicks();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always try to load fresh data when returning to home, 
        // ensuring newly added products or sold items are synced.
        if (viewModel != null) {
            viewModel.loadProducts();
        }
        startAutoRotate();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRotate();
    }

    private void observeViewModel() {
        viewModel.productsResource.observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || binding == null) return;

            switch (resource.status) {
                case LOADING:
                    if (!binding.swipeRefreshHome.isRefreshing()) {
                        binding.shimmerMarket.shimmerView.setVisibility(View.VISIBLE);
                        binding.shimmerMarket.shimmerView.startShimmer();
                        binding.recyclerViewProducts.setVisibility(View.GONE);
                        binding.tvEmptyProducts.setVisibility(View.GONE);
                    }
                    break;

                case SUCCESS:
                    binding.swipeRefreshHome.setRefreshing(false);
                    binding.shimmerMarket.shimmerView.stopShimmer();
                    binding.shimmerMarket.shimmerView.setVisibility(View.GONE);

                    List<ListingEntity> list = resource.data;
                    if (productAdapter != null) productAdapter.updateData(list);
                    boolean isEmpty = list == null || list.isEmpty();
                    binding.recyclerViewProducts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    binding.tvEmptyProducts.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    break;

                case ERROR:
                    binding.swipeRefreshHome.setRefreshing(false);
                    binding.shimmerMarket.shimmerView.stopShimmer();
                    binding.shimmerMarket.shimmerView.setVisibility(View.GONE);
                    // Show previously loaded content instead of a blank grid.
                    if (productAdapter != null && productAdapter.getItemCount() > 0) {
                        binding.recyclerViewProducts.setVisibility(View.VISIBLE);
                        binding.tvEmptyProducts.setVisibility(View.GONE);
                    }
                    // The global NetworkErrorHandler already surfaces the offline
                    // screen; here we only note the failure for cached-data users.
                    Snackbar.make(binding.getRoot(),
                            "Error: " + resource.message, Snackbar.LENGTH_LONG).show();
                    break;
            }
        });

        viewModel.picksResource.observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || binding == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                RecyclerView.Adapter<?> adapter = binding.rvPicks.getAdapter();
                if (adapter instanceof PicksAdapter) {
                    ((PicksAdapter) adapter).update(resource.data);
                    binding.rvPicks.setVisibility(resource.data.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });

        viewModel.categoriesResource.observe(getViewLifecycleOwner(), resource -> {
            if (resource == null || binding == null) return;
            if (resource.status == Resource.Status.SUCCESS && resource.data != null) {
                RecyclerView.Adapter<?> adapter = binding.rvCategories.getAdapter();
                if (adapter instanceof CategoryRowAdapter) {
                    ((CategoryRowAdapter) adapter).update(resource.data);
                    binding.rvCategories.setVisibility(resource.data.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });
    }

    private void setupHeader() {
        Context context = getContext();
        if (context == null) return;
        boolean loggedIn = AppDataStore.isLoggedIn(context);
        String name = AppDataStore.userName(context);
        String firstName = !name.isEmpty() ? name.split(" ")[0] : "Member";
        
        String hourGreeting;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) hourGreeting = getString(R.string.greeting_morning);
        else if (hour < 18) hourGreeting = getString(R.string.greeting_afternoon);
        else hourGreeting = getString(R.string.greeting_evening);

        if (!loggedIn || name == null || name.trim().isEmpty() || "PolyGo member".equals(name)) {
            binding.tvGreeting.setText(hourGreeting + "!");
        } else {
            binding.tvGreeting.setText(hourGreeting + ",\n" + firstName);
        }

        String photo = AppDataStore.userProfilePic(context);
        if (!photo.isEmpty()) {
            Glide.with(this)
                    .load(photo)
                    .circleCrop()
                    .override(160, 160)
                    .into(binding.ivProfilePic);
        }

        binding.ivProfilePic.setOnClickListener(v -> {
            HapticManager.lightTap(v);
            Context ctx = getContext();
            if (ctx == null) return;
            if (AppDataStore.isLoggedIn(ctx)) {
                Intent intent = new Intent(ctx, AccountActivity.class);
                ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(), binding.ivProfilePic, "profile_pic_hero"
                );
                startActivity(intent, options.toBundle());
            } else {
                startActivity(new Intent(ctx, LoginActivity.class));
            }
        });
    }

    private void setupEventCarousel() {
        List<Event> events = new ArrayList<>();
        events.add(new Event("Digital Career Fair 2026", "Career", "Fri 4 PM", "Main Hall", 124, true,
                R.drawable.ic_category_tech, "#0D47A1", "#1E88E5"));
        events.add(new Event("Campus Night Market", "Food", "Fri 7 PM", "Food Court", 89, false,
                R.drawable.ic_category_food, "#E65100", "#FB8C00"));
        events.add(new Event("Book Exchange Program", "Education", "Mon 10 AM", "Library", 42, false,
                R.drawable.ic_category_books, "#4A148C", "#7C4DFF"));

        EventAdapter adapter = new EventAdapter(events);
        binding.eventCarousel.setAdapter(adapter);

        binding.eventCarousel.setPageTransformer(new ScalePageTransformer());

        binding.eventCarousel.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                stopAutoRotate();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                startAutoRotate();
            }
            return false;
        });

        new TabLayoutMediator(binding.carouselIndicator, binding.eventCarousel, (tab, position) -> {
        }).attach();

        binding.eventCarousel.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                HapticManager.lightTap(binding.eventCarousel);
            }
        });

        startAutoRotate();
    }

    private void setupPicksRail() {
        binding.rvPicks.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        PicksAdapter adapter = new PicksAdapter();
        binding.rvPicks.setAdapter(adapter);
    }

    private void setupCategoryRow() {
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        CategoryRowAdapter adapter = new CategoryRowAdapter();
        binding.rvCategories.setAdapter(adapter);
    }

    private void setupProducts() {
        productAdapter = new ProductCardAdapter(new ArrayList<>(), (adapter, product, sharedView) -> {
            Context context = getContext();
            if (context == null) return;
            
            if (lastProductSharedElement != null) ViewCompat.setTransitionName(lastProductSharedElement, null);
            ViewCompat.setTransitionName(sharedView, "product_image_hero");
            lastProductSharedElement = sharedView;

            Intent intent = new Intent(context, ProductDetailActivity.class);
            intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, product.id);

            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), sharedView, "product_image_hero"
            );
            detailLauncher.launch(intent, options);
        });
        Context context = getContext();
        if (context != null) {
            binding.recyclerViewProducts.setLayoutManager(new GridLayoutManager(context, 2));
        }
        binding.recyclerViewProducts.setAdapter(productAdapter);
    }

    private void setupSearchActions() {
        View.OnClickListener openSearch = v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.lightTap(v);
            Intent intent = new Intent(context, SearchActivity.class);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    requireActivity(), binding.searchBarCard, "search_bar_hero"
            );
            startActivity(intent, options.toBundle());
        };
        binding.searchBarCard.setOnClickListener(openSearch);
        binding.tvSearchVisual.setOnClickListener(v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.swell(context);
            startActivity(new Intent(context, AiDiscoveryActivity.class));
        });
        binding.btnTextbookHub.setOnClickListener(v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.lightTap(v);
            startActivity(new Intent(context, TextbookHubActivity.class));
        });
        binding.tvSeeAllProducts.setOnClickListener(v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.lightTap(v);
            HapticManager.lightTap(v);
            startActivity(new Intent(context, SearchActivity.class));
            requireActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }

    private void setupScrollListener() {
        binding.appBar.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if (binding == null) return;
            
            float scrollRange = appBarLayout.getTotalScrollRange();
            if (scrollRange == 0) return;
            
            float fraction = Math.abs((float) verticalOffset) / scrollRange;
            
            // 1. Smooth Scroll-Synced Fade for Header Text
            float alpha = 1.0f - (fraction * 1.5f);
            binding.tvGreeting.setAlpha(Math.max(0, alpha));
            binding.tvGreetingSub.setAlpha(Math.max(0, alpha - 0.2f));

            // 2. "Clear" Sticky State: Animate search bar container margins/padding
            // Expanded: 20dp padding, Collapsed: 8dp
            int expandedPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
            int collapsedPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            int currentPadding = (int) (expandedPx - (expandedPx - collapsedPx) * fraction);
            binding.llSearchContainer.setPadding(currentPadding, 0, currentPadding, 0);

            // 3. UI Cleanup: Adjust Search Card corner radius and elevation dynamically
            float expandedRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());
            float collapsedRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
            binding.searchBarCard.setRadius(expandedRadius - (expandedRadius - collapsedRadius) * fraction);
            
            float expandedElev = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            binding.searchBarCard.setCardElevation(expandedElev * (1 - fraction));
            
            // 4. Stable Transitions: Ensure profile pic scales down slightly without jumping
            float scale = 1.0f - (fraction * 0.12f);
            binding.ivProfilePic.setScaleX(scale);
            binding.ivProfilePic.setScaleY(scale);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAutoRotate();
        binding = null;
    }

    public static class Event {
        String title, tag, time, location;
        int attendeesCount;
        boolean trending;
        int iconRes;
        String gradientStart, gradientEnd;

        Event(String title, String tag, String time, String location, int attendeesCount,
              boolean trending, int iconRes, String gradientStart, String gradientEnd) {
            this.title = title;
            this.tag = tag;
            this.time = time;
            this.location = location;
            this.attendeesCount = attendeesCount;
            this.trending = trending;
            this.iconRes = iconRes;
            this.gradientStart = gradientStart;
            this.gradientEnd = gradientEnd;
        }
    }

    private static class ScalePageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.92f;
        private static final float MIN_ALPHA = 0.65f;

        @Override
        public void transformPage(@NonNull View page, float position) {
            if (position < -1f || position > 1f) {
                page.setScaleX(MIN_SCALE);
                page.setScaleY(MIN_SCALE);
                page.setAlpha(MIN_ALPHA);
                return;
            }
            float abs = Math.abs(position);
            float scale = Math.max(MIN_SCALE, 1f - abs * 0.08f);
            page.setScaleX(scale);
            page.setScaleY(scale);
            page.setAlpha(MIN_ALPHA + ((scale - MIN_SCALE) / (1f - MIN_SCALE)) * (1f - MIN_ALPHA));
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

            holder.tvTitle.setText(event.title);
            holder.tvTag.setText(event.tag);
            holder.tvTime.setText(event.time);
            holder.tvLocation.setText(event.location);
            holder.tvAttendees.setText(holder.itemView.getContext()
                    .getString(R.string.event_going_format, event.attendeesCount));
            holder.tvTrending.setVisibility(event.trending ? View.VISIBLE : View.GONE);
            holder.ivIcon.setImageResource(event.iconRes);

            float radius = holder.bg.getContext().getResources().getDisplayMetrics().density * 24f;
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.parseColor(event.gradientStart), Color.parseColor(event.gradientEnd)});
            gradient.setCornerRadius(radius);
            holder.bg.setBackground(gradient);

            holder.itemView.setOnClickListener(v -> {
                HapticManager.swell(v.getContext());
                v.getContext().startActivity(new Intent(v.getContext(), CampusPulseActivity.class));
            });
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final View bg;
            final ImageView ivIcon;
            final TextView tvTag;
            final TextView tvTrending;
            final TextView tvTitle;
            final TextView tvTime;
            final TextView tvLocation;
            final TextView tvAttendees;

            ViewHolder(View itemView) {
                super(itemView);
                bg = itemView.findViewById(R.id.layoutEventBg);
                ivIcon = itemView.findViewById(R.id.ivEventIcon);
                tvTag = itemView.findViewById(R.id.tvEventTag);
                tvTrending = itemView.findViewById(R.id.tvTrending);
                tvTitle = itemView.findViewById(R.id.tvEventTitle);
                tvTime = itemView.findViewById(R.id.tvEventTime);
                tvLocation = itemView.findViewById(R.id.tvEventLocation);
                tvAttendees = itemView.findViewById(R.id.tvEventAttendees);
            }
        }
    }

    private final class PicksAdapter extends RecyclerView.Adapter<PicksAdapter.PickVH> {
        private final List<ListingEntity> items = new ArrayList<>();

        void update(List<ListingEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PickVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_pick_card, parent, false);
            return new PickVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PickVH holder, int position) {
            ListingEntity p = items.get(position);
            holder.title.setText(p.title);
            holder.price.setText("RM " + p.price);
            ImageView img = holder.itemView.findViewById(R.id.ivPickImage);
            Glide.with(holder.itemView.getContext())
                    .load(p.imageUrl == null || p.imageUrl.isEmpty() ? R.drawable.bg_product_home : p.imageUrl)
                    .placeholder(R.drawable.bg_product_home)
                    .error(R.drawable.bg_product_home)
                    .override(400, 400)
                    .centerCrop()
                    .into(img);
            holder.itemView.setOnClickListener(v -> openProduct(p.id));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class PickVH extends RecyclerView.ViewHolder {
            final TextView title, price;

            PickVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tvPickTitle);
                price = itemView.findViewById(R.id.tvPickPrice);
            }
        }
    }

    private final class CategoryRowAdapter extends RecyclerView.Adapter<CategoryRowAdapter.CategoryVH> {
        private final List<PolyGoApi.Category> items = new ArrayList<>();

        void update(List<PolyGoApi.Category> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public CategoryVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_category_icon, parent, false);
            return new CategoryVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CategoryVH holder, int position) {
            PolyGoApi.Category c = items.get(position);
            holder.name.setText(c.name);
            ImageView icon = holder.itemView.findViewById(R.id.ivCategoryIcon);
            icon.setImageResource(resolveCategoryIcon(c.icon_res, c.name));
            icon.setImageTintList(ColorStateList.valueOf(requireContext().getColor(UiUtils.categoryColor(c.name))));
            icon.setBackgroundTintList(ColorStateList.valueOf(requireContext().getColor(UiUtils.categoryTint(c.name))));
            holder.itemView.setOnClickListener(v -> {
                Context context = getContext();
                if (context == null) return;
                HapticManager.lightTap(v);
                startActivity(new Intent(context, SearchActivity.class)
                        .putExtra(SearchActivity.EXTRA_CATEGORY, c.name));
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class CategoryVH extends RecyclerView.ViewHolder {
            final TextView name;

            CategoryVH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvCategoryName);
            }
        }
    }

    private int resolveCategoryIcon(String iconRes, String name) {
        if (iconRes != null && !iconRes.isEmpty()) {
            int id = getResources().getIdentifier(iconRes, "drawable", requireContext().getPackageName());
            if (id != 0) return id;
        }
        return UiUtils.categoryIcon(name);
    }

    private void openProduct(String listingId) {
        Context context = getContext();
        if (context == null) return;
        Intent intent = new Intent(context, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, listingId);
        detailLauncher.launch(intent);
    }

}
