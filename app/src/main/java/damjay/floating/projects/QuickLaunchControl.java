package damjay.floating.projects;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import damjay.floating.projects.bible.BibleService;
import damjay.floating.projects.calculate.CalculatorService;
import damjay.floating.projects.music.PlayerService;
import damjay.floating.projects.notes.NoteService;

public class QuickLaunchControl {
    private static Context context;

    public static View getQuickLaunchView(Context context2) {
        context = context2;
        View quickLaunchView = LayoutInflater.from(context2).inflate(R.layout.quick_launch, (ViewGroup) null);
        quickLaunchView.findViewById(R.id.quick_launch_pdf).setOnClickListener((view) -> QuickLaunchControl.lambda$getQuickLaunchView$0(context2, view));
        quickLaunchView.findViewById(R.id.quick_launch_calc).setOnClickListener(getServiceClickListener(CalculatorService.class));
        quickLaunchView.findViewById(R.id.quick_launch_bible).setOnClickListener(getServiceClickListener(BibleService.class));
        quickLaunchView.findViewById(R.id.quick_launch_music).setOnClickListener(getServiceClickListener(PlayerService.class));
        quickLaunchView.findViewById(R.id.quick_launch_notes).setOnClickListener(getServiceClickListener(NoteService.class));
        return quickLaunchView;
    }

    static void lambda$getQuickLaunchView$0(Context context2, View v) {
        Intent intent = new Intent();
        intent.setClass(context2, FloatingPDFActivity.class);
        intent.setFlags(872415232);
        context2.startActivity(intent);
    }

    private static View.OnClickListener getServiceClickListener(Class<?> clazz) {
        return (view) -> QuickLaunchControl.lambda$getServiceClickListener$1(clazz, view);
    }

    static void lambda$getServiceClickListener$1(Class clazz, View view) {
        Intent intent = new Intent(context, (Class<?>) clazz);
        context.startService(intent);
    }
}
