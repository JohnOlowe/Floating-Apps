package damjay.floating.projects.calculate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import damjay.floating.projects.MainActivity;
import damjay.floating.projects.QuickLaunchControl;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.CalculatorHistoryAdapter;
import damjay.floating.projects.utils.ViewsUtils;
import java.util.ArrayList;

public class CalculatorService extends Service implements CalculatorHistoryAdapter.HistoryListener {
    private View collapsed;
    private EditText editor;
    private View expanded;
    private CalculatorHistoryAdapter historyAdapter;
    private ListView historyList;
    private View mainCalculator;
    private WindowManager.LayoutParams params;
    private View parentLayout;
    private String previousInvalidExpression;
    private WindowManager.LayoutParams quickLaunchParams;
    private View quickLaunchView;
    private WindowManager window;
    private int caretPosition = 1;
    private final ArrayList<CalcItem> calculatedItems = new ArrayList<>();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getViews();
        minimizeView(null);
        params = ViewsUtils.getFloatingLayoutParams();
        WindowManager windowManager = (WindowManager) getSystemService("window");
        window = windowManager;
        windowManager.addView(parentLayout, params);
        addTouchListeners();
    }

    private void getViews() {
        View viewInflate = LayoutInflater.from(this).inflate(R.layout.calculator_layout, (ViewGroup) null);
        parentLayout = viewInflate;
        editor = (EditText) viewInflate.findViewById(R.id.calcField);
        collapsed = parentLayout.findViewById(R.id.collapsedCalc);
        expanded = parentLayout.findViewById(R.id.expandedCalc);
        mainCalculator = parentLayout.findViewById(R.id.main_calculator);
        if (historyList == null) {
            historyList = (ListView) parentLayout.findViewById(R.id.calcHistory);
            CalculatorHistoryAdapter calculatorHistoryAdapter =
                    new CalculatorHistoryAdapter(this, this, calculatedItems);
            historyAdapter = calculatorHistoryAdapter;
            historyList.setAdapter((ListAdapter) calculatorHistoryAdapter);
        }
        collapsed.setOnClickListener((view) -> {
            expanded.setVisibility(View.VISIBLE);
            collapsed.setVisibility(View.GONE);
            View view2 = quickLaunchView;
            if (view2 != null) {
                view2.setVisibility(View.VISIBLE);
            }
        });
        parentLayout.findViewById(R.id.calcLaunchActivity)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        parentLayout.findViewById(R.id.show_quick_launch).setOnClickListener((v) -> {
            View view = quickLaunchView;
            if (view == null) {
                View quickLaunchView = QuickLaunchControl.getQuickLaunchView(this);
                this.quickLaunchView = quickLaunchView;
                WindowManager windowManager = window;
                WindowManager.LayoutParams quickLaunchParams = getQuickLaunchParams(null);
                this.quickLaunchParams = quickLaunchParams;
                windowManager.addView(quickLaunchView, quickLaunchParams);
                return;
            }
            window.removeView(view);
            quickLaunchView = null;
        });
        expanded.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> expanded.getLayoutParams().width = ViewsUtils.getViewWidth(275.0f));
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
            int measuredHeight = mainCalculator.getMeasuredHeight();
            if (measuredHeight < 1) {
                return;
            }
            historyList.getLayoutParams().height = measuredHeight;
        };
        mainCalculator.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        historyList.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        EditText editText = editor;
        int length = editText.getText().length();
        caretPosition = length;
        editText.setSelection(length);
    }

    private WindowManager.LayoutParams getQuickLaunchParams(WindowManager.LayoutParams quickLaunchParams) {
        int y = params.y;
        int topNavHeight = parentLayout.findViewById(R.id.topNav).getMeasuredHeight();
        int viewHeight = quickLaunchView.getMeasuredHeight();
        int viewHeight2 = viewHeight <= 0 ? topNavHeight : viewHeight;
        WindowManager.LayoutParams quickLaunchParams2 =
                quickLaunchParams == null ? ViewsUtils.getFloatingLayoutParams() : quickLaunchParams;
        if (y <= viewHeight2) {
            quickLaunchParams2.y = topNavHeight + y;
        } else {
            quickLaunchParams2.y = y - viewHeight2;
        }
        quickLaunchParams2.x = params.x;
        return quickLaunchParams2;
    }

    private void addTouchListeners() {
        View.OnTouchListener touchListener =
                ViewsUtils.getViewTouchListener(this, parentLayout, window, params);
        View.OnTouchListener mainTouchListener = (view, event) -> {
            boolean result = touchListener.onTouch(view, event);
            View view2 = quickLaunchView;
            if (view2 != null) {
                window.updateViewLayout(view2, getQuickLaunchParams(quickLaunchParams));
            }
            return result;
        };
        ViewsUtils.addTouchListener(expanded, mainTouchListener, true, true, ListView.class, null);
        ViewsUtils.addTouchListener(collapsed, touchListener, true, true, new Class[0]);
    }

    public void showHistory(View view) {
        if (parentLayout.findViewById(R.id.calcHistory).getVisibility() == View.VISIBLE) {
            hideHistory();
            return;
        }
        if (historyList == null) {
            historyList = (ListView) parentLayout.findViewById(R.id.calcHistory);
            CalculatorHistoryAdapter calculatorHistoryAdapter =
                    new CalculatorHistoryAdapter(this, this, calculatedItems);
            historyAdapter = calculatorHistoryAdapter;
            historyList.setAdapter((ListAdapter) calculatorHistoryAdapter);
        }
        View mainCalculator = parentLayout.findViewById(R.id.main_calculator);
        historyList.setVisibility(View.VISIBLE);
        mainCalculator.setVisibility(View.GONE);
    }

    @Override
    public void insertContent(int position) {
        CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
        String editorText = editor.getText().toString();
        int caretPosition = editor.getSelectionStart();
        if (caretPosition == 1 && "0".equals(editorText)) {
            editor.setText(item.getExpression());
            caretPosition--;
        } else {
            editor.setText(editorText.substring(0, caretPosition) + item.getExpression()
                    + editorText.substring(caretPosition));
        }
        editor.setSelection(item.getExpression().length() + caretPosition);
        editor.requestFocus();
        hideHistory();
    }

    @Override
    public void replaceContent(int position) {
        CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
        editor.setText(item.getExpression());
        EditText editText = editor;
        int length = item.getExpression().length();
        caretPosition = length;
        editText.setSelection(length);
        editor.requestFocus();
        hideHistory();
    }

    @Override
    public void deleteHistory(int position) {
        calculatedItems.remove(position);
        CalculatorHistoryAdapter calculatorHistoryAdapter = historyAdapter;
        if (calculatorHistoryAdapter != null) {
            calculatorHistoryAdapter.notifyDataSetChanged();
        }
    }

    public void hideHistory() {
        parentLayout.findViewById(R.id.calcHistory).setVisibility(View.GONE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.VISIBLE);
    }

    public void minimizeView(View view) {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
        View view2 = quickLaunchView;
        if (view2 != null) {
            view2.setVisibility(View.GONE);
        }
    }

    public void clearAction(View view) {
        editor.setText("0");
        EditText editText = editor;
        caretPosition = 1;
        editText.setSelection(1);
    }

    public void deleteAction(View view) {
        String input = editor.getText().toString();
        if (input.startsWith(getResources().getString(R.string.invalid_syntax))) {
            clearAction(null);
            return;
        }
        int selectionStart = editor.getSelectionStart();
        caretPosition = selectionStart;
        if (selectionStart == 0) {
            return;
        }
        if (selectionStart == input.length()) {
            editor.setText(input.length() == 1 ? "0" : input.substring(0, input.length() - 1));
        } else {
            editor.setText(input.substring(0, caretPosition - 1) + input.substring(caretPosition));
        }
        if ("0".equals(editor.getText().toString())) {
            EditText editText = editor;
            caretPosition = 1;
            editText.setSelection(1);
        } else {
            EditText editText2 = editor;
            int i = caretPosition - 1;
            caretPosition = i;
            int i2 = i >= 0 ? i : 0;
            caretPosition = i2;
            editText2.setSelection(i2);
        }
        editor.requestFocus();
    }

    public void caretLeft(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            String str = previousInvalidExpression;
            if (str != null) {
                editor.setText(str);
            }
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        int selectionStart = editor.getSelectionStart();
        caretPosition = selectionStart;
        int i = selectionStart - 1;
        caretPosition = i;
        if (i < 0) {
            i = textLength;
        }
        caretPosition = i;
        editor.setSelection(i);
        editor.requestFocus();
    }

    public void caretRight(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            String str = previousInvalidExpression;
            if (str != null) {
                editor.setText(str);
            }
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        int selectionStart = editor.getSelectionStart();
        caretPosition = selectionStart;
        int i = selectionStart + 1;
        caretPosition = i;
        if (i > textLength) {
            i = 0;
        }
        caretPosition = i;
        editor.setSelection(i);
        editor.requestFocus();
    }

    public void buttonAction(View view) {
        String updatedContent;
        if (view instanceof Button) {
            Button button = (Button) view;
            String fieldContent = editor.getText().toString();
            caretPosition = editor.getSelectionStart();
            if (fieldContent.startsWith(getResources().getString(R.string.invalid_syntax))) {
                deleteAction(null);
                caretPosition = editor.getSelectionStart();
            }
            if (caretPosition == fieldContent.length()) {
                updatedContent =
                        ((!fieldContent.equals("0") || button.getText().toString().equals(".")) ? fieldContent : "")
                        + "" + ((Object) button.getText());
            } else {
                updatedContent = fieldContent.substring(0, caretPosition) + ((Object) button.getText())
                        + fieldContent.substring(caretPosition);
            }
            editor.setText(updatedContent);
            EditText editText = editor;
            int i = caretPosition + 1;
            caretPosition = i;
            int length = i > updatedContent.length() ? updatedContent.length() : caretPosition;
            caretPosition = length;
            editText.setSelection(length);
            editor.requestFocus();
        }
    }

    public void computeCalculation(View view) {
        try {
            String editorText = editor.getText().toString();
            if (editorText.startsWith(getResources().getString(R.string.invalid_syntax))) {
                return;
            }
            CompoundExpression firstResult = new CompoundExpression(editorText);
            Expression result = firstResult.compute();
            if (result != null) {
                calculatedItems.add(0, new CalcItem(editorText, result.getExact()));
                CalculatorHistoryAdapter calculatorHistoryAdapter = historyAdapter;
                if (calculatorHistoryAdapter != null) {
                    calculatorHistoryAdapter.notifyDataSetChanged();
                }
            }
            editor.setText(result == null ? getResources().getString(R.string.invalid_syntax) : result.getExact());
            previousInvalidExpression = editorText;
            EditText editText = editor;
            int length = editText.getText().length();
            caretPosition = length;
            editText.setSelection(length);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void closeView(View view) {
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        View view = quickLaunchView;
        if (view != null) {
            window.removeView(view);
        }
        View view2 = parentLayout;
        if (view2 != null) {
            window.removeView(view2);
        }
    }

    public static class CalcItem {
        private final String answer;
        private final String expression;

        public CalcItem(String expression, String answer) {
            this.expression = expression;
            this.answer = answer;
        }

        public String getExpression() {
            return expression;
        }

        public String getAnswer() {
            return answer;
        }
    }
}
