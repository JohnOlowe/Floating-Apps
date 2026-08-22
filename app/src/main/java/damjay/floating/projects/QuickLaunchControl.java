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
        quickLaunchView.findViewById(R.id.quick_launch_pdf).setOnClickListener((v) -> {
            Intent intent = new Intent();
            intent.setClass(context2, FloatingPDFActivity.class);
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context2.startActivity(intent);
        });
        quickLaunchView.findViewById(R.id.quick_launch_calc)
                .setOnClickListener(getServiceClickListener(CalculatorService.class));
        quickLaunchView.findViewById(R.id.quick_launch_bible)
                .setOnClickListener(getServiceClickListener(BibleService.class));
        quickLaunchView.findViewById(R.id.quick_launch_music)
                .setOnClickListener(getServiceClickListener(PlayerService.class));
        quickLaunchView.findViewById(R.id.quick_launch_notes)
                .setOnClickListener(getServiceClickListener(NoteService.class));
        return quickLaunchView;
    }

    private static View.OnClickListener getServiceClickListener(Class<?> clazz) {
        return (view) -> {
            Intent intent = new Intent(context, clazz);
            context.startService(intent);
        };
    }
}
