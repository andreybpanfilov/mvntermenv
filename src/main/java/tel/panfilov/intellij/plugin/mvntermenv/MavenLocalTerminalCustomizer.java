package tel.panfilov.intellij.plugin.mvntermenv;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenWslUtil;
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer;

import java.io.File;
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

    // @Override
    @NotNull
    @SuppressWarnings("unused")
    public String[] customizeCommandAndEnvironment(@NotNull Project project, @Nullable String workingDirectory, @NotNull String[] command, @NotNull Map<String, String> envs) {
        return customizeCommandAndEnvironment(project, command, envs);
    }

    // @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public String[] customizeCommandAndEnvironment(@NotNull Project project, @NotNull String[] command, @NotNull Map<String, String> envs) {
        setJavaEnvironment(project, envs);
        setMavenEnvironment(project, envs);
        return command;
    }

    protected void setJavaEnvironment(@NotNull Project project, @NotNull Map<String, String> envs) {
        Sdk sdk = project.getService(ProjectRootManager.class).getProjectSdk();
        if (sdk == null || sdk.getHomePath() == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
            return;
        }
        File sdkHome = new File(sdk.getHomePath());
        if (!sdkHome.isDirectory()) {
            return;
        }
        envs.put(JAVA_HOME, sdkHome.getAbsolutePath());
        File binPath = new File(sdkHome, "bin");
        if (!binPath.isDirectory()) {
            return;
        }
        envs.put(PATH, prepend(binPath.getAbsolutePath(), PATH, File.pathSeparator, envs));
    }

    protected void setMavenEnvironment(@NotNull Project project, @NotNull Map<String, String> envs) {
        MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
        List<MavenProject> rootProjects = projectsManager.getRootProjects();
        if (rootProjects.isEmpty()) {
            return;
        }

        setMavenHomeAndPath(envs, project, projectsManager);
        setLocalRepositoryOpts(envs, projectsManager);
        setMavenSettingsOpt(envs, projectsManager);
        setProfileOpts(projectsManager, envs);
    }

    protected void setMavenHomeAndPath(@NotNull Map<String, String> envs, @NotNull Project project, @NotNull MavenProjectsManager projectsManager) {
        MavenGeneralSettings generalSettings = projectsManager.getGeneralSettings();
        File mavenHome = MavenWslUtil.resolveMavenHome(project, generalSettings.getMavenHome());
        if (mavenHome == null || !mavenHome.isDirectory()) {
            return;
        }
        envs.put(MAVEN_HOME, mavenHome.getPath());
        File binPath = new File(mavenHome, "bin");
        if (!binPath.isDirectory() || !checkMavenExecutable(binPath)) {
            return;
        }
        envs.put(PATH, prepend(binPath.getAbsolutePath(), PATH, File.pathSeparator, envs));
    }

    protected void setMavenSettingsOpt(@NotNull Map<String, String> envs, @NotNull MavenProjectsManager projectsManager) {
        String userSettingsFile = projectsManager.getGeneralSettings().getUserSettingsFile();
        if (userSettingsFile.isBlank()) {
            return;
        }
        envs.put(MAVEN_ARGS, append("-s" + userSettingsFile, MAVEN_ARGS, " ", envs));
    }

    protected void setLocalRepositoryOpts(@NotNull Map<String, String> envs, @NotNull MavenProjectsManager projectsManager) {
        String overriddenLocalRepository = projectsManager.getGeneralSettings().getLocalRepository();
        if (overriddenLocalRepository.isBlank()) {
            return;
        }
        File localRepository = projectsManager.getLocalRepository();
        envs.put(MAVEN_OPTS, append("-Dmaven.repo.local=" + localRepository.getAbsolutePath(), MAVEN_OPTS, " ", envs));
    }

    protected void setProfileOpts(MavenProjectsManager projectsManager, @NotNull Map<String, String> envs) {
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

    protected boolean checkMavenExecutable(File binPath) {
        if (SystemInfo.isWindows) {
            File executable = new File(binPath, "mvn.cmd");
            return executable.exists();
        }
        File executable = new File(binPath, "mvn");
        try {
            if (!executable.canExecute()) {
                return executable.setExecutable(true);
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    protected String prepend(String value, String envVariable, String separator, @NotNull Map<String, String> envs) {
        String existing = envs.getOrDefault(envVariable, System.getenv(envVariable));
        if (existing == null || existing.isBlank()) {
            return value;
        }
        return value + separator + existing;
    }

    protected String append(String value, String envVariable, String separator, @NotNull Map<String, String> envs) {
        String existing = envs.getOrDefault(envVariable, System.getenv(envVariable));
        if (existing == null || existing.isBlank()) {
            return value;
        }
        return existing + separator + value;
    }

}
