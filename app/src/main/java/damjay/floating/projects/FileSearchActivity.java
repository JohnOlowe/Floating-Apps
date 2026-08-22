package damjay.floating.projects;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
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
        searchField.addTextChangedListener(new AnonymousClass1(searchField));
        this.searchListView.setAdapter((ListAdapter) this.searchAdapter);
        this.searchListView.setOnItemClickListener((adapterView, view, i, j) -> this.m94x380e80fc(adapterView, view, i, j));
    }

    class AnonymousClass1 implements TextWatcher {
        final EditText val$searchField;

        AnonymousClass1(EditText editText) {
            this.val$searchField = editText;
        }

        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int before, int count) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
            FileSearchActivity.this.searchAdapter.reloadSearchResults(this.val$searchField.getText().toString());
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    }

    void m94x380e80fc(AdapterView parent, View view, int position, long id) {
        FileItem item = ((FileSearchAdapter.DocumentFile) this.searchListView.getItemAtPosition(position)).getFileItem();
        FileBrowserActivity.FileCallback fileCallback = callback;
        if (fileCallback != null) {
            fileCallback.fileCallback(item.getFullPath());
            callback = null;
        }
        onBackPressed();
    }
}
