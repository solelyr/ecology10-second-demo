package com.solelyr.gradle.plugin;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import groovy.json.JsonOutput;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @USER: solelyr
 * @DATE: 2026/11/15 07:08
 * @DESCRIPTION: 读取 Java 类型的 Javadoc 并生成 E10 文件清单
 */
public class SolelyrFileManifestPlugin implements Plugin<Project> {
    private static final String MANIFEST_NAME = "file-manifest.json";
    private static final Pattern USER_TAG = Pattern.compile("(?m)^\\h*@USER\\h*[:：]\\h*(.*)$");
    private static final Pattern DESCRIPTION_TAG = Pattern.compile(
            "(?ms)^\\h*@DESCRIPTION\\h*[:：]\\h*(.*?)(?=^\\h*@|\\z)");

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> configureManifest(project));
    }

    private void configureManifest(Project project) {
        FileTree sources = project.getExtensions().getByType(SourceSetContainer.class)
                .getByName("main").getJava().matching(patterns -> patterns.exclude("**/._*"));
        TaskProvider<Jar> jar = project.getTasks().named("jar", Jar.class);
        Provider<File> embedded = project.getLayout().getBuildDirectory()
                .file("generated/fileManifest/META-INF/" + MANIFEST_NAME).map(file -> file.getAsFile());
        Provider<File> external = project.provider(() -> {
            File archive = jar.get().getArchiveFile().get().getAsFile();
            String baseName = archive.getName().replaceFirst("\\.jar$", "");
            return new File(archive.getParentFile(), baseName + "-" + MANIFEST_NAME);
        });

        TaskProvider<Task> generate = project.getTasks().register("generateFileManifest", task -> {
            task.setGroup("build");
            task.setDescription("从 Java Javadoc 生成 E10 文件清单");
            task.getInputs().files(sources).withPathSensitivity(PathSensitivity.RELATIVE);
            task.getInputs().property("projectName", project.getName());
            task.getInputs().property("projectDescription", project.provider(project::getDescription));
            task.getOutputs().files(embedded, external);
            task.doLast(ignored -> writeManifest(project, sources, embedded.get(), external.get()));
        });

        jar.configure(task -> {
            task.dependsOn(generate);
            task.from(embedded, copy -> copy.into("META-INF"));
        });
    }

    private void writeManifest(Project project, FileTree sources, File embedded, File external) {
        try {
            List<Map<String, String>> entries = readEntries(sources);
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("name", project.getName());
            manifest.put("description", project.getDescription());
            manifest.put("fileList", entries);

            byte[] json = (JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            for (File file : new File[]{embedded, external}) {
                Files.createDirectories(file.toPath().getParent());
                Files.write(file.toPath(), json);
            }
            project.getLogger().lifecycle("已生成 E10 文件清单，共 {} 项：{}", entries.size(), external);
        } catch (IOException e) {
            throw new GradleException("生成 E10 文件清单失败", e);
        }
    }

    private List<Map<String, String>> readEntries(FileTree sources) throws IOException {
        List<Map<String, String>> entries = new ArrayList<>();
        if (sources.isEmpty()) {
            return entries;
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new GradleException("生成 E10 文件清单需要使用 JDK 运行 Gradle");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null,
                StandardCharsets.UTF_8)) {
            JavacTask parser = (JavacTask) compiler.getTask(null, files, diagnostics,
                    Collections.singletonList("-proc:none"), null, files.getJavaFileObjectsFromFiles(sources));
            Iterable<? extends CompilationUnitTree> units = parser.parse();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    throw new GradleException("解析文件清单源码失败：" + diagnostic);
                }
            }

            Trees trees = Trees.instance(parser);
            List<String> missing = new ArrayList<>();
            for (CompilationUnitTree unit : units) {
                String fileName = new File(unit.getSourceFile().toUri()).getName();
                String className = fileName.substring(0, fileName.length() - ".java".length());
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree
                            && ((ClassTree) declaration).getSimpleName().contentEquals(className)) {
                        String comment = trees.getDocComment(new TreePath(new TreePath(unit), declaration));
                        addEntry(unit, fileName, className, comment, entries, missing);
                        break;
                    }
                }
            }
            if (!missing.isEmpty()) {
                Collections.sort(missing);
                throw new GradleException("以下 Java 文件缺少 @USER: 或 @DESCRIPTION:\n"
                        + String.join("\n", missing));
            }
        }
        entries.sort(Comparator.comparing(entry -> entry.get("file")));
        return entries;
    }

    private void addEntry(CompilationUnitTree unit, String fileName, String className, String comment,
                          List<Map<String, String>> entries, List<String> missing) {
        String packagePath = unit.getPackageName() == null ? ""
                : unit.getPackageName().toString().replace('.', '/') + "/";
        String path = packagePath + fileName;
        String developer = readTag(USER_TAG, comment);
        String description = readTag(DESCRIPTION_TAG, comment).replaceAll("\\s*\\R\\s*", " ");
        if (developer.isEmpty() || description.isEmpty()) {
            missing.add(path);
            return;
        }

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("file", path);
        entry.put("jarEntry", packagePath + className + ".class");
        entry.put("type", "java");
        entry.put("desc", description);
        entry.put("developer", developer);
        entry.put("source", "javadoc");
        entries.add(entry);
    }

    private String readTag(Pattern tag, String comment) {
        if (comment == null) {
            return "";
        }
        Matcher matcher = tag.matcher(comment);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}
