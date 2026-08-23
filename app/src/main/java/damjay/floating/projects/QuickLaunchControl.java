package damjay.floating.projects;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import damjay.floating.projects.bible.BibleService;
import damjay.floating.projects.calculate.CalculatorService;
import damjay.floating.projects.music.PlayerService;
import damjay.floating.projects.notes.NoteService;

public class QuickLaunchControl {
    private static Context context;

    public static View getQuickLaunchView(Context context) {
        QuickLaunchControl.context = context;
        View view = LayoutInflater.from(context).inflate(R.layout.quick_launch, null);
        view.findViewById(R.id.quick_launch_pdf).setOnClickListener(v -> {
            Intent intent = new Intent(context, FloatingPDFActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(intent);
        });
        view.findViewById(R.id.quick_launch_calc).setOnClickListener(getServiceClickListener(CalculatorService.class));
        view.findViewById(R.id.quick_launch_bible).setOnClickListener(getServiceClickListener(BibleService.class));
        view.findViewById(R.id.quick_launch_music).setOnClickListener(getServiceClickListener(PlayerService.class));
        view.findViewById(R.id.quick_launch_notes).setOnClickListener(getServiceClickListener(NoteService.class));
        return view;
    }

    private static View.OnClickListener getServiceClickListener(Class<?> clazz) {
        return v -> context.startService(new Intent(context, clazz));
    }
}
