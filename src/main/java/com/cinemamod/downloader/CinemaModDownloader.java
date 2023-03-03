package com.cinemamod.downloader;

import com.cinemamod.fabric.CinemaMod;
import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;

public class CinemaModDownloader extends Thread {
    private Properties versions;
    private final JFrame frame;
    private final JLabel taskLabel;
    private final JLabel jcefVersionLabel;
    private final JLabel fileLabel;
    private final JProgressBar progressBar;

    public CinemaModDownloader(JFrame frame, JLabel taskLabel, JLabel jcefVersionLabel, JLabel fileLabel, JProgressBar progressBar) {
        this.frame = frame;
        this.taskLabel = taskLabel;
        this.jcefVersionLabel = jcefVersionLabel;
        this.fileLabel = fileLabel;
        this.progressBar = progressBar;
    }

    private Map<String, String> fetchFileManifest(String url) throws IOException {
        Map<String, String> manifest = new HashMap<>();

        try (
                InputStream inputStream = new URL(url).openStream();
                Scanner scanner = new Scanner(inputStream);
        ) {
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String sha1hash = line.split(" ")[0];
                String filePath = line.split(" ")[2].substring(1);
                manifest.put(sha1hash, filePath);
            }
        }

        return manifest;
    }

    private void fetchVersions() throws IOException {
        this.versions = new Properties();
        URL versionsURL = new URL("https://cinemamod-libraries.ewr1.vultrobjects.com/versions.txt");

        try (
                InputStream inputStream = versionsURL.openStream();
                Scanner scanner = new Scanner(inputStream);
        ) {
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();

                try {
                    String library = line.split(" ")[0];
                    String version = line.split(" ")[1];
                    this.versions.put(library, version);
                } catch (IndexOutOfBoundsException var9) {
                }
            }
        }
    }

    private boolean ensureLibFile(String sha1hash, String relPath) {
        Path librariesPath = Paths.get(System.getProperty("cinemamod.libraries.path"));
        File libFile = new File(librariesPath + relPath);
        boolean result = false;
        if (libFile.exists()) {
            try {
                String onDiskHash = Util.sha1Hash(libFile);
                if (sha1hash.equals(onDiskHash)) {
                    result = true;
                } else {
                    System.out.println(libFile + " hash mismatch, will update");
                }
            } catch (NoSuchAlgorithmException | IOException var7) {
                var7.printStackTrace();
            }
        }

        return result;
    }

    private void downloadLibFile(String remotePath, String relPath) throws IOException {
        Path librariesPath = Paths.get(System.getProperty("cinemamod.libraries.path"));
        this.fileLabel.setText("Скачивание " + remotePath);
        System.out.println(this.fileLabel.getText());
        File localFile = new File(librariesPath + relPath);
        FileUtils.copyURLToFile(new URL(remotePath), localFile);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")
                && (localFile.toString().contains("chrome-sandbox") || localFile.toString().contains("jcef_helper"))) {
            Util.makeExecNix(localFile);
        }
    }

    private void patchLibFile(String relPath) throws CompressorException, IOException, InvalidHeaderException {
        Path librariesPath = Paths.get(System.getProperty("cinemamod.libraries.path"));
        this.fileLabel.setText("Патчинг " + relPath);
        System.out.println(this.fileLabel.getText());
        File libFile = new File(librariesPath + relPath);
        File patchFile = new File(libFile + ".diff");
        FileUI.patch(libFile, libFile, patchFile);
    }

    private void ensureJcef(String cefBranch, String platform) {
        String jcefManifestUrlString = Resource.getJcefUrl(cefBranch, platform) + "/manifest.txt";
        String jcefPatchedManifestUrlString = Resource.getJcefPatchesUrl(cefBranch, platform) + "/patched-manifest.txt";
        String jcefPatchesManifestUrlString = Resource.getJcefPatchesUrl(cefBranch, platform) + "/manifest.txt";
        Map<String, String> jcefManifest = new HashMap<>();
        Map<String, String> jcefPatchedManifest = new HashMap<>();
        Map<String, String> patchesManifest = new HashMap<>();
        boolean usingCodecs = true;

        try {
            jcefManifest = this.fetchFileManifest(jcefManifestUrlString);
        } catch (IOException var26) {
            CinemaMod.LOGGER.warn("Unable to download JCEF manifest");
            var26.printStackTrace();
        }

        try {
            jcefPatchedManifest = this.fetchFileManifest(jcefPatchedManifestUrlString);
        } catch (IOException var25) {
            CinemaMod.LOGGER.warn("Unable to download patched JCEF manifest");
            var25.printStackTrace();
            usingCodecs = false;
        }

        try {
            patchesManifest = this.fetchFileManifest(jcefPatchesManifestUrlString);
        } catch (IOException var24) {
            CinemaMod.LOGGER.warn("Unable to JCEF patches manifest");
            var24.printStackTrace();
            usingCodecs = false;
        }

        int fileCount = 0;

        for(Entry<String, String> entry : usingCodecs ? jcefPatchedManifest.entrySet() : jcefManifest.entrySet()) {
            ++fileCount;
            int value = (int)((double)fileCount / (double)jcefManifest.size() * 100.0);
            this.progressBar.setValue(value);
            String sha1hash = entry.getKey();
            String filePath = entry.getValue();
            this.fileLabel.setText("Найден " + filePath.substring(1));
            if (!this.ensureLibFile(sha1hash, filePath)) {
                String remotePath = Resource.getJcefUrl(cefBranch, platform) + filePath;
                this.fileLabel.setText(remotePath);

                try {
                    this.downloadLibFile(remotePath, filePath);
                } catch (IOException var23) {
                    var23.printStackTrace();
                }

                if (usingCodecs) {
                    for(String patchFileName : patchesManifest.values()) {
                        if (patchFileName.startsWith(filePath)) {
                            String patchRemotePath = Resource.getJcefPatchesUrl(cefBranch, platform) + patchFileName;

                            try {
                                this.downloadLibFile(patchRemotePath, patchFileName);
                            } catch (IOException var22) {
                                var22.printStackTrace();
                            }

                            try {
                                this.patchLibFile(filePath);
                            } catch (InvalidHeaderException | IOException | CompressorException var21) {
                                var21.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        this.taskLabel.setText("Получение информации о версии мода...");
        /*
        try {
            Thread.sleep(2000L); // 2000
        } catch (InterruptedException var5) {
            var5.printStackTrace();
        }*/

        try {
            this.fetchVersions();
        } catch (IOException var6) {
            CinemaMod.LOGGER.warn(this.taskLabel.getText());

            for(int i = 5; i > 0; --i) {
                this.taskLabel.setText("Unable to fetch mod version info (is there internet access?). Continuing in " + i);

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException var4) {
                    var4.printStackTrace();
                }
            }
        }

        CinemaMod.LOGGER.info("Версии библиотек CinemaMod " + this.versions.toString());
        this.jcefVersionLabel.setText("Текущая ветка CEF CinemaMod: " + this.versions.getProperty("jcef"));
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String platform;
        if (os.contains("win")) {
            platform = "win64";
        } else if (os.contains("mac")) {
            platform = "mac64";
        } else if (os.contains("linux")) {
            platform = "linux64";
        } else {
            platform = "unknown";
        }

        if (this.versions.get("jcef") != null) {
            this.taskLabel.setText("Проверка файлов библиотек... Это может занять некоторое время...");
            this.ensureJcef(this.versions.getProperty("jcef"), platform);
        }

        this.frame.setVisible(false);
        this.frame.dispose();
    }

    public static void main(String[] args) {
        if (System.getProperty("cinemamod.libraries.path") == null) {
            System.out.println("Not running inside Minecraft");
        } else {
            System.setProperty("java.awt.headless", "false");

            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (UnsupportedLookAndFeelException | IllegalAccessException | InstantiationException | ClassNotFoundException var15) {
                var15.printStackTrace();
            }

            JFrame frame = new JFrame();
            frame.setSize(600, 300);
            frame.setDefaultCloseOperation(3);
            frame.setTitle("CinemaMod Downloader");
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new GridLayout(4, 1));
            JPanel iconPanel = new JPanel();
            iconPanel.setLayout(new FlowLayout());

            try {
                BufferedImage image = ImageIO.read(CinemaModDownloader.class.getResourceAsStream("/assets/cinemamod/icon.png"));
                Image scaledImage = image.getScaledInstance(80, 63, 4);
                iconPanel.add(new JLabel(new ImageIcon(scaledImage)));
            } catch (IOException var14) {
                var14.printStackTrace();
            }

            mainPanel.add(iconPanel);
            JPanel taskPanel = new JPanel();
            taskPanel.setLayout(new FlowLayout());
            JLabel taskLabel = new JLabel("Подготовка...");
            taskPanel.add(taskLabel);
            mainPanel.add(taskPanel);
            JPanel progressPanel = new JPanel();
            progressPanel.setLayout(new FlowLayout());
            JLabel fileLabel = new JLabel();
            progressPanel.add(fileLabel);
            JProgressBar progressBar = new JProgressBar(1, 100);
            progressBar.setValue(0);
            progressPanel.add(progressBar);
            mainPanel.add(progressPanel);
            JPanel versionPanel = new JPanel();
            versionPanel.setLayout(new FlowLayout());
            JLabel jcefVersionLabel = new JLabel();
            versionPanel.add(jcefVersionLabel);
            mainPanel.add(versionPanel);
            frame.add(mainPanel);
            frame.setVisible(true);
            CinemaModDownloader downloader = new CinemaModDownloader(frame, taskLabel, jcefVersionLabel, fileLabel, progressBar);
            downloader.start();

            try {
                downloader.join();
            } catch (InterruptedException var13) {
                var13.printStackTrace();
            }

            System.setProperty("java.awt.headless", "true");
        }
    }
}
