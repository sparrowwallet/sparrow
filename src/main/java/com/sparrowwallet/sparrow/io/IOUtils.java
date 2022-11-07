package com.sparrowwallet.sparrow.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class IOUtils {
    private static final Logger log = LoggerFactory.getLogger(IOUtils.class);

    public static FileType getFileType(File file) {
        try {
            String type = Files.probeContentType(file.toPath());
            if(type == null) {
                if(file.getName().toLowerCase(Locale.ROOT).endsWith("txn") || file.getName().toLowerCase(Locale.ROOT).endsWith("psbt")) {
                    return FileType.TEXT;
                }

                if(file.exists()) {
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line = br.readLine();
                        if(line != null) {
                            if(line.startsWith("01000000") || line.startsWith("cHNid")) {
                                return FileType.TEXT;
                            } else if(line.startsWith("{")) {
                                return FileType.JSON;
                            }
                        }
                    }
                }

                return FileType.BINARY;
            } else if (type.equals("application/json")) {
                return FileType.JSON;
            } else if (type.startsWith("text")) {
                return FileType.TEXT;
            }
        } catch (IOException e) {
            //ignore
        }

        return FileType.UNKNOWN;
    }

    /**
     * List directory contents for a resource folder. Not recursive.
     * This is basically a brute-force implementation.
     * Works for regular files, JARs and Java modules.
     *
     * @param clazz Any java class that lives in the same place as the resources you want.
     * @param path Should end with "/", but not start with one.
     * @return Just the name of each member item, not the full paths.
     * @throws URISyntaxException
     * @throws IOException
     */
    public static String[] getResourceListing(Class clazz, String path) throws URISyntaxException, IOException {
        URL dirURL = clazz.getClassLoader().getResource(path);
        if(dirURL != null && dirURL.getProtocol().equals("file")) {
            /* A file path: easy enough */
            return new File(dirURL.toURI()).list();
        }

        if(dirURL == null) {
            /*
             * In case of a jar file, we can't actually find a directory.
             * Have to assume the same jar as clazz.
             */
            String me = clazz.getName().replace(".", "/")+".class";
            dirURL = clazz.getClassLoader().getResource(me);
        }

        if(dirURL.getProtocol().equals("jar")) {
            /* A JAR path */
            String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!")); //strip out only the JAR file
            JarFile jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
            Enumeration<JarEntry> entries = jar.entries(); //gives ALL entries in jar
            Set<String> result = new HashSet<String>(); //avoid duplicates in case it is a subdirectory
            while(entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if(name.startsWith(path)) { //filter according to the path
                    String entry = name.substring(path.length());
                    int checkSubdir = entry.indexOf("/");
                    if (checkSubdir >= 0) {
                        // if it is a subdirectory, we just return the directory name
                        entry = entry.substring(0, checkSubdir);
                    }
                    if(!entry.isEmpty()) {
                        result.add(entry);
                    }
                }
            }

            return result.toArray(new String[result.size()]);
        }

        if(dirURL.getProtocol().equals("jrt")) {
            java.nio.file.FileSystem jrtFs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
            Path resourcePath = jrtFs.getPath("modules/com.sparrowwallet.sparrow", path);
            return Files.list(resourcePath).map(filePath -> filePath.getFileName().toString()).toArray(String[]::new);
        }

        throw new UnsupportedOperationException("Cannot list files for URL " + dirURL);
    }

    public static boolean deleteDirectory(File directory) {
        try {
            Files.walk(directory.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch(IOException e) {
            return false;
        }

        return true;
    }

    public static boolean secureDelete(File file) {
        if(file.exists()) {
            long length = file.length();
            SecureRandom random = new SecureRandom();
            byte[] data = new byte[1024*1024];
            random.nextBytes(data);
            try(RandomAccessFile raf = new RandomAccessFile(file, "rws")) {
                raf.seek(0);
                raf.getFilePointer();
                int pos = 0;
                while(pos < length) {
                    raf.write(data);
                    pos += data.length;
                }
            } catch(IOException e) {
                log.warn("Error overwriting file for deletion: " + file.getName(), e);
            }

            return file.delete();
        }

        return false;
    }
}
