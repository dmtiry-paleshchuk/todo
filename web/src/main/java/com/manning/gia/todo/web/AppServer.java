package com.manning.gia.todo.web;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Minimal embedded Tomcat launcher that extracts the bundled webapp resources
 * from the jar (placed under classpath folder "webapp/") into a temp directory
 * and starts Tomcat against that directory so web.xml, JSPs, and static assets
 * work without an external container.
 */
public class AppServer {
    public static void main(String[] args) throws Exception {
        int port = getPort();
        String contextPath = "/todo";

        Path webappDir = extractWebappToTemp();

        Tomcat tomcat = new Tomcat();
        tomcat.setPort(port);
        tomcat.getConnector(); // trigger creation

        Context ctx = tomcat.addWebapp(contextPath, webappDir.toString());

        tomcat.start();
        tomcat.getServer().await();
    }

    private static int getPort() {
        String env = System.getenv("PORT");
        if (env != null) {
            try {
                return Integer.parseInt(env);
            } catch (NumberFormatException ignored) {
            }
        }
        return 8080;
    }

    private static Path extractWebappToTemp() throws IOException, URISyntaxException {
        Path tempDir = Files.createTempDirectory("webapp-");
        URL selfUrl = AppServer.class.getProtectionDomain().getCodeSource().getLocation();

        // Running from an uber-jar: iterate jar entries and copy those under "webapp/"
        try (JarFile jar = new JarFile(selfUrl.getPath())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("webapp/"))
                    continue;
                String relative = name.substring("webapp/".length());
                if (relative.isEmpty())
                    continue;
                Path outPath = tempDir.resolve(relative);
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            // Fallback: running from classes/resources (IDE). Copy via classloader
            // resources.
            copyFromClasspathFolder("webapp", tempDir);
        }
        return tempDir;
    }

    private static void copyFromClasspathFolder(String folder, Path target) throws IOException {
        // Best-effort: copy known paths used by this project
        String[] roots = new String[] {
                folder + "/WEB-INF/web.xml",
                folder + "/index.jsp",
                folder + "/jsp/index.jsp",
                folder + "/css/base.css"
        };
        ClassLoader cl = AppServer.class.getClassLoader();
        for (String r : roots) {
            URL url = cl.getResource(r);
            if (url == null)
                continue;
            Path out = target.resolve(r.substring(folder.length() + 1));
            Files.createDirectories(out.getParent());
            try (InputStream is = url.openStream()) {
                Files.copy(is, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
