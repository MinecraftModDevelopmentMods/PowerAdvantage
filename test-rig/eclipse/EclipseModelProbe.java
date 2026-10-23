import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.EclipseSourceDirectory;

/** Java 17 source-file tool: verify the Eclipse model Buildship actually imports. */
public final class EclipseModelProbe {
    public static void main(String[] args) {
        if (args.length < 3) throw new IllegalArgumentException("Gradle home, Gradle user home, then project directories required");
        for (int i = 2; i < args.length; i++) {
            try (ProjectConnection connection = GradleConnector.newConnector()
                    .useInstallation(new File(args[0])).useGradleUserHomeDir(new File(args[1]))
                    .forProjectDirectory(new File(args[i])).connect()) {
                EclipseProject project = connection.model(EclipseProject.class).withArguments("--offline")
                        .setJavaHome(new File(System.getProperty("java.home"))).get();
                int processed = 0;
                List<String> directories = new ArrayList<>();
                for (EclipseSourceDirectory source : project.getSourceDirectories()) {
                    String path = source.getPath().replace('\\', '/');
                    directories.add(path + " -> " + source.getOutput());
                    if ("src/main/resources".equals(path) || "src/generated/resources".equals(path))
                        throw new AssertionError("Buildship would copy raw templates: " + args[i] + " " + path);
                    if ("build/resources/main".equals(path)) {
                        if (!"bin/main".equals(source.getOutput())) throw new AssertionError("Wrong resource output");
                        processed++;
                    }
                }
                if (processed != 1) throw new AssertionError("Expected one processed resource root: " + directories);
                System.out.println("PASS Eclipse model " + args[i] + ": " + directories);
            }
        }
    }
}
