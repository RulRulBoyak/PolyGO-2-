package com.poliku.polygoplus.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
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
import com.google.gson.Gson;
import com.poliku.polygoplus.api.PolyGoApi;
import com.poliku.polygoplus.api.model.BaseResponse;
import com.poliku.polygoplus.AiDiscoveryActivity;
import com.poliku.polygoplus.CampusPulseActivity;
import com.poliku.polygoplus.CaCalculatorActivity;
import com.poliku.polygoplus.ChatActivity;
import com.poliku.polygoplus.EditProductActivity;
import com.poliku.polygoplus.EventDetailActivity;
import com.poliku.polygoplus.LoginActivity;
import com.poliku.polygoplus.R;
import com.poliku.polygoplus.SearchActivity;
import com.poliku.polygoplus.AccountActivity;
import com.poliku.polygoplus.ProductDetailActivity;
import com.poliku.polygoplus.TextbookHubActivity;
import com.poliku.polygoplus.EventsActivity;
import com.poliku.polygoplus.TimetableActivity;
import com.poliku.polygoplus.data.AppDataStore;
import com.poliku.polygoplus.data.ProductCardAdapter;
import com.poliku.polygoplus.data.PolyGoRepository;
import com.poliku.polygoplus.data.local.entity.ListingEntity;
import com.poliku.polygoplus.util.Resource;
import com.poliku.polygoplus.util.EventCardBinder;
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
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment {

    @Inject PolyGoRepository polyGoRepository;
    private FragmentHomeBinding binding;
    private ProductCardAdapter productAdapter;
    private HomeViewModel viewModel;
    private ActivityResultLauncher<Intent> detailLauncher;
    @Nullable private View lastProductSharedElement;
    private AppBarLayout.OnOffsetChangedListener offsetChangedListener;
    private MyListingCardAdapter myListingAdapter;
    private BuyingAdapter buyingAdapter;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private final List<PolyGoApi.CampusEvent> campusEvents = new ArrayList<>();
    private EventAdapter eventAdapter;
    private final List<String> rotatingMessages = new ArrayList<>();
    private int messageIndex;
    private long messageIntervalMs = 8000L;
    private final Runnable messageRotation = new Runnable() {
        @Override
        public void run() {
            if (binding == null || rotatingMessages.isEmpty()) return;
            messageIndex = (messageIndex + 1) % rotatingMessages.size();
            animateHeaderCopy();
            autoRotateHandler.postDelayed(this, messageIntervalMs);
        }
    };

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
        loadHeaderMessages();
        setupEventCarousel();
        setupProducts();
        setupCategoryRow();
        setupCampusTools();
        setupPicksRail();
        setupSearchActions();
        setupScrollListener();
        setupActivityHub();
        
        observeViewModel();
        
        binding.swipeRefreshHome.setColorSchemeResources(R.color.pks_blue, R.color.polygo_purple, R.color.polygo_teal, R.color.polygo_orange);
        binding.swipeRefreshHome.setOnRefreshListener(() -> {
            HapticManager.mediumTap(binding.swipeRefreshHome);
            viewModel.loadProducts();
            loadEvents();
            loadActivityHubData();
        });
        
        viewModel.loadProducts();
        viewModel.loadCategories();
        viewModel.loadPicks();
        loadActivityHubData();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Always try to load fresh data when returning to home, 
        // ensuring newly added products or sold items are synced.
        if (viewModel != null) {
            viewModel.loadProducts();
        }
        loadEvents();
        loadActivityHubData();
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
        binding.tvGreeting.setText(greetingText(context));

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
        eventAdapter = new EventAdapter(campusEvents);
        binding.eventCarousel.setAdapter(eventAdapter);

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

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (binding != null) {
                    HapticManager.lightTap(binding.eventCarousel);
                }
            }
        };
        binding.eventCarousel.registerOnPageChangeCallback(pageChangeCallback);
    }

    private String greetingText(Context context) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greeting = hour < 5 || hour >= 21 ? R.string.greeting_night
                : hour < 12 ? R.string.greeting_morning
                : hour < 18 ? R.string.greeting_afternoon : R.string.greeting_evening;
        String name = AppDataStore.userName(context);
        if (!AppDataStore.isLoggedIn(context) || name == null || name.trim().isEmpty()
                || "PolyGo member".equals(name)) {
            return getString(greeting) + "!";
        }
        return getString(greeting) + ", " + name.trim().split(" ")[0];
    }

    private void loadHeaderMessages() {
        rotatingMessages.clear();
        for (String message : getResources().getStringArray(R.array.home_rotating_messages)) {
            rotatingMessages.add(message);
        }
        startMessageRotation();
        polyGoRepository.getStatus(new retrofit2.Callback<BaseResponse>() {
            @Override
            public void onResponse(retrofit2.Call<BaseResponse> call,
                                   retrofit2.Response<BaseResponse> response) {
                if (binding == null || response.body() == null) return;
                List<String> configured = response.body().getHomeMessages();
                if (configured != null && !configured.isEmpty()) {
                    rotatingMessages.clear();
                    rotatingMessages.addAll(configured);
                    messageIndex = 0;
                }
                int seconds = response.body().getHomeMessageIntervalSeconds();
                if (seconds >= 5 && seconds <= 60) messageIntervalMs = seconds * 1000L;
                startMessageRotation();
            }

            @Override
            public void onFailure(retrofit2.Call<BaseResponse> call, Throwable error) {
                // Localized defaults remain active offline.
            }
        });
    }

    private void startMessageRotation() {
        autoRotateHandler.removeCallbacks(messageRotation);
        if (binding == null || rotatingMessages.isEmpty()) return;
        binding.tvGreetingSub.setText(rotatingMessages.get(messageIndex));
        autoRotateHandler.postDelayed(messageRotation, messageIntervalMs);
    }

    private void animateHeaderCopy() {
        if (binding == null) return;
        Context context = getContext();
        if (context == null) return;
        binding.tvGreeting.animate().cancel();
        binding.tvGreetingSub.animate().cancel();
        binding.tvGreeting.animate().alpha(0f).translationY(-dp(6)).setDuration(180)
                .withEndAction(() -> {
                    if (binding == null) return;
                    binding.tvGreeting.setText(greetingText(context));
                    binding.tvGreeting.setTranslationY(dp(6));
                    binding.tvGreeting.animate().alpha(1f).translationY(0f)
                            .setDuration(240).start();
                }).start();
        binding.tvGreetingSub.animate().alpha(0f).translationY(-dp(6)).setDuration(180)
                .withEndAction(() -> {
                    if (binding == null) return;
                    binding.tvGreetingSub.setText(rotatingMessages.get(messageIndex));
                    binding.tvGreetingSub.setTranslationY(dp(6));
                    binding.tvGreetingSub.animate().alpha(0.9f).translationY(0f)
                            .setDuration(240).start();
                }).start();
    }

    private void loadEvents() {
        if (binding == null) return;
        polyGoRepository.campus("events", new retrofit2.Callback<PolyGoApi.CampusResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.CampusResponse> call,
                                   retrofit2.Response<PolyGoApi.CampusResponse> response) {
                if (binding == null) return;
                campusEvents.clear();
                if (!response.isSuccessful() || response.body() == null
                        || !response.body().isSuccess() || response.body().events == null) {
                    showEventCarousel(false);
                    return;
                }
                for (PolyGoApi.CampusEvent e : response.body().events) {
                    if (e != null) campusEvents.add(e);
                }
                eventAdapter.notifyDataSetChanged();
                showEventCarousel(!campusEvents.isEmpty());
                if (campusEvents.size() > 1) startAutoRotate();
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.CampusResponse> call,
                                  Throwable error) {
                if (binding != null && campusEvents.isEmpty()) showEventCarousel(false);
            }
        });
    }

    private void showEventCarousel(boolean show) {
        binding.eventCarouselSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) stopAutoRotate();
    }

    private void setupPicksRail() {
        binding.rvPicks.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        PicksAdapter adapter = new PicksAdapter();
        binding.rvPicks.setAdapter(adapter);
        animateListEntrance(binding.rvPicks);
    }

    private void setupCategoryRow() {
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        CategoryRowAdapter adapter = new CategoryRowAdapter();
        binding.rvCategories.setAdapter(adapter);
        animateListEntrance(binding.rvCategories);
    }

    private void setupCampusTools() {
        binding.rvCampusTools.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.rvCampusTools.setAdapter(new CampusToolAdapter());
    }

    private void animateListEntrance(RecyclerView list) {
        if (list == null) return;
        LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_cascade);
        list.setLayoutAnimation(controller);
        list.scheduleLayoutAnimation();
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

            if (getActivity() == null) return;
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(), sharedView, "product_image_hero"
                );
            detailLauncher.launch(intent, options);
        });
        Context context = getContext();
        if (context != null) {
            binding.recyclerViewProducts.setLayoutManager(new GridLayoutManager(context, 2));
        }
        binding.recyclerViewProducts.setAdapter(productAdapter);
    }

    private void setupActivityHub() {
        if (binding == null) return;
        Context context = getContext();
        if (context == null || !AppDataStore.isLoggedIn(context)) {
            binding.activityMarketplaceHub.setVisibility(View.GONE);
            return;
        }
        binding.activityMarketplaceHub.setVisibility(View.VISIBLE);

        binding.rvMyListings.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        myListingAdapter = new MyListingCardAdapter();
        binding.rvMyListings.setAdapter(myListingAdapter);

        binding.rvBuying.setLayoutManager(new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false));
        buyingAdapter = new BuyingAdapter();
        binding.rvBuying.setAdapter(buyingAdapter);
    }

    private void loadActivityHubData() {
        if (binding == null || myListingAdapter == null || buyingAdapter == null) return;
        Context context = getContext();
        if (context == null || !AppDataStore.isLoggedIn(context)) {
            binding.activityMarketplaceHub.setVisibility(View.GONE);
            return;
        }
        binding.activityMarketplaceHub.setVisibility(View.VISIBLE);
        loadMyListings();
        loadBuying();
    }

    private void loadMyListings() {
        Context context = getContext();
        if (context == null || myListingAdapter == null) return;
        String userId = AppDataStore.userId(context);
        polyGoRepository.getMyListings(new retrofit2.Callback<PolyGoApi.ListingsResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.ListingsResponse> call, retrofit2.Response<PolyGoApi.ListingsResponse> response) {
                List<ListingEntity> items = new ArrayList<>();
                PolyGoApi.ListingsResponse body = response.body();
                if (body != null && body.listings != null) {
                    for (PolyGoApi.Listing l : body.listings) {
                        AppDataStore.ProductRecord record = AppDataStore.ProductRecord.fromListing(l, userId);
                        if (record != null) {
                            ListingEntity e = record.toEntity();
                            e.archived = l.archivedAt != null && !l.archivedAt.isEmpty();
                            items.add(e);
                        }
                    }
                }
                myListingAdapter.update(items);
                if (binding == null) return;
                boolean empty = items.isEmpty();
                binding.tvEmptyMyListings.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.rvMyListings.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.ListingsResponse> call, Throwable t) {
                if (binding == null) return;
                binding.tvEmptyMyListings.setVisibility(View.VISIBLE);
                binding.rvMyListings.setVisibility(View.GONE);
            }
        });
    }

    private void loadBuying() {
        Context context = getContext();
        if (context == null || buyingAdapter == null) return;
        polyGoRepository.getThreads(AppDataStore.userId(context), new retrofit2.Callback<PolyGoApi.ThreadsResponse>() {
            @Override
            public void onResponse(retrofit2.Call<PolyGoApi.ThreadsResponse> call, retrofit2.Response<PolyGoApi.ThreadsResponse> response) {
                List<PolyGoApi.Thread> threads = new ArrayList<>();
                PolyGoApi.ThreadsResponse body = response.body();
                if (body != null && body.threads != null) {
                    threads.addAll(body.threads);
                }
                buyingAdapter.update(threads);
                if (binding == null) return;
                boolean empty = threads.isEmpty();
                binding.tvEmptyBuying.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.rvBuying.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(retrofit2.Call<PolyGoApi.ThreadsResponse> call, Throwable t) {
                if (binding == null) return;
                binding.tvEmptyBuying.setVisibility(View.VISIBLE);
                binding.rvBuying.setVisibility(View.GONE);
            }
        });
    }

    private void setupSearchActions() {
        View.OnClickListener openSearch = v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.lightTap(v);
            Intent intent = new Intent(context, SearchActivity.class);
            if (getActivity() == null || binding == null) return;
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    getActivity(), binding.searchBarCard, "search_bar_hero"
            );
            startActivity(intent, options.toBundle());
        };
        binding.searchBarCard.setOnClickListener(openSearch);
        binding.ivSearchVisual.setOnClickListener(v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.swell(context);
            startActivity(new Intent(context, AiDiscoveryActivity.class));
        });
        binding.tvSeeAllProducts.setOnClickListener(v -> {
            Context context = getContext();
            if (context == null) return;
            HapticManager.lightTap(v);
            HapticManager.lightTap(v);
            startActivity(new Intent(context, SearchActivity.class));
            if (getActivity() != null) {
                getActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            }
        });
    }

    private void setupScrollListener() {
        offsetChangedListener = (appBarLayout, verticalOffset) -> {
            if (binding == null) return;
            
            float scrollRange = appBarLayout.getTotalScrollRange();
            if (scrollRange == 0) return;
            
            float fraction = Math.abs((float) verticalOffset) / scrollRange;
            
            // 1. Smooth Scroll-Synced Fade for Header Text
            float alpha = 1.0f - (fraction * 1.5f);
            binding.tvGreeting.setAlpha(Math.max(0, alpha));
            binding.tvGreetingSub.setAlpha((float) Math.pow(Math.max(0, alpha), 1.6));

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
        };
        binding.appBar.addOnOffsetChangedListener(offsetChangedListener);
    }

    @Override
    public void onDestroyView() {
        if (binding != null && binding.appBar != null && offsetChangedListener != null) {
            binding.appBar.removeOnOffsetChangedListener(offsetChangedListener);
        }
        offsetChangedListener = null;
        if (pageChangeCallback != null && binding != null) {
            binding.eventCarousel.unregisterOnPageChangeCallback(pageChangeCallback);
        }
        pageChangeCallback = null;
        if (lastProductSharedElement != null) {
            ViewCompat.setTransitionName(lastProductSharedElement, null);
            lastProductSharedElement = null;
        }
        super.onDestroyView();
        stopAutoRotate();
        autoRotateHandler.removeCallbacks(messageRotation);
        binding = null;
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
        private final List<PolyGoApi.CampusEvent> events;

        EventAdapter(List<PolyGoApi.CampusEvent> events) {
            this.events = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_campus_event, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PolyGoApi.CampusEvent event = events.get(position);
            EventCardBinder.Bindings views = holder.views;
            views.title.setText(event.title);
            views.venue.setText(event.venue);
            views.description.setText(event.description);
            EventCardBinder.apply(views, event,
                    holder.itemView.getResources().getDisplayMetrics().density);
            holder.itemView.setOnClickListener(v -> {
                HapticManager.swell(v.getContext());
                Intent intent = new Intent(v.getContext(), EventDetailActivity.class);
                intent.putExtra(EventDetailActivity.EXTRA_EVENT, new Gson().toJson(event));
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final EventCardBinder.Bindings views;

            ViewHolder(View itemView) {
                super(itemView);
                views = EventCardBinder.bind(itemView);
            }
        }
    }

    private final class MyListingCardAdapter extends RecyclerView.Adapter<MyListingCardAdapter.MultiVH> {
        private final List<ListingEntity> items = new ArrayList<>();

        void update(List<ListingEntity> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public MultiVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_mylisting_card, parent, false);
            return new MultiVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MultiVH holder, int position) {
            ListingEntity p = items.get(position);
            holder.price.setText("RM " + p.price);
            holder.title.setText(p.title);
            holder.status.setText(p.archived ? getString(R.string.item_removed)
                    : (p.available ? getString(R.string.item_live) : getString(R.string.item_sold)));
            holder.status.setVisibility(View.VISIBLE);
            ImageView img = holder.itemView.findViewById(R.id.ivMyImage);
            Glide.with(holder.itemView.getContext())
                    .load(p.imageUrl == null || p.imageUrl.isEmpty() ? R.drawable.bg_product_home : p.imageUrl)
                    .placeholder(R.drawable.bg_product_home)
                    .error(R.drawable.bg_product_home)
                    .centerCrop()
                    .into(img);
            holder.itemView.setOnClickListener(v -> {
                Context context = getContext();
                if (context == null) return;
                HapticManager.lightTap(v);
                Intent i = new Intent(context, EditProductActivity.class);
                i.putExtra(EditProductActivity.EXTRA_LISTING_ID, p.id);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class MultiVH extends RecyclerView.ViewHolder {
            final TextView price, title, status;

            MultiVH(@NonNull View itemView) {
                super(itemView);
                price = itemView.findViewById(R.id.tvMyPrice);
                title = itemView.findViewById(R.id.tvMyTitle);
                status = itemView.findViewById(R.id.tvMyStatus);
            }
        }
    }

    private final class BuyingAdapter extends RecyclerView.Adapter<BuyingAdapter.BuyVH> {
        private final List<PolyGoApi.Thread> items = new ArrayList<>();

        void update(List<PolyGoApi.Thread> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BuyVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_thread_mini, parent, false);
            return new BuyVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BuyVH holder, int position) {
            PolyGoApi.Thread t = items.get(position);
            String name = t.name == null || t.name.trim().isEmpty() ? getString(R.string.campus_seller) : t.name;
            holder.name.setText(name);
            String preview = t.last_message == null || t.last_message.isEmpty()
                    ? getString(R.string.thread_open_to_chat) : t.last_message;
            holder.preview.setText(getString(R.string.thread_you_prefix, preview));
            String initial = name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase(Locale.ROOT);
            holder.initial.setText(initial);
            holder.unreadDot.setVisibility(t.unread ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> {
                Context context = getContext();
                if (context == null) return;
                HapticManager.lightTap(v);
                Intent i = new Intent(context, ChatActivity.class);
                i.putExtra(ChatActivity.EXTRA_THREAD_ID, t.id);
                i.putExtra(ChatActivity.EXTRA_LISTING_ID, t.listingId);
                i.putExtra(ChatActivity.EXTRA_OTHER_NAME, name);
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class BuyVH extends RecyclerView.ViewHolder {
            final TextView initial, name, preview;
            final View unreadDot;

            BuyVH(@NonNull View itemView) {
                super(itemView);
                initial = itemView.findViewById(R.id.tvThreadInitial);
                name = itemView.findViewById(R.id.tvThreadName);
                preview = itemView.findViewById(R.id.tvThreadPreview);
                unreadDot = itemView.findViewById(R.id.vwUnreadDot);
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

    private final class CampusToolAdapter extends RecyclerView.Adapter<CampusToolAdapter.ToolHolder> {
        private final int[] titles = {R.string.campus_hub_pulse, R.string.campus_hub_ca,
            R.string.campus_hub_events, R.string.campus_hub_timetable};
        private final int[] descriptions = {R.string.campus_hub_pulse_desc,
            R.string.campus_hub_ca_desc, R.string.campus_hub_events_desc,
            R.string.campus_hub_timetable_desc};
        private final int[] icons = {R.drawable.ic_threads, R.drawable.ic_school,
            R.drawable.ic_calendar, R.drawable.ic_clock};
        private final Class<?>[] destinations = {CampusPulseActivity.class,
            CaCalculatorActivity.class, EventsActivity.class, TimetableActivity.class};

        @NonNull
        @Override
        public ToolHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ToolHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_campus_tool, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ToolHolder holder, int position) {
            holder.title.setText(titles[position]);
            holder.description.setText(descriptions[position]);
            holder.icon.setImageResource(icons[position]);
            int color = requireContext().getColor(position == 1
                    ? R.color.pks_green : R.color.pks_blue);
            holder.icon.setImageTintList(ColorStateList.valueOf(color));
            holder.itemView.setContentDescription(getString(titles[position]) + ". "
                    + getString(descriptions[position]));
            holder.itemView.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                startActivity(new Intent(v.getContext(), destinations[position]));
            });
        }

        @Override
        public int getItemCount() {
            return titles.length;
        }

        final class ToolHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView description;

            ToolHolder(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.ivToolIcon);
                title = itemView.findViewById(R.id.tvToolTitle);
                description = itemView.findViewById(R.id.tvToolDescription);
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
            View bg = holder.itemView.findViewById(R.id.ivCategoryBg);

            icon.setImageResource(resolveCategoryIcon(c.icon_res, c.name));

            int color = requireContext().getColor(UiUtils.categoryColor(c.name));
            icon.setImageTintList(ColorStateList.valueOf(color));
            bg.setBackgroundTintList(ColorStateList.valueOf(color));
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
        return iconRes == null || iconRes.isEmpty()
                ? UiUtils.categoryIcon(name) : UiUtils.categoryIconKey(iconRes);
    }

    private void openProduct(String listingId) {
        Context context = getContext();
        if (context == null) return;
        Intent intent = new Intent(context, ProductDetailActivity.class);
        intent.putExtra(ProductDetailActivity.EXTRA_LISTING_ID, listingId);
        detailLauncher.launch(intent);
    }

    private float dp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

}
