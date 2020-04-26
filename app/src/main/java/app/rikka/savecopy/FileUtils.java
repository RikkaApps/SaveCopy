package app.rikka.savecopy;

public class FileUtils {

    public static String[] spiltFileName(String filename) {
        String name;
        String extension;
        int index = filename.lastIndexOf('.');
        if (index > 0) {
            name = filename.substring(0, index);
            extension = filename.substring(index);
        } else {
            name = filename;
            extension = "";
        }
        return new String[]{name, extension};
    }
}
