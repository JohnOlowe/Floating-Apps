package damjay.floating.projects.bible;

import android.content.Context;
import damjay.floating.projects.utils.FileUtils;
import damjay.floating.projects.utils.ZipUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class CombinedChapterBibleSource {
    public static final String BIBLE_DATA = "Bible Data";
    public static final String INDEX = "Index";
    private File biblePath;
    private int[][] bookIndex;
    private String[] bookNames;
    private int[][] combinedChapterIndex;
    private final Context context;
    private char[] fileData;
    private int numberOfBooks;
    private int[] numberOfChapters;
    private String[] shortBookNames;
    private int[] startChapters;
    private char[] verseData;
    private int verseDataSize;
    private int currentBookIndex = -1;
    private int currentFileIndex = -1;
    private int currentChapterIndex = -1;

    public CombinedChapterBibleSource(Context context, File biblePath) throws IOException {
        this.context = context;
        if (checkCreated(biblePath)) {
            DataInputStream input = openStream("Bible Data/Index");
            int i = input.read();
            this.numberOfBooks = i;
            this.bookNames = new String[i];
            this.shortBookNames = new String[i];
            this.startChapters = new int[i];
            this.numberOfChapters = new int[i];
            this.combinedChapterIndex = new int[i][];
            for (int bookIndex = 0; bookIndex < this.numberOfBooks; bookIndex++) {
                this.bookNames[bookIndex] = input.readUTF();
                this.shortBookNames[bookIndex] = input.readUTF();
                this.startChapters[bookIndex] = input.readUnsignedShort();
                int numberOfChapters = input.readUnsignedShort();
                this.numberOfChapters[bookIndex] = numberOfChapters;
                this.combinedChapterIndex[bookIndex] = new int[numberOfChapters << 2];
                int previousFileNumber = 0;
                int verseDataOffset = 0;
                for (int chapterIndex = 0; chapterIndex < numberOfChapters; chapterIndex++) {
                    int fileNumber = input.read();
                    int allVersesLength = input.readInt();
                    int[] iArr = this.combinedChapterIndex[bookIndex];
                    iArr[chapterIndex << 2] = fileNumber;
                    if (fileNumber != previousFileNumber) {
                        verseDataOffset = 0;
                        previousFileNumber = fileNumber;
                    }
                    iArr[(chapterIndex << 2) + 1] = verseDataOffset;
                    iArr[(chapterIndex << 2) + 2] = allVersesLength;
                    iArr[(chapterIndex << 2) + 3] = input.read();
                    verseDataOffset += allVersesLength;
                }
            }
            input.close();
        }
    }

    private boolean checkCreated(File biblePath) {
        this.biblePath = biblePath;
        if (!new File(biblePath, "Bible Data/Index").exists()) {
            File zipFile = new File(biblePath, "Bible Data.zip");
            try {
                if (!FileUtils.copyStream(this.context.getAssets().open("Bible Data.zip"), new FileOutputStream(zipFile))) {
                    return false;
                }
                return ZipUtils.extractZip(zipFile, biblePath);
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public char[] getChapter(int bookIndex, int chapterIndex) throws IOException {
        loadChapter(bookIndex, chapterIndex);
        return this.verseData;
    }

    public int[] getChapterIndex(int bookIndex, int chapterIndex) throws IOException {
        loadChapter(bookIndex, chapterIndex);
        return this.bookIndex[chapterIndex];
    }

    private void loadChapter(int bookIndex, int chapterIndex) throws IOException {
        int i = this.currentBookIndex;
        if (i != bookIndex || this.currentChapterIndex != chapterIndex) {
            if (i != bookIndex || this.combinedChapterIndex[bookIndex][chapterIndex << 2] != this.currentFileIndex) {
                if (i != bookIndex) {
                    loadBookIndex(bookIndex);
                }
                this.currentFileIndex = this.combinedChapterIndex[bookIndex][chapterIndex << 2];
                DataInputStream input = openStream("Bible Data/" + this.shortBookNames[bookIndex] + "/" + this.shortBookNames[bookIndex] + " " + this.currentFileIndex);
                int length = input.readInt();
                byte[] byteArray = new byte[length];
                input.readFully(byteArray, 0, length);
                input.close();
                this.fileData = new char[length];
                int charIndex = 0;
                int i2 = 0;
                while (i2 < length) {
                    byte currentByte = byteArray[i2];
                    if ((currentByte & ((byte) 0x80)) == 0) {
                        this.fileData[charIndex] = (char) (currentByte & 255);
                    } else if ((currentByte & 224) == 192) {
                        i2++;
                        this.fileData[charIndex] = (char) (((currentByte & 31) << 6) | (byteArray[i2] & 63));
                    } else if ((currentByte & 240) == 224) {
                        int i3 = i2 + 1;
                        char c = (char) (((currentByte & 15) << 12) | ((byteArray[i3] & 63) << 6));
                        i2 = i3 + 1;
                        this.fileData[charIndex] = (char) ((byteArray[i2] & 63) | c);
                    } else if ((currentByte & 248) == 240) {
                        int i4 = i2 + 1;
                        int codePoint = ((currentByte & 7) << 18) | ((byteArray[i4] & 63) << 12);
                        int i5 = i4 + 1;
                        int i6 = (byteArray[i5] & 63) << 6;
                        i2 = i5 + 1;
                        int codePoint2 = codePoint | i6 | (byteArray[i2] & 63);
                        if (codePoint2 < 65536) {
                            this.fileData[charIndex] = (char) codePoint2;
                        } else if (codePoint2 <= 1114111) {
                            int high = ((codePoint2 - 65536) >> 10) + 55296;
                            int low = ((codePoint2 - 65536) & 1023) + 56320;
                            char[] cArr = this.fileData;
                            cArr[charIndex] = (char) high;
                            charIndex++;
                            cArr[charIndex] = (char) low;
                        } else {
                            this.fileData[charIndex] = 65503;
                        }
                    } else if ((currentByte & 255) == 248) {
                        int i7 = i2 + 1;
                        int codePoint3 = ((currentByte & 3) << 24) | ((byteArray[i7] & 63) << 18);
                        int i8 = i7 + 1;
                        int i9 = (byteArray[i8] & 63) << 12;
                        int i10 = i8 + 1;
                        int codePoint4 = codePoint3 | i9 | ((byteArray[i10] & 63) << 6);
                        i2 = i10 + 1;
                        int codePoint5 = codePoint4 | (byteArray[i2] & 63);
                        if (codePoint5 < 65536) {
                            this.fileData[charIndex] = (char) codePoint5;
                        } else if (codePoint5 <= 1114111) {
                            int high2 = ((codePoint5 - 65536) >> 10) + 55296;
                            int low2 = ((codePoint5 - 65536) & 1023) + 56320;
                            char[] cArr2 = this.fileData;
                            cArr2[charIndex] = (char) high2;
                            charIndex++;
                            cArr2[charIndex] = (char) low2;
                        } else {
                            this.fileData[charIndex] = 65503;
                        }
                    }
                    charIndex++;
                    i2++;
                }
            }
            this.currentChapterIndex = chapterIndex;
            int[] iArr = this.combinedChapterIndex[bookIndex];
            int i11 = iArr[(chapterIndex << 2) + 2];
            this.verseDataSize = i11;
            char[] cArr3 = new char[i11];
            this.verseData = cArr3;
            System.arraycopy(this.fileData, iArr[(chapterIndex << 2) + 1], cArr3, 0, i11);
        }
    }

    private void loadBookIndex(int bookIndex) throws IOException {
        int numberOfChapters = this.numberOfChapters[bookIndex];
        this.bookIndex = new int[numberOfChapters][];
        this.currentBookIndex = bookIndex;
        DataInputStream input = openStream("Bible Data/" + this.shortBookNames[bookIndex] + "/" + INDEX);
        for (int chapter = 0; chapter < numberOfChapters; chapter++) {
            int numberOfVerses = this.combinedChapterIndex[bookIndex][(chapter << 2) + 3] * 2;
            int[] chapterIndex = new int[numberOfVerses];
            this.bookIndex[chapter] = chapterIndex;
            int offset = 0;
            int verse = 0;
            while (verse < numberOfVerses) {
                int verse2 = verse + 1;
                chapterIndex[verse] = offset;
                offset += input.readUnsignedShort();
                verse = verse2 + 1;
                chapterIndex[verse2] = offset;
            }
        }
        input.close();
    }

    public String[] getBookNames() {
        return this.bookNames;
    }

    public String getBookName(int bookIndex) {
        return this.bookNames[bookIndex];
    }

    public int getNumberOfBooks() {
        return this.numberOfBooks;
    }

    public int getStartChapter(int bookIndex) {
        return this.startChapters[bookIndex];
    }

    public int getNumberOfChapters(int bookIndex) {
        return this.numberOfChapters[bookIndex];
    }

    public int getNumberOfVerses(int bookIndex, int chapterIndex) {
        return this.combinedChapterIndex[bookIndex][(chapterIndex << 2) + 3];
    }

    public int getVerseDataSize() {
        return this.verseDataSize;
    }

    private DataInputStream openStream(String path) throws FileNotFoundException {
        return new DataInputStream(new FileInputStream(new File(this.biblePath, path)));
    }
}
