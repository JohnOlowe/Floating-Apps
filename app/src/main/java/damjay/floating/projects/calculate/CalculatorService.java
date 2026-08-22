package damjay.floating.projects.calculate;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;

import damjay.floating.projects.MainActivity;
import damjay.floating.projects.QuickLaunchControl;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.CalculatorHistoryAdapter;
import damjay.floating.projects.utils.ViewsUtils;

public class CalculatorService extends Service implements CalculatorHistoryAdapter.HistoryListener {
    private View parentLayout;
    private View collapsed;
    private View expanded;
    private View mainCalculator;
    private ListView historyList;
    private EditText editor;
    
    private CalculatorHistoryAdapter historyAdapter;
    
    private int caretPosition = 1;

    private LayoutParams params;
    private WindowManager window;

    private ArrayList<CalcItem> calculatedItems = new ArrayList<>();

    private String previousInvalidExpression;

    private View quickLaunchView;
    private LayoutParams quickLaunchParams;

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

    private void getViews() {
        parentLayout = LayoutInflater.from(this).inflate(R.layout.calculator_layout, null);
        editor = parentLayout.findViewById(R.id.calcField);
        collapsed = parentLayout.findViewById(R.id.collapsedCalc);
        expanded = parentLayout.findViewById(R.id.expandedCalc);
        mainCalculator = parentLayout.findViewById(R.id.main_calculator);
        
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }

        collapsed.setOnClickListener(view -> {
            expanded.setVisibility(View.VISIBLE);
            collapsed.setVisibility(View.GONE);
            if (quickLaunchView != null) {
                quickLaunchView.setVisibility(View.VISIBLE);
            }
        });
        
