package damjay.floating.projects.utils;

import java.math.BigDecimal;
import java.util.Date;

public class FormatUtils {
    public static String formatSize(long length) {
        String[] suffices = {" bytes", "KB", "MB", "GB", "TB"};
        int sizeType = 0;
        while (sizeType < suffices.length) {
            sizeType++;
            if ((length >>> ((int) (((long) sizeType) * 10))) == 0) {
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        double d = length;
        double d2 = 1 << ((sizeType - 1) * 10);
        Double.isNaN(d);
        Double.isNaN(d2);
        return sb.append(approximateNumberToTwoPlaces(d / d2)).append(suffices[sizeType - 1]).toString();
    }

    private static String approximateNumberToTwoPlaces(double doubleValue) {
        return Double.toString(new BigDecimal(doubleValue).setScale(2, 4).doubleValue());
    }

    public static String formatDate(long lastModified) {
        if (lastModified < 0) {
            return "";
        }
        Date date = new Date(lastModified);
        String[] months = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        int hours = date.getHours();
        int minutes = date.getMinutes();
        String dateString = months[date.getMonth()] + " " + date.getDate();
        String timeString = (hours % 12 != 0 ? hours % 12 : 12) + ":" + (minutes < 10 ? "0" : "") + minutes + (hours >= 12 ? " pm" : " am");
        return dateString + " " + timeString;
    }
}
