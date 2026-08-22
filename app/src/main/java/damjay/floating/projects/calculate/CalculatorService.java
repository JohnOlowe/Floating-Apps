package damjay.floating.projects.calculate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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
        this.params = ViewsUtils.getFloatingLayoutParams();
        WindowManager windowManager = (WindowManager) getSystemService("window");
        this.window = windowManager;
        windowManager.addView(this.parentLayout, this.params);
        addTouchListeners();
    }

    private void getViews() {
        View viewInflate = LayoutInflater.from(this).inflate(R.layout.calculator_layout, (ViewGroup) null);
        this.parentLayout = viewInflate;
        this.editor = (EditText) viewInflate.findViewById(R.id.calcField);
        this.collapsed = this.parentLayout.findViewById(R.id.collapsedCalc);
        this.expanded = this.parentLayout.findViewById(R.id.expandedCalc);
        this.mainCalculator = this.parentLayout.findViewById(R.id.main_calculator);
        if (this.historyList == null) {
            this.historyList = (ListView) this.parentLayout.findViewById(R.id.calcHistory);
            CalculatorHistoryAdapter calculatorHistoryAdapter = new CalculatorHistoryAdapter(this, this, this.calculatedItems);
            this.historyAdapter = calculatorHistoryAdapter;
            this.historyList.setAdapter((ListAdapter) calculatorHistoryAdapter);
        }
        this.collapsed.setOnClickListener((view) -> this.m171xa8b847a8(view));
        this.parentLayout.findViewById(R.id.calcLaunchActivity).setOnClickListener((view) -> this.m172xc2d3c647(view));
        this.parentLayout.findViewById(R.id.show_quick_launch).setOnClickListener((view) -> this.m173xdcef44e6(view));
        this.expanded.getViewTreeObserver().addOnGlobalLayoutListener(() -> this.m174xf70ac385());
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = () -> this.m175x11264224();
        this.mainCalculator.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        this.historyList.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        EditText editText = this.editor;
        int length = editText.getText().length();
        this.caretPosition = length;
        editText.setSelection(length);
    }

    void m171xa8b847a8(View view) {
        this.expanded.setVisibility(0);
        this.collapsed.setVisibility(8);
        View view2 = this.quickLaunchView;
        if (view2 != null) {
            view2.setVisibility(0);
        }
    }

    void m172xc2d3c647(View v) {
        ViewsUtils.launchApp(this, MainActivity.class);
    }

    void m173xdcef44e6(View v) {
        View view = this.quickLaunchView;
        if (view == null) {
            View quickLaunchView = QuickLaunchControl.getQuickLaunchView(this);
            this.quickLaunchView = quickLaunchView;
            WindowManager windowManager = this.window;
            WindowManager.LayoutParams quickLaunchParams = getQuickLaunchParams(null);
            this.quickLaunchParams = quickLaunchParams;
            windowManager.addView(quickLaunchView, quickLaunchParams);
            return;
        }
        this.window.removeView(view);
        this.quickLaunchView = null;
    }

    void m174xf70ac385() {
        this.expanded.getLayoutParams().width = ViewsUtils.getViewWidth(275.0f);
    }

    void m175x11264224() {
        int measuredHeight = this.mainCalculator.getMeasuredHeight();
        if (measuredHeight < 1) {
            return;
        }
        this.historyList.getLayoutParams().height = measuredHeight;
    }

    private WindowManager.LayoutParams getQuickLaunchParams(WindowManager.LayoutParams quickLaunchParams) {
        int y = this.params.y;
        int topNavHeight = this.parentLayout.findViewById(R.id.topNav).getMeasuredHeight();
        int viewHeight = this.quickLaunchView.getMeasuredHeight();
        int viewHeight2 = viewHeight <= 0 ? topNavHeight : viewHeight;
        WindowManager.LayoutParams quickLaunchParams2 = quickLaunchParams == null ? ViewsUtils.getFloatingLayoutParams() : quickLaunchParams;
        if (y <= viewHeight2) {
            quickLaunchParams2.y = topNavHeight + y;
        } else {
            quickLaunchParams2.y = y - viewHeight2;
        }
        quickLaunchParams2.x = this.params.x;
        return quickLaunchParams2;
    }

    private void addTouchListeners() {
        View.OnTouchListener touchListener = ViewsUtils.getViewTouchListener(this, this.parentLayout, this.window, this.params);
        View.OnTouchListener mainTouchListener = (view, motionEvent) -> this.m170x866193b4(touchListener, view, motionEvent);
        ViewsUtils.addTouchListener(this.expanded, mainTouchListener, true, true, ListView.class, null);
        ViewsUtils.addTouchListener(this.collapsed, touchListener, true, true, new Class[0]);
    }

    boolean m170x866193b4(View.OnTouchListener touchListener, View view, MotionEvent event) {
        boolean result = touchListener.onTouch(view, event);
        View view2 = this.quickLaunchView;
        if (view2 != null) {
            this.window.updateViewLayout(view2, getQuickLaunchParams(this.quickLaunchParams));
        }
        return result;
    }

    public void showHistory(View view) {
        if (this.parentLayout.findViewById(R.id.calcHistory).getVisibility() == 0) {
            hideHistory();
            return;
        }
        if (this.historyList == null) {
            this.historyList = (ListView) this.parentLayout.findViewById(R.id.calcHistory);
            CalculatorHistoryAdapter calculatorHistoryAdapter = new CalculatorHistoryAdapter(this, this, this.calculatedItems);
            this.historyAdapter = calculatorHistoryAdapter;
            this.historyList.setAdapter((ListAdapter) calculatorHistoryAdapter);
        }
        View mainCalculator = this.parentLayout.findViewById(R.id.main_calculator);
        this.historyList.setVisibility(0);
        mainCalculator.setVisibility(8);
    }

    @Override
    public void insertContent(int position) {
        CalcItem item = (CalcItem) this.historyList.getItemAtPosition(position);
        String editorText = this.editor.getText().toString();
        int caretPosition = this.editor.getSelectionStart();
        if (caretPosition == 1 && "0".equals(editorText)) {
            this.editor.setText(item.getExpression());
            caretPosition--;
        } else {
            this.editor.setText(editorText.substring(0, caretPosition) + item.getExpression() + editorText.substring(caretPosition));
        }
        this.editor.setSelection(item.getExpression().length() + caretPosition);
        this.editor.requestFocus();
        hideHistory();
    }

    @Override
    public void replaceContent(int position) {
        CalcItem item = (CalcItem) this.historyList.getItemAtPosition(position);
        this.editor.setText(item.getExpression());
        EditText editText = this.editor;
        int length = item.getExpression().length();
        this.caretPosition = length;
        editText.setSelection(length);
        this.editor.requestFocus();
        hideHistory();
    }

    @Override
    public void deleteHistory(int position) {
        this.calculatedItems.remove(position);
        CalculatorHistoryAdapter calculatorHistoryAdapter = this.historyAdapter;
        if (calculatorHistoryAdapter != null) {
            calculatorHistoryAdapter.notifyDataSetChanged();
        }
    }

    public void hideHistory() {
        this.parentLayout.findViewById(R.id.calcHistory).setVisibility(8);
        this.parentLayout.findViewById(R.id.main_calculator).setVisibility(0);
    }

    public void minimizeView(View view) {
        this.expanded.setVisibility(8);
        this.collapsed.setVisibility(0);
        View view2 = this.quickLaunchView;
        if (view2 != null) {
            view2.setVisibility(8);
        }
    }

    public void clearAction(View view) {
        this.editor.setText("0");
        EditText editText = this.editor;
        this.caretPosition = 1;
        editText.setSelection(1);
    }

    public void deleteAction(View view) {
        String input = this.editor.getText().toString();
        if (input.startsWith(getResources().getString(R.string.invalid_syntax))) {
            clearAction(null);
            return;
        }
        int selectionStart = this.editor.getSelectionStart();
        this.caretPosition = selectionStart;
        if (selectionStart == 0) {
            return;
        }
        if (selectionStart == input.length()) {
            this.editor.setText(input.length() == 1 ? "0" : input.substring(0, input.length() - 1));
        } else {
            this.editor.setText(input.substring(0, this.caretPosition - 1) + input.substring(this.caretPosition));
        }
        if ("0".equals(this.editor.getText().toString())) {
            EditText editText = this.editor;
            this.caretPosition = 1;
            editText.setSelection(1);
        } else {
            EditText editText2 = this.editor;
            int i = this.caretPosition - 1;
            this.caretPosition = i;
            int i2 = i >= 0 ? i : 0;
            this.caretPosition = i2;
            editText2.setSelection(i2);
        }
        this.editor.requestFocus();
    }

    public void caretLeft(View view) {
        int textLength = this.editor.getText().length();
        if (this.editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            String str = this.previousInvalidExpression;
            if (str != null) {
                this.editor.setText(str);
            }
            this.previousInvalidExpression = null;
            return;
        }
        this.editor.setCursorVisible(true);
        int selectionStart = this.editor.getSelectionStart();
        this.caretPosition = selectionStart;
        int i = selectionStart - 1;
        this.caretPosition = i;
        if (i < 0) {
            i = textLength;
        }
        this.caretPosition = i;
        this.editor.setSelection(i);
        this.editor.requestFocus();
    }

    public void caretRight(View view) {
        int textLength = this.editor.getText().length();
        if (this.editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            String str = this.previousInvalidExpression;
            if (str != null) {
                this.editor.setText(str);
            }
            this.previousInvalidExpression = null;
            return;
        }
        this.editor.setCursorVisible(true);
        int selectionStart = this.editor.getSelectionStart();
        this.caretPosition = selectionStart;
        int i = selectionStart + 1;
        this.caretPosition = i;
        if (i > textLength) {
            i = 0;
        }
        this.caretPosition = i;
        this.editor.setSelection(i);
        this.editor.requestFocus();
    }

    public void buttonAction(View view) {
        String updatedContent;
        if (view instanceof Button) {
            Button button = (Button) view;
            String fieldContent = this.editor.getText().toString();
            this.caretPosition = this.editor.getSelectionStart();
            if (fieldContent.startsWith(getResources().getString(R.string.invalid_syntax))) {
                deleteAction(null);
                this.caretPosition = this.editor.getSelectionStart();
            }
            if (this.caretPosition == fieldContent.length()) {
                updatedContent = ((!fieldContent.equals("0") || button.getText().toString().equals(".")) ? fieldContent : "") + "" + ((Object) button.getText());
            } else {
                updatedContent = fieldContent.substring(0, this.caretPosition) + ((Object) button.getText()) + fieldContent.substring(this.caretPosition);
            }
            this.editor.setText(updatedContent);
            EditText editText = this.editor;
            int i = this.caretPosition + 1;
            this.caretPosition = i;
            int length = i > updatedContent.length() ? updatedContent.length() : this.caretPosition;
            this.caretPosition = length;
            editText.setSelection(length);
            this.editor.requestFocus();
        }
    }

    public void computeCalculation(View view) {
        try {
            String editorText = this.editor.getText().toString();
            if (editorText.startsWith(getResources().getString(R.string.invalid_syntax))) {
                return;
            }
            CompoundExpression firstResult = new CompoundExpression(editorText);
            Expression result = firstResult.compute();
            if (result != null) {
                this.calculatedItems.add(0, new CalcItem(editorText, result.getExact()));
                CalculatorHistoryAdapter calculatorHistoryAdapter = this.historyAdapter;
                if (calculatorHistoryAdapter != null) {
                    calculatorHistoryAdapter.notifyDataSetChanged();
                }
            }
            this.editor.setText(result == null ? getResources().getString(R.string.invalid_syntax) : result.getExact());
            this.previousInvalidExpression = editorText;
            EditText editText = this.editor;
            int length = editText.getText().length();
            this.caretPosition = length;
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
        View view = this.quickLaunchView;
        if (view != null) {
            this.window.removeView(view);
        }
        View view2 = this.parentLayout;
        if (view2 != null) {
            this.window.removeView(view2);
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
            return this.expression;
        }

        public String getAnswer() {
            return this.answer;
        }
    }
}