        parentLayout.findViewById(R.id.calcLaunchActivity).setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));

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

        expanded.getViewTreeObserver().addOnGlobalLayoutListener(() -> expanded.getLayoutParams().width = ViewsUtils.getViewWidth(275.0f));
        
        ViewTreeObserver.OnGlobalLayoutListener layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    int measuredHeight = mainCalculator.getMeasuredHeight();
                    if (measuredHeight < 1) return;
                    historyList.getLayoutParams().height = measuredHeight;
                }
                
            };
            mainCalculator.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            historyList.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            editor.setSelection(caretPosition = editor.getText().length());
    }

    private LayoutParams getQuickLaunchParams(LayoutParams inputParams) {
        int y = params.y;
        int topNavHeight = parentLayout.findViewById(R.id.topNav).getMeasuredHeight();
        int viewHeight = quickLaunchView.getMeasuredHeight();
        int effectiveHeight = viewHeight <= 0 ? topNavHeight : viewHeight;

        LayoutParams resultParams = inputParams == null ? ViewsUtils.getFloatingLayoutParams() : inputParams;
        if (y <= effectiveHeight) {
            resultParams.y = topNavHeight + y;
        } else {
            resultParams.y = y - effectiveHeight;
        }
        resultParams.x = params.x;
        return resultParams;
    }

    private void addTouchListeners() {
        View.OnTouchListener touchListener = ViewsUtils.getViewTouchListener(this, parentLayout, window, params);
        View.OnTouchListener mainTouchListener = (v, event) -> {
            boolean result = touchListener.onTouch(v, event);
            if (quickLaunchView != null) {
                window.updateViewLayout(quickLaunchView, getQuickLaunchParams(quickLaunchParams));
            }
            return result;
        };
        ViewsUtils.addTouchListener(expanded, mainTouchListener, true, true, ListView.class, null);
        ViewsUtils.addTouchListener(collapsed, touchListener, true, true);
    }

    public void showHistory(final View view) {
        if (parentLayout.findViewById(R.id.calcHistory).getVisibility() == View.VISIBLE) {
            hideHistory(view);
            return;
        }
        if (historyList == null) {
            historyList = parentLayout.findViewById(R.id.calcHistory);
            historyAdapter = new CalculatorHistoryAdapter(this, this, calculatedItems);
            historyList.setAdapter(historyAdapter);
        }

        // Show the history views
        historyList.setVisibility(View.VISIBLE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.GONE);
    }

    @Override
    public void insertContent(int position) {
        CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
        // Insert at caret position
        String editorText = editor.getText().toString();
        int caret = editor.getSelectionStart();
        if (caret == 1 && "0".equals(editorText)) {
            editor.setText(item.getExpression());
            caret--;
        } else {
            editor.setText(editorText.substring(0, caret) + item.getExpression() + editorText.substring(caret));
        }
        // Shift the caretPosition to the end of inserted expression
        editor.setSelection(caret + item.getExpression().length());
        editor.requestFocus();
        hideHistory(null);
    }

    @Override
    public void replaceContent(int position) {
        CalcItem item = (CalcItem) historyList.getItemAtPosition(position);
        editor.setText(item.getExpression());
        editor.setSelection(caretPosition = item.getExpression().length());
        editor.requestFocus();
        hideHistory(null);
    }

    @Override
    public void deleteHistory(int position) {
        calculatedItems.remove(position);
        if (historyAdapter != null) {
            historyAdapter.notifyDataSetChanged();
        }
    }

    public void hideHistory(View view) {
        parentLayout.findViewById(R.id.calcHistory).setVisibility(View.GONE);
        parentLayout.findViewById(R.id.main_calculator).setVisibility(View.VISIBLE);
    }

    public void minimizeView(View view) {
        expanded.setVisibility(View.GONE);
        collapsed.setVisibility(View.VISIBLE);
        if (quickLaunchView != null) {
            quickLaunchView.setVisibility(View.GONE);
        }
    }
    
    public void clearAction(View view) {
        editor.setText("0");
        editor.setSelection(caretPosition = 1);
    }

    public void deleteAction(View view) {
        String input = editor.getText().toString();
        if (input.startsWith(getResources().getString(R.string.invalid_syntax))) {
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
            editor.setSelection(caretPosition = 1);
        } else {
            editor.setSelection(caretPosition = (--caretPosition < 0 ? 0 : caretPosition));
        }
        editor.requestFocus();
    }
    
    public void caretLeft(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            if (previousInvalidExpression != null)
                editor.setText(previousInvalidExpression);
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = --caretPosition < 0 ? textLength : caretPosition;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }
    
    public void caretRight(View view) {
        int textLength = editor.getText().length();
        if (editor.getText().toString().startsWith(getResources().getString(R.string.invalid_syntax))) {
            if (previousInvalidExpression != null)
                editor.setText(previousInvalidExpression);
            previousInvalidExpression = null;
            return;
        }
        editor.setCursorVisible(true);
        caretPosition = editor.getSelectionStart();
        caretPosition = ++caretPosition > textLength ? 0 : caretPosition;
        editor.setSelection(caretPosition);
        editor.requestFocus();
    }
    
    public void buttonAction(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String fieldContent = editor.getText().toString();
            String updatedContent;
            
            caretPosition = editor.getSelectionStart();
            if (fieldContent.startsWith(getResources().getString(R.string.invalid_syntax))) {
                deleteAction(null);
                caretPosition = editor.getSelectionStart();
            }
            if (caretPosition == fieldContent.length()) {
                updatedContent = (fieldContent.equals("0") && !button.getText().toString().equals(".") ? "" : fieldContent) + "" + button.getText();
            } else {
                updatedContent = fieldContent.substring(0, caretPosition) + button.getText() + fieldContent.substring(caretPosition);
            }
            editor.setText(updatedContent);
            editor.setSelection(caretPosition = (++caretPosition > updatedContent.length() ? updatedContent.length() : caretPosition));
            editor.requestFocus();
        }
    }

    public void computeCalculation(View view) {
        try {
            String editorText = editor.getText().toString();
            if (editorText.startsWith(getResources().getString(R.string.invalid_syntax)))
                return;
            CompoundExpression firstResult = new CompoundExpression(editorText);
            Expression result = firstResult.compute();
            if (result != null) {
                calculatedItems.add(new CalcItem(editorText, result.getExact()));
                if (historyAdapter != null) {
                    historyAdapter.notifyDataSetChanged();
                }
            }
            editor.setText(result == null ? getResources().getString(R.string.invalid_syntax) : result.getExact());
            previousInvalidExpression = editorText;
            editor.setSelection(caretPosition = editor.getText().length());
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
        if (quickLaunchView != null) window.removeView(quickLaunchView);
        if (parentLayout != null) window.removeView(parentLayout);
    }

    public static class CalcItem {
        private String expression;
        private String answer;

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
