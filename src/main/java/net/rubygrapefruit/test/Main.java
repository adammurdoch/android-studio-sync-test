package net.rubygrapefruit.test;

import com.android.builder.model.*;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.gradle.BasicGradleProject;
import org.gradle.tooling.model.gradle.GradleBuild;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        OptionParser parser = new OptionParser();
        ArgumentAcceptingOptionSpec<String> projectFlag = parser.accepts("project").withRequiredArg();
        ArgumentAcceptingOptionSpec<String> gradleInstallFlag = parser.accepts("gradle-install").withRequiredArg();
        OptionSpecBuilder embeddedFlag = parser.accepts("embedded");
        OptionSet options = parser.parse(args);
        File buildDir = options.hasArgument(projectFlag) ? new File(options.valueOf(projectFlag)) : new File(
                "/Users/adam/gradle/projects/uber-test-app");
        File gradleInstallDir = options.hasArgument(gradleInstallFlag) ? new File(options.valueOf(gradleInstallFlag))
                : null;
        boolean embedded = options.hasArgument(embeddedFlag);

        fetch(buildDir, gradleInstallDir, embedded);
        System.exit(0);
    }

    private static void fetch(File buildDir, File gradleInstallDir, boolean embedded) {
        System.out.println("* Fetching model for " + buildDir);

        Timer timer = new Timer();

        GradleConnector gradleConnector = GradleConnector.newConnector();
        gradleConnector.forProjectDirectory(buildDir);
        ((DefaultGradleConnector) gradleConnector).embedded(embedded);
        if (gradleInstallDir != null) {
            gradleConnector.useInstallation(gradleInstallDir);
        }

        ProjectConnection connect = gradleConnector.connect();
        try {
            System.out.println("* Running action");
            Timer actionTimer = new Timer();
            BuildActionExecuter<Map<String, AndroidProject>> modelBuilder = connect.action(new GetModel());
            modelBuilder.setStandardOutput(System.out);
            modelBuilder.setStandardError(System.err);
            modelBuilder.withArguments("-Dcom.android.build.gradle.overrideVersionCheck=true",
                    "-Pandroid.injected.build.model.only=true", "-Pandroid.injected.invoked.from.ide=true",
                    "-Pandroid.injected.build.model.only.versioned=2");
            modelBuilder.setJvmArguments("-Xmx2g");
            Map<String, AndroidProject> models = modelBuilder.run();
            actionTimer.stop();
            System.out.println("Running action took " + actionTimer.duration());
            System.out.println("Received models: " + models.size());

            new Inspector().inspectModel(models);
        } finally {
            connect.close();
        }

        timer.stop();
        System.out.println("total time: " + timer.duration());
    }

    private static class Inspector {
        Set<JavaLibrary> javaLibsByEquality = new HashSet<>();
        Map<File, JavaLibrary> javaLibsByFile = new HashMap<>();
        Map<JavaLibrary, JavaLibrary> javaLibsByIdentity = new IdentityHashMap<>();
        Map<Object, Object> javaLibsBackingByIdentity = new IdentityHashMap<>();

        Set<AndroidLibrary> libsByEquality = new HashSet<>();
        Map<File, AndroidLibrary> libsByFile = new HashMap<>();
        Map<AndroidLibrary, AndroidLibrary> libsByIdentity = new IdentityHashMap<>();
        Map<Object, Object> libsBackingByIdentity = new IdentityHashMap<>();

        void inspectModel(Map<String, AndroidProject> models) {
            System.out.println("* Inspecting");
            Timer timer = new Timer();
            for (AndroidProject androidProject : models.values()) {
                if (androidProject == null) {
                    continue;
                }
                inspect(androidProject);
            }
            timer.stop();
            System.out.println("Inspect took " + timer.duration());

            System.out.println("---");
            System.out.println("Android libs: " + libsByEquality.size());
            System.out.println("Android libs by file: " + libsByFile.size());
            System.out.println("Android libs by id: " + libsByIdentity.size());
            System.out.println("Android libs by id (backing): " + libsBackingByIdentity.size());
            System.out.println("Java libs: " + javaLibsByEquality.size());
            System.out.println("Java libs by file: " + javaLibsByFile.size());
            System.out.println("Java libs by id: " + javaLibsByIdentity.size());
            System.out.println("Java libs by id (backing): " + javaLibsBackingByIdentity.size());
            System.out.println("---");
        }

        private void inspect(AndroidProject androidProject) {
            for (Variant variant : androidProject.getVariants()) {
                inspect(variant.getMainArtifact().getDependencies());
                inspect(variant.getMainArtifact().getPackageDependencies());
                for (AndroidArtifact otherArtifact : variant.getExtraAndroidArtifacts()) {
                    inspect(otherArtifact.getDependencies());
                }
            }
        }

        private void inspect(Dependencies dependencies) {
            for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
                inspect(androidLibrary);
            }
            for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
                inspect(javaLibrary);
            }
        }

        private void inspect(AndroidLibrary androidLibrary) {
            libsByEquality.add(androidLibrary);
            libsByFile.put(androidLibrary.getJarFile(), androidLibrary);
            libsByIdentity.put(androidLibrary, androidLibrary);
            unpack(androidLibrary, libsBackingByIdentity);
            for (AndroidLibrary library : androidLibrary.getLibraryDependencies()) {
                inspect(library);
            }
            for (JavaLibrary library : androidLibrary.getJavaDependencies()) {
                inspect(library);
            }
        }

        private void inspect(JavaLibrary javaLibrary) {
            javaLibsByEquality.add(javaLibrary);
            javaLibsByFile.putIfAbsent(javaLibrary.getJarFile(), javaLibrary);
            javaLibsByIdentity.putIfAbsent(javaLibrary, javaLibrary);
            unpack(javaLibrary, javaLibsBackingByIdentity);
            for (JavaLibrary library : javaLibrary.getDependencies()) {
                inspect(library);
            }
        }

        private void unpack(Object library, Map<Object, Object> objectMap) {
            Object unpacked = new ProtocolToModelAdapter().unpack(library);
            objectMap.put(unpacked, unpacked);
        }
    }

    private static class GetModel implements BuildAction<Map<String, AndroidProject>> {
        @Override
        public Map<String, AndroidProject> execute(BuildController controller) {
            System.out.println("* Building models");
            Timer timer = new Timer();
            GradleBuild build = controller.getBuildModel();
            Map<String, AndroidProject> result = new TreeMap<>();
            for (BasicGradleProject project : build.getProjects()) {
                AndroidProject androidProject = controller.findModel(project, AndroidProject.class);
                result.put(project.getPath(), androidProject);
            }
            timer.stop();
            System.out.println("building models took " + timer.duration());

            new Inspector().inspectModel(result);
            return result;
        }
    }

    static class Timer {
        final long startTime = System.nanoTime();
        private long endTime;

        Timer stop() {
            endTime = System.nanoTime();
            return this;
        }

        String duration() {
            return BigDecimal.valueOf(endTime-startTime, 9) + "s";
        }
    }
}
