package damjay.floating.projects.bible;

import android.content.Context;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import damjay.floating.projects.utils.FileUtils;
import damjay.floating.projects.utils.ZipUtils;

public class CombinedChapterBibleSource {
    public static final String BIBLE_DATA = "Bible Data";
    public static final String INDEX = "Index";
        
    private final Context context;
    
    // Current chapter loaded
    private int currentBookIndex = -1;
    private int currentFileIndex = -1;
    private int currentChapterIndex = -1;

    // Bible index
    private int numberOfBooks;
    private String[] bookNames;

    /** Short book names are for file names as they are ensured to be 7-bit ASCII. **/
    private String[] shortBookNames;

    // The chapter number that the book starts with, usually 1 but may be
    // a larger chapter if the book has been split
    private int[] startChapters;
    private int[] numberOfChapters;

    // Contains the file number and number of verses for each chapter.
    // Second index is numberOfChapters * 4 with the first entry being
    // the file number, the second entry being the offset into the verse
    // data where the chapter starts, the third entry being the number of bytes
    // of the verse data and the third entry being for the
    // number of verses.
    private int[][] combinedChapterIndex;

    // Book index
    private int[][] bookIndex;

    private char[] fileData;

    // Current chapter data
    private char[] verseData;
    
    private int verseDataSize;
    
    private File biblePath;

    public CombinedChapterBibleSource(Context context, File biblePath) throws IOException {
        this.context = context;
        
        if (!checkCreated(biblePath)) return;
        DataInputStream input = openStream(BIBLE_DATA + "/" + INDEX);
        
        // Read in the number of books
        numberOfBooks = input.read();

        bookNames = new String[numberOfBooks];
        shortBookNames = new String[numberOfBooks];
        startChapters = new int[numberOfBooks];
        numberOfChapters = new int[numberOfBooks];
        combinedChapterIndex = new int[numberOfBooks][];

        for (int bookIndex = 0; bookIndex < numberOfBooks; bookIndex++) {
            // Read in the name of the book
            bookNames[bookIndex] = input.readUTF();

            // Read in the short book name
            shortBookNames[bookIndex] = input.readUTF();
            startChapters[bookIndex] = input.readUnsignedShort();

            // Read in the number of chapters in this book
            int numberOfChapters = input.readUnsignedShort();
            this.numberOfChapters[bookIndex] = numberOfChapters;
            combinedChapterIndex[bookIndex] = new int[numberOfChapters << 2];

            int previousFileNumber = 0;
            int verseDataOffset = 0;

            // Read in the file number, verse offset, and number of verses for each chapter
            for (int chapterIndex = 0; chapterIndex < numberOfChapters; chapterIndex++) {
                int fileNumber = input.read();
                int allVersesLength = input.readInt();

                combinedChapterIndex[bookIndex][chapterIndex << 2] = fileNumber;

                if (fileNumber != previousFileNumber) {
                    verseDataOffset = 0;
                    previousFileNumber = fileNumber;
                }

                combinedChapterIndex[bookIndex][(chapterIndex << 2) + 1] = verseDataOffset;
                combinedChapterIndex[bookIndex][(chapterIndex << 2) + 2] = allVersesLength;
                combinedChapterIndex[bookIndex][(chapterIndex << 2) + 3] = input.read();

                /* System.err.println(bookIndex + " chapter " + chapterIndex + ": " +
                                   verseDataOffset + ", " + allVersesLength + ", " +
                                   combinedChapterIndex[bookIndex][(chapterIndex << 2) + 3]);*/

                verseDataOffset += allVersesLength;
            }
        }

        input.close();
    }
    
