package com.weaver.ecode.gradle.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.bundling.Jar;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @USER: solelyr
 * @DATE: 2026/09/22 11:02
 * @DESCRIPTION: 为二开 JAR 写入资源与源码清单并生成部署 ZIP
 */
public class BuildArchPlugin implements Plugin<Project> {

    private void readResourcePathList(String rootPath, Set<String> pathList) {
        File dir = new File(rootPath);
        if (Objects.isNull(dir.listFiles())) return;
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isDirectory()) {
                pathList.add(file.getPath());
                readResourcePathList(file.getPath(), pathList);
            } else {
                pathList.add(file.getPath());
            }
        }
    }

    private void addResourceListToJar(String jarLibPath, Set<String> resPathList, String fileName) {
        StringBuilder text = new StringBuilder("[\n");
        int idx = 1;
        for (String s : resPathList) {
            if (idx++ == resPathList.size()) {
                text.append("\t\"").append(s).append("\"\n");
            } else {
                text.append("\t\"").append(s).append("\",\n");
            }
        }
        text.append("]");
        File zipFile = new File(jarLibPath);
        String tmpFilePath = UUID.randomUUID() + ".zip";
        File tmpZipFile = new File(zipFile.getParentFile(), tmpFilePath);
        try(ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile.toPath()));
            ZipOutputStream tmpZipOut = new ZipOutputStream(Files.newOutputStream(tmpZipFile.toPath()));
            InputStream fis = new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8));) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                tmpZipOut.putNextEntry(new ZipEntry(entry.getName()));
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zin.read(buffer)) > 0) {
                    tmpZipOut.write(buffer, 0, len);
                }
                entry = zin.getNextEntry();
            }
            ZipEntry zipEntry = new ZipEntry("META-INF/" + fileName);
            tmpZipOut.putNextEntry(zipEntry);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                tmpZipOut.write(buffer, 0, len);
            }
        } catch (Exception e) {
            throw new GradleException("Failed to add resource metadata to " + jarLibPath, e);
        }
        try {
            Files.move(tmpZipFile.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new GradleException("Failed to replace archive " + jarLibPath, e);
        }
    }

    private Set<String> calcAbsPathList(String rooPath, Set<String> pathList) {
        Set<String> absPaths = new HashSet<>();
        for (String path : pathList) {
            Path basePath = Paths.get(rooPath).toAbsolutePath().normalize();
            Path filePath = new File(path).toPath();
            Path relativize = basePath.relativize(filePath);
            absPaths.add(relativize.toString().replace("\\", "/"));
        }
        return absPaths;
    }

    private void addPackagedSources(File jarFile, Set<String> srcPaths) {
        try (ZipFile archive = new ZipFile(jarFile)) {
            archive.stream()
                    .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".java"))
                    .map(ZipEntry::getName)
                    .filter(name -> !name.startsWith("META-INF/"))
                    .forEach(name -> {
                        srcPaths.add(name);
                        for (int slash = name.lastIndexOf('/'); slash >= 0; slash = name.lastIndexOf('/')) {
                            name = name.substring(0, slash);
                            srcPaths.add(name);
                        }
                    });
        } catch (IOException e) {
            throw new GradleException("Failed to list Java sources in " + jarFile, e);
        }
    }

    @Override
    public void apply(Project project) {
        // 扫描 项目下的resources 文件内容
        List<String> pathList = new ArrayList<>();
//        scanFileOrDir()
        project.getTasks().forEach(t -> {
            if (t.getName().equalsIgnoreCase("jar")) { // 打包task添加后置处理逻辑
                t.doLast(l -> {
                    String jarName = ((Jar) l).getArchiveBaseName().get();
                    String version = null;
                    try {
                        version = ((Jar) l).getArchiveVersion().get();
                    } catch (Exception ignored) {}
                    String libJarPath = project.getBuildDir().getPath() + "/libs/" + jarName + (Objects.nonNull(version) ? "-" + version : "")  + ".jar";
                    File jarFile = new File(libJarPath);
                    if (jarFile.exists()) {
                        // 当前目录下生成build.zip
                        // 获取对应模块的resources 目录清单
                        File moduleProjectFile = l.getProject().getProjectDir();
                        Set<String> resourcesList = new HashSet<>();
                        Set<String> srcList = new HashSet<>();
                        readResourcePathList(moduleProjectFile.getPath() + "/src/main/resources", resourcesList);
                        readResourcePathList(moduleProjectFile.getPath() + "/src/main/java", srcList);
                        Set<String> resAbsPath = calcAbsPathList(moduleProjectFile.getPath() + "/src/main/resources", resourcesList);
                        Set<String> srcAbsPath = calcAbsPathList(moduleProjectFile.getPath() + "/src/main/java", srcList);
                        addPackagedSources(jarFile, srcAbsPath);
                        addResourceListToJar(libJarPath, resAbsPath, "res.list");
                        addResourceListToJar(libJarPath, srcAbsPath, "src.list");
                        createBuildZip(libJarPath);
                    } else {
                        project.getLogger().warn("Archive jar does not exist: {}", libJarPath);
                    }
                });
            }
        });
    }

    private void createBuildZip(String jarPath) {
        try {
            File jarFile = new File(jarPath);
            String zipFileName = jarFile.getParent() + "/build.zip";
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFileName))) {
                addFileToZip(new File(jarPath), "", zipOutputStream);
            }
        } catch (Exception e) {
            throw new GradleException("Failed to create build.zip for " + jarPath, e);
        }
    }

    private void addFileToZip(File file, String parentFolder, ZipOutputStream zipOutputStream) throws Exception {
        String entryName = parentFolder.isEmpty()
                ? file.getName()
                : parentFolder + "/" + file.getName();
        ZipEntry zipEntry = new ZipEntry(entryName);
        zipOutputStream.putNextEntry(zipEntry);

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fileInputStream.read(buffer)) > 0) {
                zipOutputStream.write(buffer, 0, len);
            }
        }
        zipOutputStream.closeEntry();
    }

}
