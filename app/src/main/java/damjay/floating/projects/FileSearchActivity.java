package damjay.floating.projects;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.customadapters.FileSearchAdapter;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.files.FileItem;

public class FileSearchActivity extends AppCompatActivity {
    public static FileBrowserActivity.FileCallback callback;
    private FileSearchAdapter searchAdapter;
    private ListView searchListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_search);
        setListeners();
    }

    private void setListeners() {
        EditText searchField = (EditText) findViewById(R.id.search_field);
        this.searchListView = (ListView) findViewById(R.id.search_list_view);
        this.searchAdapter = new FileSearchAdapter(this, callback);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
                FileSearchActivity.this.searchAdapter.reloadSearchResults(searchField.getText().toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
        this.searchListView.setAdapter((ListAdapter) this.searchAdapter);
        this.searchListView.setOnItemClickListener((parent, view, position, id) -> {
            FileItem item =
                    ((FileSearchAdapter.DocumentFile) this.searchListView.getItemAtPosition(position)).getFileItem();
            FileBrowserActivity.FileCallback fileCallback = callback;
            if (fileCallback != null) {
                fileCallback.fileCallback(item.getFullPath());
                callback = null;
            }
            onBackPressed();
        });
    }
}
