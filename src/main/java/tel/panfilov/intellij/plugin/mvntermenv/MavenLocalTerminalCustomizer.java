package tel.panfilov.intellij.plugin.mvntermenv;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MavenLocalTerminalCustomizer extends LocalTerminalCustomizer {

    public static final String JAVA_HOME = "JAVA_HOME";

    public static final String PATH = "PATH";

    public static final String MAVEN_HOME = "MAVEN_HOME";

    public static final String MAVEN_OPTS = "MAVEN_OPTS";

    public static final String MAVEN_ARGS = "MAVEN_ARGS";

    @Override
    @NotNull
    public String[] customizeCommandAndEnvironment(@NotNull Project project, @Nullable String workingDirectory, @NotNull String[] command, @NotNull Map<String, String> envs) {
        setJavaEnvironment(project, envs);
        setMavenEnvironment(project, envs);
        return command;
    }

    protected void setJavaEnvironment(@NotNull Project project, @NotNull Map<String, String> envs) {
        Sdk sdk = project.getService(ProjectRootManager.class).getProjectSdk();
        if (sdk == null || sdk.getHomePath() == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
            return;
        }
        Path sdkHome = Paths.get(sdk.getHomePath());
        if (!Files.isDirectory(sdkHome)) {
            return;
        }
        envs.put(JAVA_HOME, sdkHome.toAbsolutePath().toString());
        Path binPath = sdkHome.resolve("bin");
        if (!Files.isDirectory(binPath)) {
            return;
        }
        envs.put(PATH, prepend(binPath, PATH, File.pathSeparator, envs));
    }

    protected void setMavenEnvironment(@NotNull Project project, @NotNull Map<String, String> envs) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
        List<MavenProject> rootProjects = projectsManager.getRootProjects();
        if (rootProjects.isEmpty()) {
            return;
        }

        setMavenHomeAndPath(envs, rootProjects.get(0), projectsManager);

        setLocalRepository(envs, projectsManager);
        setMavenArgs(envs, projectsManager);
        setProfileOpts(envs, projectsManager);
    }

    protected void setMavenHomeAndPath(@NotNull Map<String, String> envs, @NotNull MavenProject project, @NotNull MavenProjectsManager projectsManager) {
        MavenGeneralSettings generalSettings = projectsManager.getGeneralSettings();
        if (MavenUtil.isWrapper(generalSettings)) {
            Path binPath = Paths.get(project.getDirectory());
            if (!Files.isDirectory(binPath) || !checkMavenExecutable(binPath, "mvnw")) {
                return;
            }
            envs.put(PATH, prepend(binPath.toAbsolutePath().toString(), PATH, File.pathSeparator, envs));
            return;
        }

        MavenHomeType mavenHomeType = generalSettings.getMavenHomeType();
        if (!(mavenHomeType instanceof StaticResolvedMavenHomeType resolvedMavenHomeType)) {
            return;
        }

        Path mavenHome = MavenUtil.getMavenHomePath(resolvedMavenHomeType);
        if (mavenHome == null || !Files.isDirectory(mavenHome)) {
            return;
        }

        envs.put(MAVEN_HOME, mavenHome.toAbsolutePath().toString());
        Path binPath = mavenHome.resolve("bin");
        if (!Files.isDirectory(binPath) || !checkMavenExecutable(binPath, "mvn")) {
            return;
        }
        envs.put(PATH, prepend(binPath, PATH, File.pathSeparator, envs));
    }

    protected void setMavenArgs(@NotNull Map<String, String> envs, @NotNull MavenProjectsManager projectsManager) {
        MavenGeneralSettings settings = projectsManager.getGeneralSettings();
        String userSettingsFile = settings.getUserSettingsFile();
        if (!userSettingsFile.isBlank() && Files.isRegularFile(Paths.get(userSettingsFile))) {
            envs.put(MAVEN_ARGS, append("-s" + userSettingsFile, MAVEN_ARGS, " ", envs));
        }

        if (settings.isWorkOffline()) {
            envs.put(MAVEN_ARGS, append("-o", MAVEN_ARGS, " ", envs));
        }

        String threads = settings.getThreads();
        if (threads != null && !threads.isBlank()) {
            envs.put(MAVEN_ARGS, append("-T" + threads, MAVEN_ARGS, " ", envs));
        }

        String failureMode = settings.getFailureBehavior().getCommandLineOption();
        if (!failureMode.isBlank()) {
            envs.put(MAVEN_ARGS, append(failureMode, MAVEN_ARGS, " ", envs));
        }

        String checksumPolicy = settings.getChecksumPolicy().getCommandLineOption();
        if (!checksumPolicy.isBlank()) {
            envs.put(MAVEN_ARGS, append(checksumPolicy, MAVEN_ARGS, " ", envs));
        }

        if (settings.isAlwaysUpdateSnapshots()) {
            envs.put(MAVEN_ARGS, append("-U", MAVEN_ARGS, " ", envs));
        }

        if (settings.isPrintErrorStackTraces()) {
            envs.put(MAVEN_ARGS, append("-e", MAVEN_ARGS, " ", envs));
        }

        if (settings.isNonRecursive()) {
            envs.put(MAVEN_ARGS, append("-N", MAVEN_ARGS, " ", envs));
        }
    }

    protected void setLocalRepository(@NotNull Map<String, String> envs, @NotNull MavenProjectsManager projectsManager) {
        Path localRepository = projectsManager.getRepositoryPath().toAbsolutePath();
        envs.put(MAVEN_OPTS, append("-Dmaven.repo.local=" + localRepository, MAVEN_OPTS, " ", envs));
    }

    protected void setProfileOpts(@NotNull Map<String, String> envs, MavenProjectsManager projectsManager) {
        MavenExplicitProfiles explicitProfiles = projectsManager.getExplicitProfiles();
        String profiles = Stream.concat(
                explicitProfiles.getEnabledProfiles().stream(),
                explicitProfiles.getDisabledProfiles().stream()
                        .map(s -> "!" + s)
        ).collect(Collectors.joining(","));

        if (profiles.isBlank()) {
            return;
        }
        envs.put(MAVEN_ARGS, append("-P" + profiles, MAVEN_ARGS, " ", envs));
    }

    protected boolean checkMavenExecutable(Path binPath, String binaryName) {
        if (SystemInfo.isWindows) {
            Path executable = binPath.resolve(binaryName + ".cmd");
            return Files.exists(executable);
        }
        Path executable = binPath.resolve(binaryName);
        try {
            if (!Files.isExecutable(executable)) {
                return executable.toFile().setExecutable(true);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    protected String prepend(Path path, String envVariable, String separator, @NotNull Map<String, String> envs) {
        return prepend(path.toAbsolutePath().toString(), envVariable, separator, envs);
    }

    protected String prepend(String value, String envVariable, String separator, @NotNull Map<String, String> envs) {
        String existing = envs.getOrDefault(envVariable, System.getenv(envVariable));
        if (existing == null || existing.isBlank()) {
            return value;
        }
        return value + separator + existing;
    }

    protected String append(Path path, String envVariable, String separator, @NotNull Map<String, String> envs) {
        return append(path.toAbsolutePath().toString(), envVariable, separator, envs);
    }

    protected String append(String value, String envVariable, String separator, @NotNull Map<String, String> envs) {
        String existing = envs.getOrDefault(envVariable, System.getenv(envVariable));
        if (existing == null || existing.isBlank()) {
            return value;
        }
        return existing + separator + value;
    }

}
