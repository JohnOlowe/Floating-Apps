package damjay.floating.projects;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import damjay.floating.projects.bible.BibleService;
import damjay.floating.projects.calculate.CalculatorService;
import damjay.floating.projects.music.PlayerService;
import damjay.floating.projects.notes.NoteService;

/**
 * Quick launch control providing rapid access to floating services.
 * Creates and configures quick launch views for PDF, calculator, bible,
 * music player, and note services.
 */
public class QuickLaunchControl {

    private static Context context;

    /** Create quick launch view with service click handlers */
    public static View getQuickLaunchView(Context appContext) {
        context = appContext;
        View quickLaunchView = LayoutInflater.from(appContext).inflate(R.layout.quick_launch, null);

        quickLaunchView.findViewById(R.id.quick_launch_pdf).setOnClickListener(v -> {
            Intent intent = new Intent(appContext, FloatingPDFActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            appContext.startActivity(intent);
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

    /** Create click listener that launches a floating service */
    private static View.OnClickListener getServiceClickListener(Class<?> serviceClass) {
        return v -> {
            Intent intent = new Intent(context, serviceClass);
            context.startService(intent);
        };
    }
}
