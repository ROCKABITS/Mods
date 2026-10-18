package pt.realmcasino.data;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** File contents are forced everywhere. Java cannot open a directory handle on Windows. */
public final class DurableFiles {
    private DurableFiles() {}
    public static void forceFile(Path path) throws IOException {
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.force(true);}
    }
    public static void forceDirectory(Path directory) throws IOException {
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))return;
        try(FileChannel channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
}
