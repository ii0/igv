package org.broad.igv.ui.util.download;


import org.broad.igv.util.HttpUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;


// This class downloads a file from a URL.
public class Downloader implements Runnable {

    // Max size of download buffer.
    private static final int MAX_BUFFER_SIZE = 1000000;    // Max buffer size


    private URL url; // download URL
    private File localFile;
    private String tmpName;
    private int downloaded; // number of bytes downloaded
    private boolean canceled;
    private final ProgressMonitor monitor;

    // Constructor for Download.
    public Downloader(URL url, File file, ProgressMonitor monitor) {
        this.url = url;
        this.localFile = file;
        this.tmpName = file + ".download";
        this.monitor = monitor;
        this.canceled = false;
        this.downloaded = 0;
    }


    // Download file.
    public void run() {

        RandomAccessFile file = null;
        InputStream stream = null;

        try {

            long contentLength = HttpUtils.getInstance().getContentLength(url);


            // Check for valid content length.
            if (contentLength < 1) {
                // error();
            }

            Map<String, String> requestProperties = new HashMap<>();
            requestProperties.put("Range", "bytes=" + downloaded + "-" + downloaded + MAX_BUFFER_SIZE);

            // Open connection to URL.
            HttpURLConnection connection = HttpUtils.getInstance().openConnection(url, null);


            // Connect to server.
            connection.connect();

            // Make sure response code is in the 200 range.
            if (connection.getResponseCode() / 100 != 2) {
                // error();
            }


            // Open file and seek to the end of it.
            file = new RandomAccessFile(tmpName, "rw");
            file.seek(downloaded);

            stream = connection.getInputStream();
            while (downloaded < contentLength) {

                // Size buffer according to how much of the file is left to download.
                byte buffer[];
                if (contentLength - downloaded > MAX_BUFFER_SIZE) {
                    buffer = new byte[MAX_BUFFER_SIZE];
                } else {
                    buffer = new byte[(int) (contentLength - downloaded)];
                }

                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                    break;

                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;

                int percent = (int) (100.0 * downloaded / contentLength);

                if (monitor != null) {

                    if (monitor.isCanceled()) {
                        this.canceled = true;
                        break;
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            monitor.setProgress(percent);
                            monitor.setNote("" + (downloaded / 1000) + " of " + (contentLength / 1000) + " kb");
                        });
                    }
                }
            }

            if(canceled) {
                (new File(tmpName)).delete();
            }
            else {
                (new File(tmpName)).renameTo(localFile);
            }


        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            if (monitor != null) {
                monitor.close();
            }

            // Close file.
            if (file != null) {
                try {
                    file.close();
                } catch (Exception e) {
                }
            }

            // Close connection to server.
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception e) {
                }
            }
        }

    }

    // Convenience method
    public static void download(URL url, File localFile, Component frame) throws MalformedURLException {

        String message = "Downloading " + url.toString();
        int min = 0;
        int max = 100;

        final javax.swing.ProgressMonitor monitor = new javax.swing.ProgressMonitor(frame, message, "", min, max);
        monitor.setMillisToDecideToPopup(100);

        (new Downloader(url, localFile, monitor)).run();

    }

    public static void main(String[] args) throws MalformedURLException {

        URL url = new URL(args[0]);
        String localFile = args[1];
        JComponent frame = null;

        String message = "Downloading " + url.toString();
        int min = 0;
        int max = 100;

        final javax.swing.ProgressMonitor monitor = new javax.swing.ProgressMonitor(frame, message, "", min, max);
        monitor.setMillisToDecideToPopup(100);


        Downloader dl = new Downloader(url, new File(localFile), monitor);

        (new Thread(dl)).start();


    }

}
