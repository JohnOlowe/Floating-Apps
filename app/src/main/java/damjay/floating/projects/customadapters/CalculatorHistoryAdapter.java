package damjay.floating.projects.customadapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;

import damjay.floating.projects.R;
import damjay.floating.projects.calculate.CalculatorService;

import java.util.ArrayList;

/**
 * Adapter for displaying calculator expression history in floating calculator layout.
 * Supports click (replace), long-press (insert/replace/delete/clear) actions.
 */
public class CalculatorHistoryAdapter extends BaseAdapter {

    private final CalculatorService calcService;
    private final HistoryListener historyListener;
    private final ArrayList<CalculatorService.CalcItem> list;

    public interface HistoryListener {
        void deleteHistory(int index);
        void insertContent(int index);
        void replaceContent(int index);
    }

    public CalculatorHistoryAdapter(CalculatorService calcService, HistoryListener historyListener,
                                     ArrayList<CalculatorService.CalcItem> list) {
        this.calcService = calcService;
        this.historyListener = historyListener;
        this.list = list;
    }

    @Override
    public int getCount() {
        return list == null ? 0 : list.size();
    }

    @Override
    public Object getItem(int position) {
        return (list == null || list.size() <= position) ? null : list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(calcService).inflate(
                    R.layout.calculator_history, parent, false);
        }

        // Long-press context menu: replace, insert, delete, clear all
        convertView.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(calcService, v);
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case R.id.replace_content:
                        historyListener.replaceContent(position);
                        return true;
                    case R.id.insert_content:
                        historyListener.insertContent(position);
                        return true;
                    case R.id.delete_history:
                        historyListener.deleteHistory(position);
                        return true;
                    case R.id.clear_history:
                        int size = list.size();
                        for (int i = 0; i < size; i++) {
                            historyListener.deleteHistory(0);
                        }
                        return true;
                }
                return false;
            });
            menu.inflate(R.menu.calc_history_menu);
            menu.show();
            return true;
        });

        // Click to replace editor content
        convertView.setOnClickListener(v -> historyListener.replaceContent(position));

        TextView expressionView = convertView.findViewById(R.id.calc_history_expression);
        expressionView.setText(list.get(position).getExpression());

        TextView solutionView = convertView.findViewById(R.id.calc_history_solution);
        solutionView.setText(list.get(position).getAnswer());

        return convertView;
    }
}
