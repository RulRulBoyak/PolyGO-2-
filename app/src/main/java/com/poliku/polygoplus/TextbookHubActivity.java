package com.poliku.polygoplus;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poliku.polygoplus.ui.HapticManager;

import java.util.ArrayList;
import java.util.List;

public class TextbookHubActivity extends AppCompatActivity {

    private static final int SCAN_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_textbook_hub);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        findViewById(R.id.btnOpenScanner).setOnClickListener(v -> {
            HapticManager.swell(this);
            startActivityForResult(new Intent(this, IsbnScannerActivity.class), SCAN_REQUEST_CODE);
        });

        setupDepartments();
        
        findViewById(R.id.btnWantedList).setOnClickListener(v -> {
            HapticManager.lightTap(v);
            Toast.makeText(this, "Book Alerts feature coming soon!", Toast.LENGTH_SHORT).show();
        });
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String isbn = data.getStringExtra("scanned_isbn");
            HapticManager.success(this);
            
            // Redirect to Search with the ISBN
            Intent search = new Intent(this, SearchActivity.class);
            search.putExtra(SearchActivity.EXTRA_CATEGORY, "Books");
            // Set the search query to the ISBN
            // Note: In a real app, we'd add another EXTRA for query
            startActivity(search);
            Toast.makeText(this, "Searching for ISBN: " + isbn, Toast.LENGTH_LONG).show();
        }
    }

    static class Department {
        String code, name;
        int icon;
        Department(String c, String n, int i) { code = c; name = n; icon = i; }
    }

    private class DepartmentAdapter extends RecyclerView.Adapter<DepartmentAdapter.Holder> {
        private final List<Department> items;
        DepartmentAdapter(List<Department> i) { items = i; }

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

        @Override public int getItemCount() { return items.size(); }

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