    private boolean checkCreated(File biblePath) {
        this.biblePath = biblePath;
        if (!new File(biblePath, BIBLE_DATA + "/" + INDEX).exists()) {
            File zipFile = new File(biblePath, BIBLE_DATA + ".zip");
            try {
                if (!FileUtils.copyStream(context.getAssets().open(BIBLE_DATA + ".zip"), new FileOutputStream(zipFile))) return false;
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
            
            return ZipUtils.extractZip(zipFile, biblePath);
        }
        return true;
    }

    public char[] getChapter(int bookIndex, int chapterIndex) throws IOException {
        // Load the chapter if it isn't loaded
        loadChapter(bookIndex, chapterIndex);

        return verseData;
    }

    public int[] getChapterIndex(int bookIndex, int chapterIndex) throws IOException {
        // Load the chapter if it isn't loaded
        loadChapter(bookIndex, chapterIndex);

        return this.bookIndex[chapterIndex];
    }

    private void loadChapter(int bookIndex, int chapterIndex) throws IOException {
        // If the chapter isn't loaded then load it
        if (currentBookIndex != bookIndex || currentChapterIndex != chapterIndex) {
            // If the book or file index have changed then load the new file
            if (currentBookIndex != bookIndex || combinedChapterIndex[bookIndex][chapterIndex << 2] != currentFileIndex) {
                // If the bible canvas is displayed then indicate that we are loading a chapter
//              if (goBible != null && goBible.display.getCurrent() == goBible.bibleCanvas) {
                    // Notify the application that we'll be spending some time loading
//                  goBible.bibleCanvas.enterLoadingMode();
//              }

                // If the book is different then we need to load its index
                if (currentBookIndex != bookIndex) {
                    loadBookIndex(bookIndex);
                }

                currentFileIndex = combinedChapterIndex[bookIndex][chapterIndex << 2];

                // Load the chapter as it will be different if either the chapter or book changed
                //start = System.currentTimeMillis();

                DataInputStream input = openStream(BIBLE_DATA + "/" + shortBookNames[bookIndex] + "/" + shortBookNames[bookIndex] + " " + currentFileIndex);

                int length = input.readInt();

                byte[] byteArray = new byte[length];

                input.readFully(byteArray, 0, length);

                input.close();

                //fileData = new String(byteArray, "UTF-8").toCharArray();

                // Do our own UTF-8 conversion
                fileData = new char[length];

                int charIndex = 0;

                for (int i = 0; i < length; i++) {
                    byte currentByte = byteArray[i];

                    // If ASCII character then simply copy in
                    if ((currentByte & 0x80) == 0) {
                        fileData[charIndex] = (char) (currentByte & 0xff);
                    } else if ((currentByte & 0xe0) == 0xc0) {
                        fileData[charIndex] = (char) (((currentByte & 0x1f) << 6) | (byteArray[++i] & 0x3f));
                    } else if ((currentByte & 0xf0) == 0xe0) {
                        char c = (char) (((currentByte & 0x0f) << 12) | ((byteArray[++i] & 0x3f) << 6));
                        fileData[charIndex] = (char) (c | (byteArray[++i] & 0x3f));
                    } else if ((currentByte & 0xf8) == 0xf0) {
                        // The old code correctly mapped from UTF8 to UTF32, but we can't
                        // process 32 bit characters. It needs to be broken up into a surrogate pair.
                        // That's what the new code below is doing.
                        int codePoint = (((currentByte & 0x07) << 18) | ((byteArray[++i] & 0x3f) << 12));
                        codePoint |= ((byteArray[++i] & 0x3f) << 6) | (byteArray[++i] & 0x3f);
                        // if it's a valid 16 bit code point, just put it out
                        if (codePoint < 0x10000) {
                            fileData[charIndex] = (char) codePoint;
                        } else if (codePoint <= 0x10FFFF) {
                            int high = (((codePoint - 0x00010000) >> 10) + 0xd800);
                            int low = (((codePoint - 0x00010000) & 0x3ff) + 0xdc00);
                            fileData[charIndex] = (char) high;
                            fileData[++charIndex] = (char) low;
                        } else  // >= 0x10FFFF, put out the standard REPLACEMENT CHARACTER
                            fileData[charIndex] = '\uFFDF';
                    } else if ((currentByte & 0xff) == 0xf8) {
                        int codePoint = (((currentByte & 0x03) << 24) | ((byteArray[++i] & 0x3f) << 18));
                        codePoint |= ((byteArray[++i] & 0x3f) << 12) | ((byteArray[++i] & 0x3f) << 6);
                        codePoint |= (byteArray[++i] & 0x3f);
                        // if it's a valid 16 bit code point, just put it out
                        if (codePoint < 0x10000) {
                            fileData[charIndex] = (char) codePoint;
                        } else if (codePoint <= 0x10FFFF) {
                            int high = (((codePoint - 0x00010000) >> 10) + 0xd800);
                            int low = (((codePoint - 0x00010000) & 0x3ff) + 0xdc00);
                            fileData[charIndex] = (char) high;
                            fileData[++charIndex] = (char) low;
                        } else  // >= 0x10FFFF, put out the standard REPLACEMENT CHARACTER
                            fileData[charIndex] = '\uFFDF';
                    }

                    charIndex++;
                }

            }

            currentChapterIndex = chapterIndex;

            // Load the chapter's verse data from the fileData
            verseDataSize = combinedChapterIndex[bookIndex][(chapterIndex << 2) + 2];
            verseData = new char[verseDataSize];

            // Copy verse data out of fileData
            System.arraycopy(fileData, combinedChapterIndex[bookIndex][(chapterIndex << 2) + 1], verseData, 0, verseDataSize);
        }
    }

    private void loadBookIndex(int bookIndex) throws IOException {
        int numberOfChapters = this.numberOfChapters[bookIndex];
        this.bookIndex = new int[numberOfChapters][];

        currentBookIndex = bookIndex;

        DataInputStream input = openStream(BIBLE_DATA + "/" + shortBookNames[bookIndex] + "/" + INDEX);

        // Read each verse length in for each chapter
        for (int chapter = 0; chapter < numberOfChapters; chapter++) {
            // There are two verse entries (offset, length) for every verse
            int numberOfVerses = 2 * combinedChapterIndex[bookIndex][(chapter << 2) + 3];
            int[] chapterIndex = new int[numberOfVerses];
            this.bookIndex[chapter] = chapterIndex;

            int offset = 0;

            // Read in each verse length
            for (int verse = 0; verse < numberOfVerses;) {
                chapterIndex[verse++] = offset;
                offset += input.readUnsignedShort();
                chapterIndex[verse++] = offset;
            }
        }

        input.close();
    }

    public String[] getBookNames() {
        return bookNames;
    }

    public String getBookName(int bookIndex) {
        return bookNames[bookIndex];
    }

    public int getNumberOfBooks() {
        return numberOfBooks;
    }

    public int getStartChapter(int bookIndex) {
        return startChapters[bookIndex];
    }

    public int getNumberOfChapters(int bookIndex) {
        return numberOfChapters[bookIndex];
    }

    public int getNumberOfVerses(int bookIndex, int chapterIndex) {
        return combinedChapterIndex[bookIndex][(chapterIndex << 2) + 3];
    }

    public int getVerseDataSize() {
        return verseDataSize;
    }
    
    private DataInputStream openStream(String path) throws FileNotFoundException {
        return new DataInputStream(new FileInputStream(new File(biblePath, path)));
    }

}
