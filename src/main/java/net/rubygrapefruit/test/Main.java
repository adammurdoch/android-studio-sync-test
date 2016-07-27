package net.rubygrapefruit.test;

import com.android.builder.model.*;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        fetch();
        System.exit(0);
    }

    private static void fetch() {
        System.out.println();
        long start = System.currentTimeMillis();
        GradleConnector gradleConnector = GradleConnector.newConnector();
        gradleConnector.forProjectDirectory(new File("/Users/adam/gradle/projects/uber-test-app"));
        ((DefaultGradleConnector) gradleConnector).embedded(false);
        gradleConnector.useInstallation(new File("/Users/adam/gradle/current"));
        ProjectConnection connect = gradleConnector.connect();
        try {
            System.out.println("starting action:");
            BuildActionExecuter<Map<String, AndroidProject>> modelBuilder = connect.action(new GetModel());
            modelBuilder.setStandardOutput(System.out);
            modelBuilder.setStandardError(System.err);
            modelBuilder.withArguments("-Dcom.android.build.gradle.overrideVersionCheck=true");
            modelBuilder.setJvmArguments("-Xmx2g");
            Map<String, AndroidProject> models = modelBuilder.run();

            System.out.println("Received models: " + models.size());

            inspectModel(models);
        } finally {
            connect.close();
        }
        long end = System.currentTimeMillis();
        System.out.println("time: " + (end - start));
        System.out.println();
    }

    private static void inspectModel(Map<String, AndroidProject> models) {
        Set<JavaLibrary> javaLibsByEquality = new HashSet<>();
        Map<File, JavaLibrary> javaLibsByFile = new HashMap<>();
        Map<JavaLibrary, JavaLibrary> javaLibsByIdentity = new IdentityHashMap<>();

        Set<AndroidLibrary> libsByEquality = new HashSet<>();
        Map<File, AndroidLibrary> libsByFile = new HashMap<>();
        Map<AndroidLibrary, AndroidLibrary> libsByIdentity = new IdentityHashMap<>();

        for (AndroidProject androidProject : models.values()) {
            if (androidProject == null) {
                continue;
            }
            for (Variant variant : androidProject.getVariants()) {
                System.out.println(androidProject.getName() + " " + variant.getName());
                Dependencies dependencies = variant.getMainArtifact().getDependencies();
                System.out.println("android libs: " + dependencies.getLibraries().size());
                for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
                    libsByEquality.add(androidLibrary);
                    libsByFile.put(androidLibrary.getJarFile(), androidLibrary);
                    libsByIdentity.put(androidLibrary, androidLibrary);
                }
                System.out.println("java libs: " + dependencies.getJavaLibraries().size());
                for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
                    javaLibsByEquality.add(javaLibrary);
                    javaLibsByFile.putIfAbsent(javaLibrary.getJarFile(), javaLibrary);
                    javaLibsByIdentity.putIfAbsent(javaLibrary, javaLibrary);
                }
            }
        }
        System.out.println("---");
        System.out.println("Android libs: " + libsByEquality.size());
        System.out.println("Android libs by file: " + libsByFile.size());
        System.out.println("Android libs by id: " + libsByIdentity.size());
        System.out.println("Java libs: " + javaLibsByEquality.size());
        System.out.println("Java libs by file: " + javaLibsByFile.size());
        System.out.println("Java libs by id: " + javaLibsByIdentity.size());
        System.out.println("---");
    }

    private static class GetModel implements BuildAction<Map<String, AndroidProject>> {
        @Override
        public Map<String, AndroidProject> execute(BuildController controller) {
            System.out.println("fetching");
            GradleBuild build = controller.getBuildModel();
            Map<String, AndroidProject> result = new TreeMap<>();
            for (BasicGradleProject project : build.getProjects()) {
                AndroidProject androidProject = controller.findModel(project, AndroidProject.class);
                result.put(project.getPath(), androidProject);
            }
            inspectModel(result);
            return result;
        }
    }
}
