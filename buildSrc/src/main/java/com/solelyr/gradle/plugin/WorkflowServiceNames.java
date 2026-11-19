package com.solelyr.gradle.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @USER: solelyr
 * @DATE: 2026/11/15 17:34
 * @DESCRIPTION: 校验部署包中工作流处理器的 Spring Service 名称是否冲突
 */
public final class WorkflowServiceNames {
    private static final String HANDLER =
            "com/weaver/seconddev/solelyr/workflow/action/WorkflowEsbActionHandler";
    private static final String SERVICE = "Lorg/springframework/stereotype/Service;";

    private WorkflowServiceNames() {
    }

    public static void validate(Project project, FileCollection classes, FileCollection jars) {
        List<ServiceClass> classFiles = new ArrayList<>();
        classes.forEach(directory -> project.fileTree(directory).matching(pattern -> pattern.include("**/*.class"))
                .forEach(file -> classFiles.add(readClass(file, project.relativePath(directory)))));
        jars.forEach(jar -> project.zipTree(jar).matching(pattern -> {
            pattern.include("**/*.class");
            pattern.exclude("META-INF/**", "module-info.class");
        }).forEach(file -> classFiles.add(readClass(file, jar.getName()))));

        Map<String, ServiceClass> hierarchy = new TreeMap<>();
        Map<String, List<ServiceClass>> services = new TreeMap<>();
        for (ServiceClass type : classFiles) {
            hierarchy.putIfAbsent(type.name, type);
            if (type.serviceName != null && (type.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0) {
                services.computeIfAbsent(type.serviceName, name -> new ArrayList<>()).add(type);
            }
        }

        List<String> conflicts = new ArrayList<>();
        services.forEach((name, candidates) -> {
            if (candidates.size() > 1 && candidates.stream()
                    .anyMatch(type -> isHandler(type.name, hierarchy, new HashSet<>()))) {
                conflicts.add(name + "\n  " + String.join("\n  ", candidates.stream()
                        .map(type -> type.name.replace('/', '.') + " [" + type.origin + "]")
                        .sorted().toArray(String[]::new)));
            }
        });

        if (!conflicts.isEmpty()) {
            throw new GradleException("工作流处理器的 Spring Service 名称重复：\n" + String.join("\n", conflicts));
        }
    }

    private static boolean isHandler(String name, Map<String, ServiceClass> hierarchy, Set<String> visited) {
        if (HANDLER.equals(name)) {
            return true;
        }
        ServiceClass type = hierarchy.get(name);
        return type != null && visited.add(name)
                && type.parents.stream().anyMatch(parent -> isHandler(parent, hierarchy, visited));
    }

    private static ServiceClass readClass(File file, String origin) {
        ServiceClass type = new ServiceClass(origin);
        try {
            ClassReader reader = new ClassReader(Files.readAllBytes(file.toPath()));
            reader.accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type;
        } catch (IOException | IllegalArgumentException e) {
            throw new GradleException("无法读取合包类文件 " + file.getName() + " [" + origin + "]", e);
        }
    }

    /**
     * @USER: solelyr
     * @DATE: 2026/11/15 17:34
     * @DESCRIPTION: 从类字节码提取继承关系和 Spring Service 名称
     */
    private static final class ServiceClass extends ClassVisitor {
        private final String origin;
        private final List<String> parents = new ArrayList<>();
        private String name;
        private String serviceName;
        private int access;

        private ServiceClass(String origin) {
            super(Opcodes.ASM9);
            this.origin = origin;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            this.name = name;
            this.access = access;
            if (superName != null) {
                parents.add(superName);
            }
            parents.addAll(Arrays.asList(interfaces));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!visible || !SERVICE.equals(descriptor)) {
                return null;
            }
            serviceName = Introspector.decapitalize(name.substring(name.lastIndexOf('/') + 1).replace('$', '.'));
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String attribute, Object value) {
                    if ("value".equals(attribute) && value instanceof String && !((String) value).trim().isEmpty()) {
                        serviceName = (String) value;
                    }
                }
            };
        }
    }
}
