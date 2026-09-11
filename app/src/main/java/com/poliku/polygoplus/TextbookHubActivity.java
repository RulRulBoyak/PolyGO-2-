package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.poliku.polygoplus.network.BookLookup;
import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;

public class TextbookHubActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> scanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_textbook_hub);

        scanLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String isbn = result.getData().getStringExtra("scanned_isbn");
                if (isbn == null || isbn.trim().isEmpty()) {
                    return;
                }
                HapticManager.success(this);
                Toast.makeText(this, getString(R.string.textbook_looking_up, isbn), Toast.LENGTH_SHORT).show();
                lookupIsbn(isbn.trim());
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btnOpenScanner).setOnClickListener(v -> {
            HapticManager.swell(this);
            scanLauncher.launch(new Intent(this, IsbnScannerActivity.class));
        });

        setupDepartments();
        
        findViewById(R.id.btnWantedList).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            Toast.makeText(this, "Alerts are in development.", Toast.LENGTH_SHORT).show();
        });
    }

    private void lookupIsbn(String isbn) {
        BookLookup.search(isbn, new BookLookup.Callback() {
            @Override
            public void onResult(BookLookup.Book book) {
                openPrefillListing(isbn, book);
            }

            @Override
            public void onError(String message) {
                showNoBookDetailsDialog(isbn);
            }
        });
    }

    private void openPrefillListing(String isbn, BookLookup.Book book) {
        String meta = joinNonEmpty(" \u00b7 ", book.author, book.year, emptyToNull(book.pageCount) == null ? "" : book.pageCount + " pages");
        StringBuilder description = new StringBuilder(meta);
        if (book.description != null && !book.description.isEmpty()) {
            if (description.length() > 0) description.append("\n\n");
            description.append(book.description);
        }

        Intent prefill = new Intent(this, EditProductActivity.class);
        prefill.putExtra(EditProductActivity.EXTRA_PREFILL_TITLE,
                book.title != null && !book.title.isEmpty() ? book.title : "ISBN " + isbn);
        prefill.putExtra(EditProductActivity.EXTRA_PREFILL_PRICE, book.suggestedPrice);
        prefill.putExtra(EditProductActivity.EXTRA_PREFILL_DESCRIPTION, description.toString());
        prefill.putExtra(EditProductActivity.EXTRA_PREFILL_CATEGORY, "Books");
        startActivity(prefill);
        Toast.makeText(this, R.string.textbook_details_found, Toast.LENGTH_LONG).show();
    }

    private void showNoBookDetailsDialog(String isbn) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.textbook_not_found_title)
                .setMessage(getString(R.string.textbook_not_found, isbn))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.textbook_search_listings, (dialog, which) -> {
                    Intent search = new Intent(this, SearchActivity.class);
                    search.putExtra(SearchActivity.EXTRA_CATEGORY, "Books");
                    startActivity(search);
                })
                .show();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String joinNonEmpty(String sep, String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (joined.length() > 0) joined.append(sep);
            joined.append(part);
        }
        return joined.toString();
    }

    private void setupDepartments() {
        RecyclerView rv = findViewById(R.id.rvDepartments);
        List<Department> list = new ArrayList<>();
        list.add(new Department("DIT", "Information Tech", R.drawable.ic_category_tech));
        list.add(new Department("DKM", "Mechanical Eng", R.drawable.ic_category_repair));
        list.add(new Department("DAT", "Accounting", R.drawable.ic_category_books));
        list.add(new Department("DKE", "Civil Eng", R.drawable.ic_nav_explore));
        list.add(new Department("DEP", "Electrical Eng", R.drawable.ic_category_tech));
        list.add(new Department("DHM", "Hospitality", R.drawable.ic_category_food));

        rv.setAdapter(new DepartmentAdapter(list));
    }

    static class Department {
        String code, name;
        int icon;
        Department(String c, String n, int i) {
            code = c;
            name = n;
            icon = i;
        }
    }

    private class DepartmentAdapter extends RecyclerView.Adapter<DepartmentAdapter.Holder> {
        private final List<Department> items;

        DepartmentAdapter(List<Department> i) {
            items = i;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_tile, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Department d = items.get(position);
            h.title.setText(d.code);
            h.subtitle.setText(d.name);
            h.icon.setImageResource(d.icon);
            h.itemView.setOnClickListener(v -> {
                HapticManager.lightTap(v);
                Intent i = new Intent(TextbookHubActivity.this, SearchActivity.class);
                i.putExtra(SearchActivity.EXTRA_CATEGORY, "Books");
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon;
            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.tvCategory);
                subtitle = v.findViewById(R.id.tvCategorySub);
                icon = v.findViewById(R.id.imgCategory);
            }
        }
    }
}
