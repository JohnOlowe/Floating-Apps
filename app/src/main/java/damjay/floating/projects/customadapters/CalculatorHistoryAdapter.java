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

public class CalculatorHistoryAdapter extends BaseAdapter {
    private final CalculatorService calcService;
    private final HistoryListener historyListener;
    private final ArrayList<CalculatorService.CalcItem> list;

    public interface HistoryListener {
        void deleteHistory(int position);
        void insertContent(int position);
        void replaceContent(int position);
    }

    public CalculatorHistoryAdapter(CalculatorService calcService, HistoryListener historyListener,
            ArrayList<CalculatorService.CalcItem> list) {
        this.list = list;
        this.calcService = calcService;
        this.historyListener = historyListener;
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return list != null && list.size() > position ? list.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup vg) {
        if (view == null) {
            view = LayoutInflater.from(calcService).inflate(R.layout.calculator_history, vg, false);
        }

        view.setOnLongClickListener(v -> {
            PopupMenu menu = new PopupMenu(calcService, v);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.replace_content) {
                    historyListener.replaceContent(position);
                    return true;
                }
                if (item.getItemId() == R.id.insert_content) {
                    historyListener.insertContent(position);
                    return true;
                }
                if (item.getItemId() == R.id.delete_history) {
                    historyListener.deleteHistory(position);
                    return true;
                }
                if (item.getItemId() == R.id.clear_history) {
                    int originalListSize = list.size();
                    for (int i = 0; i < originalListSize; i++) {
                        historyListener.deleteHistory(0);
                    }
                    return true;
                }
                return true;
            });
            menu.inflate(R.menu.calc_history_menu);
            menu.show();
            return true;
        });

        view.setOnClickListener(v -> calcService.replaceContent(position));

        TextView expressionView = view.findViewById(R.id.calc_history_expression);
        expressionView.setText(list.get(position).getExpression());
        TextView solutionView = view.findViewById(R.id.calc_history_solution);
        solutionView.setText(list.get(position).getAnswer());
        return view;
    }
}
