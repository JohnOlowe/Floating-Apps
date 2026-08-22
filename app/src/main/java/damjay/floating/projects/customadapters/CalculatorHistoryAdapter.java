package damjay.floating.projects.customadapters;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;
import damjay.floating.projects.R;
import damjay.floating.projects.calculate.CalculatorService;
import java.util.ArrayList;

public class CalculatorHistoryAdapter extends BaseAdapter {
    private final CalculatorService calcService;
    private final HistoryListener historyListener;
    private final ArrayList<CalculatorService.CalcItem> list;

    public interface HistoryListener {
        void deleteHistory(int i);

        void insertContent(int i);

        void replaceContent(int i);
    }

    public CalculatorHistoryAdapter(CalculatorService calcService, HistoryListener historyListener, ArrayList<CalculatorService.CalcItem> list) {
        this.list = list;
        this.calcService = calcService;
        this.historyListener = historyListener;
    }

    @Override
    public int getCount() {
        ArrayList<CalculatorService.CalcItem> arrayList = this.list;
        if (arrayList != null) {
            return arrayList.size();
        }
        return 0;
    }

    @Override
    public Object getItem(int position) {
        ArrayList<CalculatorService.CalcItem> arrayList = this.list;
        if (arrayList == null || arrayList.size() <= position) {
            return null;
        }
        return this.list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup vg) {
        if (view == null) {
            view = LayoutInflater.from(this.calcService).inflate(R.layout.calculator_history, vg, false);
        }
        view.setOnLongClickListener((view) -> this.m199xfa69f187(position, view));
        view.setOnClickListener((view) -> this.m200x649979a6(position, view));
        TextView expressionView = (TextView) view.findViewById(R.id.calc_history_expression);
        expressionView.setText(this.list.get(position).getExpression());
        TextView solutionView = (TextView) view.findViewById(R.id.calc_history_solution);
        solutionView.setText(this.list.get(position).getAnswer());
        return view;
    }

    boolean m199xfa69f187(int position, View v) {
        PopupMenu menu = new PopupMenu(this.calcService, v);
        menu.setOnMenuItemClickListener((menuItem) -> this.m198x903a6968(position, menuItem));
        menu.inflate(R.menu.calc_history_menu);
        menu.show();
        return true;
    }

    boolean m198x903a6968(int position, MenuItem item) {
        if (item.getItemId() == R.id.replace_content) {
            this.historyListener.replaceContent(position);
            return true;
        }
        if (item.getItemId() == R.id.insert_content) {
            this.historyListener.insertContent(position);
            return true;
        }
        if (item.getItemId() == R.id.delete_history) {
            this.historyListener.deleteHistory(position);
            return true;
        }
        if (item.getItemId() == R.id.clear_history) {
            int originalListSize = this.list.size();
            for (int i = 0; i < originalListSize; i++) {
                this.historyListener.deleteHistory(0);
            }
            return true;
        }
        return true;
    }

    void m200x649979a6(int position, View v) {
        this.calcService.replaceContent(position);
    }
}
