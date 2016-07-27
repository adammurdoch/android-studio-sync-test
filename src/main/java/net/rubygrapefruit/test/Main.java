package net.rubygrapefruit.test;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.connection.DefaultGradleConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        fetch();
    }

    private static void fetch() {
        System.out.println();
        long start = System.currentTimeMillis();
        GradleConnector gradleConnector = GradleConnector.newConnector();
        gradleConnector.forProjectDirectory(new File("/Users/adam/gradle/projects/uber-test-app"));
        ((DefaultGradleConnector) gradleConnector).embedded(true);
        ProjectConnection connect = gradleConnector.connect();
        try {
            System.out.println("starting action:");
            BuildActionExecuter<Map<String, AndroidProject>> modelBuilder = connect.action(new GetModel());
            modelBuilder.setStandardOutput(System.out);
            modelBuilder.setStandardError(System.err);
            Map<String, AndroidProject> models = modelBuilder.run();

            System.out.println("models: " + models.size());

            dump(models);
        } finally {
            connect.close();
        }
        long end = System.currentTimeMillis();
        System.out.println("time: " + (end - start));
        System.out.println();
    }

    private static void dump(Map<String, AndroidProject> models) {
        Set<JavaLibrary> libsByEquality = new HashSet<>();
        Map<File, JavaLibrary> libsByFile = new HashMap<>();
        Map<JavaLibrary, JavaLibrary> libsByIdentity = new IdentityHashMap<>();
        for (AndroidProject androidProject : models.values()) {
            if (androidProject == null) {
                continue;
            }
            System.out.println(androidProject.getName());
            for (Variant variant : androidProject.getVariants()) {
                if (variant.getName().equals("debug")) {
                    Dependencies dependencies = variant.getMainArtifact().getDependencies();
                    System.out.println("android libs: " + dependencies.getLibraries().size());
                    System.out.println("java libs: " + dependencies.getJavaLibraries().size());
                    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
                        libsByEquality.add(javaLibrary);
                        libsByFile.putIfAbsent(javaLibrary.getJarFile(), javaLibrary);
                        libsByIdentity.putIfAbsent(javaLibrary, javaLibrary);
                    }
                }
            }
        }
        System.out.println("---");
        System.out.println("Libs: " + libsByFile.size());
        System.out.println("Libs by file: " + libsByFile.size());
        System.out.println("Libs by id: " + libsByIdentity.size());
        System.out.println("---");
    }

    static class GetModel implements BuildAction<Map<String, AndroidProject>> {
        @Override
        public Map<String, AndroidProject> execute(BuildController controller) {
            System.out.println("fetching");
            GradleBuild build = controller.getBuildModel();
            System.out.println("projects: " + build.getProjects().size());
            Map<String, AndroidProject> result = new TreeMap<>();
            for (BasicGradleProject project : build.getProjects()) {
                AndroidProject androidProject = controller.findModel(project, AndroidProject.class);
                result.put(project.getPath(), androidProject);
            }
            dump(result);
            return result;
        }
    }
}
