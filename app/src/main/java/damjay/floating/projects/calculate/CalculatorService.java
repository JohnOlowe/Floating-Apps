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

import java.util.ArrayList;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.QuickLaunchControl;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.CalculatorHistoryAdapter;
import damjay.floating.projects.utils.ViewsUtils;

/**
 * Floating calculator service providing arithmetic computation,
 * expression history, and quick-launch controls.
 */
public class CalculatorService extends Service implements CalculatorHistoryAdapter.HistoryListener {

    // Main layout components
    private View parentLayout;
    private View collapsed;
    private View expanded;
    private View mainCalculator;
    private EditText editor;
    private ListView historyList;
    private CalculatorHistoryAdapter historyAdapter;

    // Window and layout parameters
    private WindowManager.LayoutParams params;
    private WindowManager window;

    // Quick launch support
    private View quickLaunchView;
    private WindowManager.LayoutParams quickLaunchParams;

    // History tracking
    private final ArrayList<CalcItem> calculatedItems = new ArrayList<>();

    // Editor state
    private int caretPosition = 1;
    private String previousInvalidExpression;

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
        window = (WindowManager) getSystemService(WINDOW_SERVICE);
        window.addView(parentLayout, params);
        addTouchListeners();
    }

    /** Initialize all calculator views and set up listeners */
    private void getViews() {
        parentLayout = LayoutInflater.from(this).inflate(R.layout.calculator_layout, null);
        editor = parentLayout.findViewById(R.id.calcField);
        collapsed = parentLayout.findViewById(R.id.collapsedCalc);
        expanded = parentLayout.findViewById(R.id.expandedCalc);
        mainCalculator = parentLayout.findViewById(R.id.main_calculator);

        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }

        // Collapse/expand toggle
        collapsed.setOnClickListener(view -> {
            expanded.setVisibility(View.VISIBLE);
            collapsed.setVisibility(View.GONE);
            if (quickLaunchView != null) {
                quickLaunchView.setVisibility(View.VISIBLE);
            }
        });

        // Launch main app
        parentLayout.findViewById(R.id.calcLaunchActivity)
                .setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));

        // Quick launch toggle
        parentLayout.findViewById(R.id.show_quick_launch).setOnClickListener(v -> {
            if (quickLaunchView == null) {
                quickLaunchView = QuickLaunchControl.getQuickLaunchView(this);
                quickLaunchParams = getQuickLaunchParams(null);
                window.addView(quickLaunchView, quickLaunchParams);
            } else {
                window.removeView(quickLaunchView);
                quickLaunchView = null;
            }
        });

        // Dynamic width for expanded calculator
        expanded.getViewTreeObserver().addOnGlobalLayoutListener(
                () -> expanded.getLayoutParams().width = ViewsUtils.getViewWidth(275.0f));

        // Match history list height to calculator height
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> {
            int measuredHeight = mainCalculator.getMeasuredHeight();
            if (measuredHeight < 1) return;
            historyList.getLayoutParams().height = measuredHeight;
        };
        mainCalculator.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        historyList.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        // Initialize caret position
        editor.setSelection(caretPosition = editor.getText().length());
    }

    /** Calculate quick launch window position relative to calculator */
    private WindowManager.LayoutParams getQuickLaunchParams(WindowManager.LayoutParams existingParams) {
        int y = params.y;
        int topNavHeight = parentLayout.findViewById(R.id.topNav).getMeasuredHeight();
        int measuredHeight = quickLaunchView.getMeasuredHeight();
        int viewHeight = measuredHeight <= 0 ? topNavHeight : measuredHeight;

        WindowManager.LayoutParams result = existingParams == null
                ? ViewsUtils.getFloatingLayoutParams() : existingParams;
        result.y = (y <= viewHeight) ? topNavHeight + y : y - viewHeight;
        result.x = params.x;
        return result;
    }

    /** Add touch listeners for floating behavior */
    private void addTouchListeners() {
        View.OnTouchListener touchListener = ViewsUtils.getViewTouchListener(
                this, parentLayout, window, params);

        // Main expanded area with quick-launch position tracking
        View.OnTouchListener mainListener = (view, event) -> {
            boolean result = touchListener.onTouch(view, event);
            if (quickLaunchView != null) {
                window.updateViewLayout(quickLaunchView, getQuickLaunchParams(quickLaunchParams));
            }
            return result;
        };
        ViewsUtils.addTouchListener(expanded, mainListener, true, true, ListView.class, null);
        ViewsUtils.addTouchListener(collapsed, touchListener, true, true);
    }

    // --- History actions ---

    public void showHistory(View view) {
        if (parentLayout.findViewById(R.id.calcHistory).getVisibility() == View.VISIBLE) {
            hideHistory();
            return;
        }
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }
        parentLayout.findViewById(R.id.calcHistory).setVisibility(View.VISIBLE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.GONE);
    }

    public void hideHistory() {
        parentLayout.findViewById(R.id.calcHistory).setVisibility(View.GONE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.VISIBLE);
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
        caretPosition = item.getExpression().length();
        editor.setSelection(caretPosition);
        editor.requestFocus();
        hideHistory();
    }

    @Override
    public void deleteHistory(int position) {
        calculatedItems.remove(position);
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
    }

    // --- Calculator actions ---

    public void minimizeView(View view) {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
        if (quickLaunchView != null) {
            quickLaunchView.setVisibility(View.GONE);
        }
    }

    public void clearAction(View view) {
        editor.setText("0");
        caretPosition = 1;
        editor.setSelection(1);
    }

    public void deleteAction(View view) {
        String input = editor.getText().toString();
        if (input.startsWith(getString(R.string.invalid_syntax))) {
            clearAction(null);
            return;
        }
        caretPosition = editor.getSelectionStart();
        if (caretPosition == 0) return;
        if (caretPosition == input.length()) {
            editor.setText(input.length() == 1 ? "0" : input.substring(0, input.length() - 1));
        } else {
            editor.setText(input.substring(0, caretPosition - 1) + input.substring(caretPosition));
        }
        if ("0".equals(editor.getText().toString())) {
            caretPosition = 1;
            editor.setSelection(1);
        } else {
            caretPosition = Math.max(0, caretPosition - 1);
            editor.setSelection(caretPosition);
        }
        editor.requestFocus();
    }

    public void caretLeft(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getString(R.string.invalid_syntax))) {
            if (previousInvalidExpression != null) {
                editor.setText(previousInvalidExpression);
            }
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = (caretPosition - 1 < 0) ? textLength : caretPosition - 1;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }

    public void caretRight(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getString(R.string.invalid_syntax))) {
            if (previousInvalidExpression != null) {
                editor.setText(previousInvalidExpression);
            }
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = (caretPosition + 1 > textLength) ? 0 : caretPosition + 1;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }

    public void buttonAction(View view) {
        if (!(view instanceof Button)) return;
        Button button = (Button) view;
        String fieldContent = editor.getText().toString();
        caretPosition = editor.getSelectionStart();
        if (fieldContent.startsWith(getString(R.string.invalid_syntax))) {
            deleteAction(null);
            caretPosition = editor.getSelectionStart();
        }
        if (caretPosition == fieldContent.length()) {
            String updatedContent = (!fieldContent.equals("0") || button.getText().toString().equals("."))
                    ? fieldContent + button.getText() : button.getText().toString();
            editor.setText(updatedContent);
        } else {
            String updatedContent = fieldContent.substring(0, caretPosition)
                    + button.getText() + fieldContent.substring(caretPosition);
            editor.setText(updatedContent);
        }
        caretPosition++;
        caretPosition = Math.min(caretPosition, editor.getText().length());
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }

    public void computeCalculation(View view) {
        try {
            String editorText = editor.getText().toString();
            if (editorText.startsWith(getString(R.string.invalid_syntax))) return;
            CompoundExpression expression = new CompoundExpression(editorText);
            Expression result = expression.compute();
            if (result != null) {
                calculatedItems.add(0, new CalcItem(editorText, result.getExact()));
                if (historyAdapter != null) {
                    historyAdapter.notifyDataSetChanged();
                }
            }
            editor.setText(result == null ? getString(R.string.invalid_syntax) : result.getExact());
            previousInvalidExpression = editorText;
            caretPosition = editor.getText().length();
            editor.setSelection(caretPosition);
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
        if (quickLaunchView != null) {
            window.removeView(quickLaunchView);
        }
        if (parentLayout != null) {
            window.removeView(parentLayout);
        }
    }

    /** Data item representing a calculated expression and its result */
    public static class CalcItem {
        private final String expression;
        private final String answer;

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
